package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.R
import com.example.timetable.data.EntryConstants
import com.example.timetable.data.EntryValidationError
import com.example.timetable.data.EntryValidator
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.MAX_EXPANDED_OCCURRENCES
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableGroup
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.WeekRule
import com.example.timetable.data.countConflictPairs
import com.example.timetable.data.countConflictPairsBetween
import com.example.timetable.data.findConflictForEntry
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.normalizeWeekListText
import com.example.timetable.data.occursOnDate
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.parseWeekList
import com.example.timetable.data.resolveRecurrenceType
import com.example.timetable.data.resolveWeekRule
import com.example.timetable.data.suggestAdjustedEntryAfterConflicts
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.notify.ReminderFallbackWorker
import com.example.timetable.widget.TimetableWidgetUpdater
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_ICS_IMPORT_BYTES = 1024 * 1024
private const val ENTRY_SIDE_EFFECT_DEBOUNCE_MS = 300L

/**
 * 课程表视图模型。
 *
 * 管理课程表数据的核心视图模型，负责处理课程的添加、编辑、删除、导入/导出等操作。
 *
 * @param application 应用实例
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _activeGroupId = MutableStateFlow(TimetableRepository.getActiveGroupId(application))
    val activeGroupId: StateFlow<String> = _activeGroupId

    val timetableGroups: StateFlow<List<TimetableGroup>> = TimetableRepository.getGroupsStream(application)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(TimetableGroup.default()),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<TimetableEntry>> = _activeGroupId
        .flatMapLatest { groupId -> TimetableRepository.getEntriesStream(application, groupId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val reminderSyncMutex = Mutex()
    private var reminderSyncGeneration = 0L
    private var reminderSyncJob: Job? = null
    private var lastReminderSyncToken: String? = null
    private val widgetRefreshMutex = Mutex()
    private var widgetRefreshGeneration = 0L
    private var widgetRefreshJob: Job? = null
    private var lastWidgetRefreshToken: String? = null

    init {
        ReminderFallbackWorker.ensureScheduled(application)
        viewModelScope.launch {
            TimetableRepository.ensureMigrated(getApplication())
            _activeGroupId.value = TimetableRepository.resolveActiveGroupId(getApplication())
            entries.debouncedEntrySideEffects().collect { currentEntries ->
                syncReminders(currentEntries)
                refreshWidgets(currentEntries)
            }
        }
    }

    fun selectTimetableGroup(groupId: String) {
        val normalizedGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        viewModelScope.launch {
            TimetableRepository.setActiveGroupId(getApplication(), normalizedGroupId)
            _activeGroupId.value = normalizedGroupId
            val group = timetableGroups.value.firstOrNull { it.id == normalizedGroupId }
            postMessage(
                getApplication<Application>().getString(
                    R.string.vm_timetable_group_selected,
                    group?.name ?: TimetableGroup.DEFAULT_NAME,
                ),
            )
        }
    }

    fun createTimetableGroup(name: String) {
        viewModelScope.launch {
            val group = TimetableRepository.createGroup(getApplication(), name)
            TimetableRepository.setActiveGroupId(getApplication(), group.id)
            _activeGroupId.value = group.id
            postMessage(getApplication<Application>().getString(R.string.vm_timetable_group_created, group.name))
        }
    }

    /**
     * 预览课程冲突。
     *
     * 检查给定课程条目是否与现有课程冲突。
     *
     * @param entry 课程条目
     * @return 冲突的课程条目，或 null 如果没有冲突
     */
    suspend fun previewConflict(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        val entriesSnapshot = entries.value
        return runConflictCalculation {
            findConflictForEntry(normalized, entriesSnapshot)
        }
    }

    /**
     * 建议解决冲突的课程条目。
     *
     * 为给定的课程条目生成一个调整后的版本，以解决与现有课程的冲突。
     *
     * @param entry 课程条目
     * @return 调整后的课程条目，或 null 如果无法解决冲突
     */
    suspend fun suggestResolvedEntry(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        val entriesSnapshot = entries.value
        return runConflictCalculation {
            suggestAdjustedEntryAfterConflicts(normalized, entriesSnapshot)
        }
    }

    /**
     * 添加或更新课程条目。
     *
     * 验证并保存课程条目，处理冲突检测。
     *
     * @param entry 课程条目
     * @param allowConflict 是否允许冲突
     */
    fun upsertEntry(entry: TimetableEntry, allowConflict: Boolean = false) {
        val normalized = normalizeEntry(entry)
        validateEntry(normalized)?.let {
            postMessage(getApplication<Application>().getString(R.string.vm_save_failed, it))
            return
        }
        val entriesSnapshot = entries.value

        viewModelScope.launch {
            val conflict = runConflictCalculation {
                findConflictForEntry(normalized, entriesSnapshot)
            }
            if (conflict != null && !allowConflict) {
                postMessage(
                    getApplication<Application>().getString(
                        R.string.vm_conflict_detected,
                        conflict.title,
                        formatMinutes(conflict.startMinutes),
                        formatMinutes(conflict.endMinutes),
                    ),
                )
                return@launch
            }

            TimetableRepository.upsertEntry(getApplication(), normalized)

            if (conflict == null) {
                postMessage(getApplication<Application>().getString(R.string.vm_entry_saved))
            } else {
                postMessage(
                    getApplication<Application>().getString(
                        R.string.vm_entry_saved_with_conflict,
                        conflict.title,
                        formatMinutes(conflict.startMinutes),
                        formatMinutes(conflict.endMinutes),
                    ),
                )
            }
        }
    }

    /**
     * 删除课程条目。
     *
     * 根据 ID 删除课程条目。
     *
     * @param entryId 课程条目 ID
     */
    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            TimetableRepository.deleteEntry(getApplication(), entryId)
            postMessage(getApplication<Application>().getString(R.string.vm_entry_deleted))
        }
    }

    /**
     * 导出课程表为 ICS 格式。
     *
     * 将所有课程条目导出为 ICS 日历格式字符串。
     *
     * @return ICS 格式的日历字符串
     */
    suspend fun exportIcs(): String = withContext(Dispatchers.Default) {
        IcsCalendar.write(entries.value, getApplication<Application>().getString(R.string.default_calendar_name))
    }

    private val _importPreview = MutableSharedFlow<ImportPreview>(extraBufferCapacity = 1)
    val importPreview = _importPreview.asSharedFlow()

    fun importFromIcs(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = readText(contentResolver, uri) ?: return@launch
            if (text.isBlank()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_empty))
                return@launch
            }

            val imported = try {
                withContext(Dispatchers.Default) {
                    IcsCalendar.parse(text)
                }
            } catch (error: Exception) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_parse_failed, error.message ?: getApplication<Application>().getString(R.string.vm_calendar_format_error)))
                emptyList()
            }
            if (imported.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_valid))
                return@launch
            }

            val preview = buildImportPreview(
                imported = imported,
                existingEntries = entries.value,
                sourceName = getApplication<Application>().getString(R.string.default_calendar_name),
            )
            if (preview.validEntries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_effective))
                return@launch
            }

            if (preview.truncated) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_truncated, preview.totalParsed))
            }

            _importPreview.tryEmit(preview)
        }
    }

    fun importAcademicEntries(sourceName: String, entries: List<TimetableEntry>) {
        viewModelScope.launch {
            if (entries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_academic_import_empty, sourceName))
                return@launch
            }

            val preview = buildImportPreview(
                imported = entries,
                existingEntries = this@ScheduleViewModel.entries.value,
                sourceName = sourceName,
            )
            if (preview.validEntries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_effective))
                return@launch
            }

            if (preview.truncated) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_truncated, preview.totalParsed))
            }

            _importPreview.tryEmit(preview)
        }
    }

    fun confirmImport(preview: ImportPreview, target: ImportTarget) {
        viewModelScope.launch {
            commitImport(preview, target)
        }
    }

    fun cancelImport() {
        // no-op; UI just dismisses the dialog
    }

    private suspend fun commitImport(preview: ImportPreview, target: ImportTarget) {
        val app = getApplication<Application>()
        when (target) {
            ImportTarget.OverwriteCurrent -> {
                val groupId = _activeGroupId.value
                TimetableRepository.replaceEntriesInGroup(app, groupId, preview.validEntries)
                val groupName = timetableGroups.value.firstOrNull { it.id == groupId }?.name ?: TimetableGroup.DEFAULT_NAME
                postImportMessage(preview, app.getString(R.string.vm_import_target_overwrite, groupName))
            }
            is ImportTarget.CreateGroup -> {
                val group = TimetableRepository.createGroupWithEntries(app, target.name, preview.validEntries)
                _activeGroupId.value = group.id
                postImportMessage(preview, app.getString(R.string.vm_import_target_new_group, group.name))
            }
        }
    }

    private fun postImportMessage(preview: ImportPreview, targetLabel: String) {
        val app = getApplication<Application>()
        if (preview.invalidCount == 0 && preview.conflictCount == 0) {
            postMessage(app.getString(R.string.vm_import_success_to_target, preview.validEntries.size, targetLabel))
        } else {
            postMessage(
                app.getString(
                    R.string.vm_import_success_partial_to_target,
                    preview.validEntries.size,
                    preview.invalidCount,
                    preview.conflictCount,
                    targetLabel,
                ),
            )
        }
    }

    private fun buildImportPreview(
        imported: List<TimetableEntry>,
        existingEntries: List<TimetableEntry>,
        sourceName: String,
    ): ImportPreview {
        val validEntries = mutableListOf<TimetableEntry>()
        var invalidCount = 0

        imported.map(::normalizeEntry)
            .forEach { entry ->
                if (validateEntry(entry) != null) {
                    invalidCount++
                    return@forEach
                }
                validEntries += entry
            }

        val internalConflictCount = countConflictPairs(validEntries)
        val existingConflictCount = countConflictPairsBetween(validEntries, existingEntries)
        val conflictCount = internalConflictCount + existingConflictCount
        val truncated = imported.size >= MAX_EXPANDED_OCCURRENCES
        return ImportPreview(
            validEntries = validEntries,
            invalidCount = invalidCount,
            conflictCount = conflictCount,
            totalParsed = imported.size,
            truncated = truncated,
            sourceName = sourceName,
            suggestedGroupName = suggestedImportGroupName(sourceName),
            internalConflictCount = internalConflictCount,
            existingConflictCount = existingConflictCount,
        )
    }

    fun updateReminderMinutes(minutes: Iterable<Int>) {
        val normalizedMinutes = CourseReminderScheduler.normalizeReminderMinutes(minutes)
        if (normalizedMinutes.isEmpty()) return
        val currentMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication())
        if (normalizedMinutes == currentMinutes) return
        CourseReminderScheduler.setReminderMinutes(getApplication(), normalizedMinutes)
        syncReminders(entries.value)
        postMessage(getApplication<Application>().getString(R.string.vm_reminder_updated, CourseReminderScheduler.formatReminderSelection(normalizedMinutes)))
    }

    fun resyncReminderSchedule() {
        syncReminders(entries.value, force = true)
    }

    private suspend fun readText(contentResolver: ContentResolver, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val declaredSize = queryImportSize(contentResolver, uri)
                if (declaredSize != null && declaredSize > MAX_ICS_IMPORT_BYTES) {
                    throw IOException(importSizeLimitMessage())
                }
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                inputStream.use { stream ->
                    readLimitedUtf8Text(stream, MAX_ICS_IMPORT_BYTES)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                postImportReadFailure(error)
                null
            }
        }
    }

    private fun postImportReadFailure(error: Throwable) {
        val app = getApplication<Application>()
        val message = when (val messageResId = importReadFailureMessageResId(error)) {
            R.string.vm_read_file_too_large -> app.getString(messageResId, formatImportSize(MAX_ICS_IMPORT_BYTES))
            R.string.vm_read_file_failed -> app.getString(
                messageResId,
                error.message ?: app.getString(R.string.msg_unknown_error),
            )
            else -> app.getString(messageResId)
        }
        postMessage(message)
    }

    private fun queryImportSize(contentResolver: ContentResolver, uri: Uri): Long? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun normalizeEntry(entry: TimetableEntry): TimetableEntry {
        val normalizedCustomWeekList = normalizeWeekListText(entry.customWeekList)
        val normalizedSkipWeekList = normalizeWeekListText(entry.skipWeekList)
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        val weekRule = resolveWeekRule(entry.weekRule) ?: WeekRule.ALL
        return entry.copy(
            title = entry.title.trim(),
            groupId = entry.groupId.ifBlank { _activeGroupId.value },
            location = entry.location.trim(),
            note = entry.note.trim(),
            recurrenceType = recurrence.name,
            semesterStartDate = entry.semesterStartDate.trim(),
            weekRule = weekRule.name,
            customWeekList = normalizedCustomWeekList,
            skipWeekList = normalizedSkipWeekList,
        )
    }

    private fun validateEntry(entry: TimetableEntry): String? {
        val app = getApplication<Application>()
        val error = EntryValidator.validate(entry) ?: return null
        return app.getString(error.messageResId)
    }


    private fun syncReminders(entriesList: List<TimetableEntry>, force: Boolean = false) {
        val generation = ++reminderSyncGeneration
        reminderSyncJob?.cancel()
        reminderSyncJob = viewModelScope.launch(Dispatchers.IO) {
            reminderSyncMutex.withLock {
                if (generation != reminderSyncGeneration) return@launch
                lastReminderSyncToken = runReminderSyncIfNeeded(
                    entries = entriesList,
                    reminderMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication()),
                    lastSyncToken = lastReminderSyncToken,
                    force = force,
                ) { entries, forceReschedule ->
                    CourseReminderScheduler.sync(
                        context = getApplication(),
                        entries = entries,
                        forceReschedule = forceReschedule,
                    )
                }
            }
        }
    }

    private fun refreshWidgets(entriesList: List<TimetableEntry>) {
        val generation = ++widgetRefreshGeneration
        widgetRefreshJob?.cancel()
        widgetRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            widgetRefreshMutex.withLock {
                if (generation != widgetRefreshGeneration) return@launch
                val refreshToken = widgetRefreshToken(entriesList)
                if (refreshToken == lastWidgetRefreshToken) return@launch
                TimetableWidgetUpdater.refreshAll(getApplication(), entriesList)
                lastWidgetRefreshToken = refreshToken
            }
        }
    }

    private fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }
}

