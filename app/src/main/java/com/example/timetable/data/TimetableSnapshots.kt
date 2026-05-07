package com.example.timetable.data

import android.content.Context
import com.example.timetable.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * context == null 时的回退字符串（用于单元测试和无上下文场景）。
 * 这些常量集中管理便于未来国际化时统一替换。
 */
private object FbStrings {
    const val ONGOING_REMAINING = "正在进行，距离下课 %d 分钟"
    const val ONGOING = "正在进行"
    const val STARTING_SOON = "即将开始"
    const val MINUTES_LATER = "%d 分钟后开始"
    const val TODAY = "今天"
    const val TOMORROW = "明天"
    const val DAY_AFTER_TOMORROW = "后天"
    const val DAYS_LATER = "%d 天后"
    const val DAY_START = "%s %s 开始"
}

internal data class NextCourseSnapshot(
    val entry: TimetableEntry,
    val occurrenceDate: LocalDate,
    val statusText: String,
)

internal fun findNextCourseSnapshot(
    entries: List<TimetableEntry>,
    nowDate: LocalDate = LocalDate.now(),
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    context: Context? = null,
): NextCourseSnapshot? {
    val todayEntries = entriesForDate(entries, nowDate)

    val ongoingEntry = todayEntries.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }
    if (ongoingEntry != null) {
        val remainingMinutes = (ongoingEntry.endMinutes - nowMinutes).coerceAtLeast(0)
        val status = if (context != null) {
            if (remainingMinutes > 0) {
                context.getString(R.string.status_ongoing_remaining, remainingMinutes)
            } else {
                context.getString(R.string.status_ongoing)
            }
        } else {
            if (remainingMinutes > 0) {
                FbStrings.ONGOING_REMAINING.format(remainingMinutes)
            } else {
                FbStrings.ONGOING
            }
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
        val status = if (context != null) {
            if (waitMinutes == 0) {
                context.getString(R.string.status_starting_soon)
            } else {
                context.getString(R.string.status_minutes_later, waitMinutes)
            }
        } else {
            if (waitMinutes == 0) {
                FbStrings.STARTING_SOON
            } else {
                FbStrings.MINUTES_LATER.format(waitMinutes)
            }
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
    val dayLabel = if (context != null) {
        when (dayOffset) {
            0 -> context.getString(R.string.widget_today)
            1 -> context.getString(R.string.widget_tomorrow)
            2 -> context.getString(R.string.widget_day_after_tomorrow)
            else -> context.getString(R.string.status_days_later, dayOffset)
        }
    } else {
        when (dayOffset) {
            0 -> FbStrings.TODAY
            1 -> FbStrings.TOMORROW
            2 -> FbStrings.DAY_AFTER_TOMORROW
            else -> FbStrings.DAYS_LATER.format(dayOffset)
        }
    }
    val startLabel = formatMinutes(resolvedEntry.startMinutes)
    return NextCourseSnapshot(
        entry = resolvedEntry,
        occurrenceDate = resolvedDate,
        statusText = if (context != null) {
            context.getString(R.string.status_day_start, dayLabel, startLabel)
        } else {
            FbStrings.DAY_START.format(dayLabel, startLabel)
        },
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

    private val rangeCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<RangeKey, Map<LocalDate, List<TimetableEntry>>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<RangeKey, Map<LocalDate, List<TimetableEntry>>>?,
            ): Boolean {
                return size > maxRanges
            }
        }
    )

    fun resolve(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, List<TimetableEntry>> {
        if (endDate.isBefore(startDate)) return emptyMap()

        val key = RangeKey(startDate, endDate)
        return synchronized(rangeCache) {
            rangeCache[key] ?: entriesByDateInRange(entries, startDate, endDate).also {
                rangeCache[key] = it
            }
        }
    }
}
