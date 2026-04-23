package com.example.timetable.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal data class NextCourseSnapshot(
    val entry: TimetableEntry,
    val occurrenceDate: LocalDate,
    val statusText: String,
)

internal fun findNextCourseSnapshot(
    entries: List<TimetableEntry>,
    nowDate: LocalDate = LocalDate.now(),
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
): NextCourseSnapshot? {
    val todayEntries = entriesForDate(entries, nowDate)

    val ongoingEntry = todayEntries.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }
    if (ongoingEntry != null) {
        val remainingMinutes = (ongoingEntry.endMinutes - nowMinutes).coerceAtLeast(0)
        val status = if (remainingMinutes > 0) {
            "正在进行，距离下课 $remainingMinutes 分钟"
        } else {
            "正在进行"
        }
        return NextCourseSnapshot(
            entry = ongoingEntry,
            occurrenceDate = nowDate,
            statusText = status,
        )
    }

    val nextTodayEntry = todayEntries.firstOrNull { it.startMinutes >= nowMinutes }
    if (nextTodayEntry != null) {
        val waitMinutes = (nextTodayEntry.startMinutes - nowMinutes).coerceAtLeast(0)
        val status = if (waitMinutes == 0) {
            "即将开始"
        } else {
            "$waitMinutes 分钟后开始"
        }
        return NextCourseSnapshot(
            entry = nextTodayEntry,
            occurrenceDate = nowDate,
            statusText = status,
        )
    }

    val futureOccurrence = entries
        .asSequence()
        .mapNotNull { entry ->
            val nextDate = nextOccurrenceDate(entry, nowDate.plusDays(1)) ?: return@mapNotNull null
            entry to nextDate
        }
        .minWithOrNull(
            compareBy<Pair<TimetableEntry, LocalDate>>(
                { it.second },
                { it.first.startMinutes },
                { it.first.endMinutes },
                { it.first.title },
            ),
        )
        ?: return null

    val resolvedEntry = futureOccurrence.first
    val resolvedDate = futureOccurrence.second
    val dayOffset = ChronoUnit.DAYS.between(nowDate, resolvedDate).toInt().coerceAtLeast(0)
    val dayLabel = when (dayOffset) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> "$dayOffset 天后"
    }
    return NextCourseSnapshot(
        entry = resolvedEntry,
        occurrenceDate = resolvedDate,
        statusText = "$dayLabel ${formatMinutes(resolvedEntry.startMinutes)} 开始",
    )
}

internal fun entriesForDate(
    entries: List<TimetableEntry>,
    date: LocalDate,
): List<TimetableEntry> {
    return entriesByDateInRange(entries, date, date)[date].orEmpty()
}

internal fun entriesByDateInRange(
    entries: List<TimetableEntry>,
    startDate: LocalDate,
    endDate: LocalDate,
): Map<LocalDate, List<TimetableEntry>> {
    if (endDate.isBefore(startDate)) return emptyMap()

    val groupedEntries = linkedMapOf<LocalDate, MutableList<TimetableEntry>>()
    var cursor = startDate
    while (!cursor.isAfter(endDate)) {
        groupedEntries[cursor] = mutableListOf()
        cursor = cursor.plusDays(1)
    }

    entries.forEach { entry ->
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        if (recurrence == RecurrenceType.NONE) {
            val entryDate = parseEntryDate(entry.date) ?: return@forEach
            if (entryDate in groupedEntries && occursOnDate(entry, entryDate)) {
                groupedEntries.getValue(entryDate).add(entry)
            }
            return@forEach
        }

        var occurrenceDate = nextOccurrenceDate(entry, startDate) ?: return@forEach
        while (!occurrenceDate.isAfter(endDate)) {
            groupedEntries[occurrenceDate]?.add(entry)
            occurrenceDate = nextOccurrenceDate(entry, occurrenceDate.plusDays(1)) ?: break
        }
    }

    return groupedEntries.mapValues { (_, dayEntries) ->
        dayEntries.sortedBy { it.startMinutes }
    }
}

internal class DateRangeEntriesCache(
    private val entries: List<TimetableEntry>,
    private val maxRanges: Int = 8,
) {
    private data class RangeKey(
        val startDate: LocalDate,
        val endDate: LocalDate,
    )

    private val rangeCache = object : LinkedHashMap<RangeKey, Map<LocalDate, List<TimetableEntry>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<RangeKey, Map<LocalDate, List<TimetableEntry>>>?,
        ): Boolean {
            return size > maxRanges
        }
    }

    fun resolve(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, List<TimetableEntry>> {
        if (endDate.isBefore(startDate)) return emptyMap()

        val key = RangeKey(startDate, endDate)
        val cached = rangeCache[key]
        if (cached != null) return cached

        val computed = entriesByDateInRange(entries, startDate, endDate)
        rangeCache[key] = computed
        return computed
    }
}