internal fun countImportConflicts(
    validEntries: List<TimetableEntry>,
    existingEntries: List<TimetableEntry>,
): Int {
    if (validEntries.isEmpty()) return 0

    val internalConflicts = countConflictPairs(validEntries)
    val existingConflicts = countConflictPairsBetween(validEntries, existingEntries)
    return internalConflicts + existingConflicts
}

internal suspend fun <T> runConflictCalculation(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: () -> T,
): T {
    return withContext(dispatcher) {
        block()
    }
}

@OptIn(FlowPreview::class)
internal fun Flow<List<TimetableEntry>>.debouncedEntrySideEffects(
    debounceMillis: Long = ENTRY_SIDE_EFFECT_DEBOUNCE_MS,
): Flow<List<TimetableEntry>> {
    return debounce(debounceMillis)
}

internal fun readLimitedUtf8Text(inputStream: InputStream, maxBytes: Int): String {
    require(maxBytes > 0)

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    var totalRead = 0

    while (true) {
        val read = inputStream.read(buffer)
        if (read < 0) break
        if (read == 0) continue

        totalRead += read
        if (totalRead > maxBytes) {
            throw IOException(importSizeLimitMessage(maxBytes))
        }
        output.write(buffer, 0, read)
    }

    return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

internal fun importSizeLimitMessage(maxBytes: Int = MAX_ICS_IMPORT_BYTES): String {
    return "ICS file exceeds the import limit of ${formatImportSize(maxBytes)}."
}

internal fun formatImportSize(maxBytes: Int): String {
    val kibibyte = 1024
    val mebibyte = kibibyte * kibibyte
    return when {
        maxBytes >= mebibyte && maxBytes % mebibyte == 0 -> "${maxBytes / mebibyte} MB"
        maxBytes >= kibibyte && maxBytes % kibibyte == 0 -> "${maxBytes / kibibyte} KB"
        else -> "$maxBytes bytes"
    }
}

@StringRes
internal fun importReadFailureMessageResId(error: Throwable): Int {
    return when (error) {
        is FileNotFoundException,
        is SecurityException -> R.string.vm_read_file_access_denied
        is IOException -> {
            if (isImportSizeLimitError(error)) R.string.vm_read_file_too_large else R.string.vm_read_file_failed
        }
        else -> R.string.vm_read_file_failed
    }
}

private fun isImportSizeLimitError(error: IOException): Boolean {
    return error.message?.startsWith("ICS file exceeds the import limit of ") == true
}

internal fun reminderSyncToken(
    entries: List<TimetableEntry>,
    reminderMinutes: List<Int>,
): String {
    val normalizedReminderMinutes = CourseReminderScheduler.normalizeReminderMinutes(reminderMinutes)
    return JSONObject()
        .put("reminderMinutes", JSONArray(normalizedReminderMinutes))
        .put("entries", JSONArray(entries.map(::entryTokenJson)))
        .toString()
}

internal suspend fun runReminderSyncIfNeeded(
    entries: List<TimetableEntry>,
    reminderMinutes: List<Int>,
    lastSyncToken: String?,
    force: Boolean,
    syncAction: suspend (List<TimetableEntry>, Boolean) -> Unit,
): String? {
    val syncToken = reminderSyncToken(entries, reminderMinutes)
    if (!force && syncToken == lastSyncToken) return lastSyncToken
    syncAction(entries, force)
    return syncToken
}

internal fun widgetRefreshToken(entries: List<TimetableEntry>): String {
    return JSONArray(entries.map(::entryTokenJson)).toString()
}

private fun entryTokenJson(entry: TimetableEntry): JSONObject {
    return JSONObject()
        .put("id", entry.id)
        .put("groupId", entry.groupId)
        .put("title", entry.title)
        .put("location", entry.location)
        .put("date", entry.date)
        .put("dayOfWeek", entry.dayOfWeek)
        .put("startMinutes", entry.startMinutes)
        .put("endMinutes", entry.endMinutes)
        .put("recurrenceType", entry.recurrenceType)
        .put("semesterStartDate", entry.semesterStartDate)
        .put("weekRule", entry.weekRule)
        .put("customWeekList", entry.customWeekList)
        .put("skipWeekList", entry.skipWeekList)
}

/**
 * Import preview: parsed import results that have not yet been written to the database.
 *
 * When conflicts are detected, the ViewModel sends this to the UI via [ScheduleViewModel.importPreview],
 * and writes to the database only after the user confirms via [ScheduleViewModel.confirmImport].
 */
data class ImportPreview(
    val validEntries: List<TimetableEntry>,
    val invalidCount: Int,
    val conflictCount: Int,
    val totalParsed: Int,
    val truncated: Boolean = false,
    val sourceName: String = "",
    val suggestedGroupName: String = "",
    val internalConflictCount: Int = conflictCount,
    val existingConflictCount: Int = 0,
)

sealed interface ImportTarget {
    data object OverwriteCurrent : ImportTarget
    data class CreateGroup(val name: String) : ImportTarget
}

internal fun suggestedImportGroupName(sourceName: String): String {
    val base = sourceName.trim().ifBlank { "导入课表" }
    val today = java.time.LocalDate.now().toString()
    return "$base $today".take(40)
}
