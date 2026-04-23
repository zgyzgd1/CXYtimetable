package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.WeekRule
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.occursOnDate
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.parseWeekList
import com.example.timetable.data.resolveRecurrenceType
import com.example.timetable.data.resolveWeekRule
import com.example.timetable.data.weekIndexFromSemesterStart
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.widget.TimetableWidgetUpdater
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
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
        viewModelScope.launch {
            TimetableRepository.ensureMigrated(getApplication())
        }
        viewModelScope.launch {
            entries.collect { currentEntries ->
                syncReminders(currentEntries)
                refreshWidgets(currentEntries)
            }
        }
    }

    fun previewConflict(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        return findConflictForEntry(normalized, entries.value)
    }

    fun suggestResolvedEntry(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        return suggestAdjustedEntryAfterConflicts(normalized, entries.value)
    }

    fun upsertEntry(entry: TimetableEntry, allowConflict: Boolean = false) {
        val normalized = normalizeEntry(entry)
        validateEntry(normalized)?.let {
            postMessage("保存失败：$it")
            return
        }
        val conflict = findConflictForEntry(normalized, entries.value)
        if (conflict != null && !allowConflict) {
            postMessage(
                "检测到冲突：${conflict.title} " +
                    "${formatMinutes(conflict.startMinutes)}-${formatMinutes(conflict.endMinutes)}，请调整后再保存",
            )
            return
        }

        viewModelScope.launch {
            TimetableRepository.upsertEntry(getApplication(), normalized)

            if (conflict == null) {
                postMessage("已保存课程")
            } else {
                postMessage(
                    "已保存课程，但与 ${conflict.title} " +
                        "${formatMinutes(conflict.startMinutes)}-${formatMinutes(conflict.endMinutes)} 时间重叠",
                )
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            TimetableRepository.deleteEntry(getApplication(), entryId)
            postMessage("已删除课程")
        }
    }

    suspend fun exportIcs(): String = withContext(Dispatchers.Default) {
        IcsCalendar.write(entries.value)
    }

    fun importFromIcs(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = readText(contentResolver, uri)
            if (text.isBlank()) {
                postMessage("导入失败：文件内容为空")
                return@launch
            }

            val imported = try {
                withContext(Dispatchers.Default) {
                    IcsCalendar.parse(text)
                }
            } catch (error: Exception) {
                postMessage("导入失败：${error.message ?: "日历格式异常"}")
                emptyList()
            }
            if (imported.isEmpty()) {
                postMessage("未识别到可导入的课程")
                return@launch
            }

            applyImportedEntries(imported)
        }
    }

    fun updateReminderMinutes(minutes: Iterable<Int>) {
        val normalizedMinutes = CourseReminderScheduler.normalizeReminderMinutes(minutes)
        if (normalizedMinutes.isEmpty()) return
        val currentMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication())
        if (normalizedMinutes == currentMinutes) return
        CourseReminderScheduler.setReminderMinutes(getApplication(), normalizedMinutes)
        syncReminders(entries.value)
        postMessage("已设置为提前 ${CourseReminderScheduler.formatReminderSelection(normalizedMinutes)} 提醒")
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
                postMessage("读取文件失败：${it.message ?: "未知错误"}")
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

    private suspend fun applyImportedEntries(imported: List<TimetableEntry>) {
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

        if (validEntries.isEmpty()) {
            postMessage("导入失败：未发现有效课程")
            return
        }

        val conflictCount = countConflictPairs(validEntries)

        TimetableRepository.replaceAllEntries(getApplication(), validEntries)

        if (invalidCount == 0 && conflictCount == 0) {
            postMessage("已导入 ${validEntries.size} 条课程，并保存为当前课表")
        } else {
            postMessage(
                "已导入 ${validEntries.size} 条课程，跳过无效 $invalidCount 条，检测到冲突 $conflictCount 组",
            )
        }
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
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: return "重复规则无效"
        val weekRule = resolveWeekRule(entry.weekRule) ?: return "周次规则无效"
        val customWeeks = parseWeekList(entry.customWeekList) ?: return "自定义周次格式错误"
        val skipWeeks = parseWeekList(entry.skipWeekList) ?: return "跳过周次格式错误"
        val entryDate = parseEntryDate(entry.date) ?: return "课程日期无效"
        val semesterStartDate = entry.semesterStartDate.takeIf { it.isNotBlank() }?.let { parseEntryDate(it) }
        return when {
            entry.title.isBlank() -> "课程名称不能为空"
            entry.title.length > 64 -> "课程名称不能超过 64 个字符"
            entry.location.length > 64 -> "地点不能超过 64 个字符"
            entry.note.length > 256 -> "备注不能超过 256 个字符"
            entry.startMinutes !in 0 until 24 * 60 -> "开始时间不合法"
            entry.endMinutes !in 1..24 * 60 -> "结束时间不合法"
            entry.startMinutes >= entry.endMinutes -> "结束时间需要晚于开始时间"
            recurrence == RecurrenceType.WEEKLY && semesterStartDate == null -> "请设置合法的学期开学日期"
            recurrence == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isEmpty() -> {
                "自定义周次不能为空"
            }
            recurrence == RecurrenceType.WEEKLY && !occursOnDate(entry, entryDate) -> {
                "首次上课日期不符合当前周次规则"
            }
            recurrence != RecurrenceType.WEEKLY && weekRule != WeekRule.ALL -> {
                "仅按周循环课程支持单双周或自定义周"
            }
            recurrence != RecurrenceType.WEEKLY && customWeeks.isNotEmpty() -> {
                "仅按周循环课程支持自定义周次"
            }
            recurrence != RecurrenceType.WEEKLY && skipWeeks.isNotEmpty() -> {
                "仅按周循环课程支持跳周"
            }
            else -> null
        }
    }

    private fun normalizeWeekListText(raw: String): String {
        return raw.trim()
            .replace('，', ',')
            .replace(" ", "")
    }

    private fun findConflict(target: TimetableEntry, entriesList: List<TimetableEntry>): TimetableEntry? {
        return findConflictForEntry(target, entriesList)
    }

    private fun countConflictPairs(entriesList: List<TimetableEntry>): Int {
        var pairs = 0
        val sorted = entriesList.sortedBy { it.startMinutes }
        for (index in sorted.indices) {
            val current = sorted[index]
            for (nextIndex in index + 1 until sorted.size) {
                val next = sorted[nextIndex]
                if (current.startMinutes >= next.endMinutes || next.startMinutes >= current.endMinutes) {
                    continue
                }
                if (entriesShareAnyOccurrenceDate(current, next)) {
                    pairs++
                }
            }
        }
        return pairs
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

internal fun findConflictForEntry(
    target: TimetableEntry,
    entriesList: List<TimetableEntry>,
): TimetableEntry? {
    return entriesList.firstOrNull { existing ->
        existing.id != target.id &&
            target.startMinutes < existing.endMinutes &&
            existing.startMinutes < target.endMinutes &&
            entriesShareAnyOccurrenceDate(target, existing)
    }
}

internal fun suggestAdjustedEntryAfterConflicts(
    target: TimetableEntry,
    entriesList: List<TimetableEntry>,
): TimetableEntry? {
    val duration = target.endMinutes - target.startMinutes
    if (duration <= 0) return null

    val conflictingEntries = entriesList
        .asSequence()
        .filter { it.id != target.id && entriesShareAnyOccurrenceDate(it, target) }
        .sortedBy { it.startMinutes }
        .toList()

    var candidateStart = target.startMinutes
    var candidateEnd = candidateStart + duration
    if (candidateEnd > 24 * 60) return null

    for (existing in conflictingEntries) {
        if (existing.endMinutes <= candidateStart) continue
        if (existing.startMinutes >= candidateEnd) break
        candidateStart = existing.endMinutes
        candidateEnd = candidateStart + duration
        if (candidateEnd > 24 * 60) return null
    }

    return target.copy(
        startMinutes = candidateStart,
        endMinutes = candidateEnd,
    )
}

private fun entriesShareAnyOccurrenceDate(
    first: TimetableEntry,
    second: TimetableEntry,
): Boolean {
    val firstRecurrence = resolveRecurrenceType(first.recurrenceType) ?: RecurrenceType.NONE
    val secondRecurrence = resolveRecurrenceType(second.recurrenceType) ?: RecurrenceType.NONE
    val firstDate = parseEntryDate(first.date) ?: return false
    val secondDate = parseEntryDate(second.date) ?: return false

    if (firstRecurrence == RecurrenceType.NONE && secondRecurrence == RecurrenceType.NONE) {
        return firstDate == secondDate
    }
    if (firstRecurrence == RecurrenceType.NONE) {
        return occursOnDate(second, firstDate)
    }
    if (secondRecurrence == RecurrenceType.NONE) {
        return occursOnDate(first, secondDate)
    }
    if (first.dayOfWeek != second.dayOfWeek) return false

    val firstCustomDates = customOccurrenceDates(first)
    val secondCustomDates = customOccurrenceDates(second)
    return when {
        firstCustomDates != null && secondCustomDates != null -> {
            val secondDateSet = secondCustomDates.toHashSet()
            firstCustomDates.any(secondDateSet::contains)
        }
        firstCustomDates != null -> firstCustomDates.any { occursOnDate(second, it) }
        secondCustomDates != null -> secondCustomDates.any { occursOnDate(first, it) }
        else -> {
            val searchStart = maxOf(firstDate, secondDate)
            val candidateCount = maxOf(relevantSearchWeeks(first), relevantSearchWeeks(second)) + 2
            generateSequence(searchStart) { it.plusWeeks(1) }
                .take(candidateCount.coerceAtLeast(2))
                .any { date -> occursOnDate(first, date) && occursOnDate(second, date) }
        }
    }
}

private fun customOccurrenceDates(entry: TimetableEntry): List<LocalDate>? {
    val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: return null
    if (recurrence != RecurrenceType.WEEKLY) return null
    val weekRule = resolveWeekRule(entry.weekRule) ?: return null
    if (weekRule != WeekRule.CUSTOM) return null

    val firstDate = parseEntryDate(entry.date) ?: return emptyList()
    val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
        ?: firstDate
    val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val customWeeks = parseWeekList(entry.customWeekList).orEmpty()
    val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()
    val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)

    return customWeeks
        .asSequence()
        .filter { it !in skippedWeeks }
        .sorted()
        .map { weekNumber ->
            semesterWeekStart
                .plusWeeks((weekNumber - 1).toLong())
                .plusDays(dayOffset.toLong())
        }
        .filter { !it.isBefore(firstDate) && occursOnDate(entry, it) }
        .toList()
}

private fun relevantSearchWeeks(entry: TimetableEntry): Int {
    val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: return 0
    if (recurrence != RecurrenceType.WEEKLY) return 0

    val firstDate = parseEntryDate(entry.date) ?: return 0
    val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
        ?: firstDate
    val firstWeek = weekIndexFromSemesterStart(semesterStartDate, firstDate) ?: 1
    val customWeeks = parseWeekList(entry.customWeekList).orEmpty()
    val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()

    return maxOf(
        firstWeek,
        customWeeks.maxOrNull() ?: 0,
        skippedWeeks.maxOrNull() ?: 0,
    )
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
