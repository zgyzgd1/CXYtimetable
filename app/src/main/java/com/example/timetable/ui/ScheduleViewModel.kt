package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.R
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.MAX_EXPANDED_OCCURRENCES
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.WeekRule
import com.example.timetable.data.countConflictPairs
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
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_ICS_IMPORT_BYTES = 1024 * 1024

/**
 * 课程表视图模型。
 *
 * 管理课程表数据的核心视图模型，负责处理课程的添加、编辑、删除、导入/导出等操作。
 *
 * @param application 应用实例
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    val entries: StateFlow<List<TimetableEntry>> = TimetableRepository.getEntriesStream(application)
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
            TimetableRepository.getEntriesStream(getApplication()).collect { currentEntries ->
                syncReminders(currentEntries)
                refreshWidgets(currentEntries)
            }
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
    fun previewConflict(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        return findConflictForEntry(normalized, entries.value)
    }

    /**
     * 建议解决冲突的课程条目。
     *
     * 为给定的课程条目生成一个调整后的版本，以解决与现有课程的冲突。
     *
     * @param entry 课程条目
     * @return 调整后的课程条目，或 null 如果无法解决冲突
     */
    fun suggestResolvedEntry(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        return suggestAdjustedEntryAfterConflicts(normalized, entries.value)
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
        val conflict = findConflictForEntry(normalized, entries.value)
        if (conflict != null && !allowConflict) {
            postMessage(
                getApplication<Application>().getString(
                    R.string.vm_conflict_detected,
                    conflict.title,
                    formatMinutes(conflict.startMinutes),
                    formatMinutes(conflict.endMinutes),
                ),
            )
            return
        }

        viewModelScope.launch {
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
            val text = readText(contentResolver, uri)
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

            val preview = buildImportPreview(imported)
            if (preview.validEntries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_effective))
                return@launch
            }

            if (preview.truncated) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_truncated, preview.totalParsed))
            }

            if (preview.conflictCount == 0) {
                // No conflicts — apply directly
                commitImport(preview)
            } else {
                // Has conflicts — let the UI present confirmation
                _importPreview.tryEmit(preview)
            }
        }
    }

    fun confirmImport(preview: ImportPreview) {
        viewModelScope.launch {
            commitImport(preview)
        }
    }

    fun cancelImport() {
        // no-op; UI just dismisses the dialog
    }

    private suspend fun commitImport(preview: ImportPreview) {
        TimetableRepository.mergeEntries(getApplication(), preview.validEntries)
        if (preview.invalidCount == 0 && preview.conflictCount == 0) {
            postMessage(getApplication<Application>().getString(R.string.vm_import_success, preview.validEntries.size))
        } else {
            postMessage(
                getApplication<Application>().getString(
                    R.string.vm_import_success_partial,
                    preview.validEntries.size,
                    preview.invalidCount,
                    preview.conflictCount,
                ),
            )
        }
    }

    private fun buildImportPreview(imported: List<TimetableEntry>): ImportPreview {
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

        val conflictCount = if (validEntries.isNotEmpty()) countConflictPairs(validEntries) else 0
        val truncated = imported.size >= MAX_EXPANDED_OCCURRENCES
        return ImportPreview(
            validEntries = validEntries,
            invalidCount = invalidCount,
            conflictCount = conflictCount,
            totalParsed = imported.size,
            truncated = truncated,
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

    private suspend fun readText(contentResolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                val declaredSize = queryImportSize(contentResolver, uri)
                if (declaredSize != null && declaredSize > MAX_ICS_IMPORT_BYTES) {
                    throw IOException(importSizeLimitMessage())
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    readLimitedUtf8Text(stream, MAX_ICS_IMPORT_BYTES)
                }.orEmpty()
            }.onFailure {
                postMessage(getApplication<Application>().getString(R.string.vm_read_file_failed, it.message ?: getApplication<Application>().getString(R.string.msg_unknown_error)))
            }.getOrDefault("")
        }
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
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: return app.getString(R.string.val_invalid_recurrence)
        val weekRule = resolveWeekRule(entry.weekRule) ?: return app.getString(R.string.val_invalid_week_rule)
        val customWeeks = parseWeekList(entry.customWeekList) ?: return app.getString(R.string.val_invalid_custom_weeks)
        val skipWeeks = parseWeekList(entry.skipWeekList) ?: return app.getString(R.string.val_invalid_skip_weeks)
        val entryDate = parseEntryDate(entry.date) ?: return app.getString(R.string.val_invalid_date)
        val semesterStartDate = entry.semesterStartDate.takeIf { it.isNotBlank() }?.let { parseEntryDate(it) }
        return when {
            entry.title.isBlank() -> app.getString(R.string.val_empty_title)
            entry.title.length > 64 -> app.getString(R.string.val_title_too_long)
            entry.location.length > 64 -> app.getString(R.string.val_location_too_long)
            entry.note.length > 256 -> app.getString(R.string.val_note_too_long)
            entry.startMinutes !in 0 until 24 * 60 -> app.getString(R.string.val_invalid_start)
            entry.endMinutes !in 1..24 * 60 -> app.getString(R.string.val_invalid_end)
            entry.startMinutes >= entry.endMinutes -> app.getString(R.string.val_end_before_start)
            recurrence == RecurrenceType.WEEKLY && semesterStartDate == null -> app.getString(R.string.val_invalid_semester_date)
            recurrence == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isEmpty() -> {
                app.getString(R.string.val_empty_custom_weeks)
            }
            recurrence == RecurrenceType.WEEKLY && !occursOnDate(entry, entryDate) -> {
                app.getString(R.string.val_week_mismatch)
            }
            recurrence != RecurrenceType.WEEKLY && weekRule != WeekRule.ALL -> {
                app.getString(R.string.val_non_weekly_odd_even)
            }
            recurrence != RecurrenceType.WEEKLY && customWeeks.isNotEmpty() -> {
                app.getString(R.string.val_non_weekly_custom)
            }
            recurrence != RecurrenceType.WEEKLY && skipWeeks.isNotEmpty() -> {
                app.getString(R.string.val_non_weekly_skip)
            }
            else -> null
        }
    }


    private fun syncReminders(entriesList: List<TimetableEntry>, force: Boolean = false) {
        val generation = ++reminderSyncGeneration
        reminderSyncJob?.cancel()
        reminderSyncJob = viewModelScope.launch(Dispatchers.IO) {
            reminderSyncMutex.withLock {
                if (generation != reminderSyncGeneration) return@launch
                val syncToken = reminderSyncToken(
                    entries = entriesList,
                    reminderMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication()),
                )
                if (!force && syncToken == lastReminderSyncToken) return@launch
                CourseReminderScheduler.sync(getApplication(), entriesList)
                lastReminderSyncToken = syncToken
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

private fun formatImportSize(maxBytes: Int): String {
    val kibibyte = 1024
    val mebibyte = kibibyte * kibibyte
    return when {
        maxBytes >= mebibyte && maxBytes % mebibyte == 0 -> "${maxBytes / mebibyte} MB"
        maxBytes >= kibibyte && maxBytes % kibibyte == 0 -> "${maxBytes / kibibyte} KB"
        else -> "$maxBytes bytes"
    }
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

internal fun widgetRefreshToken(entries: List<TimetableEntry>): String {
    return JSONArray(entries.map(::entryTokenJson)).toString()
}

private fun entryTokenJson(entry: TimetableEntry): JSONObject {
    return JSONObject()
        .put("id", entry.id)
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
)
