package com.example.timetable.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun findConflictForEntry(
    target: TimetableEntry,
    entriesList: List<TimetableEntry>,
): TimetableEntry? {
    return entriesList.firstOrNull { existing ->
        existing.id != target.id &&
            timeRangesOverlap(target, existing) &&
            entriesShareAnyOccurrenceDate(target, existing)
    }
}

fun suggestAdjustedEntryAfterConflicts(
    target: TimetableEntry,
    entriesList: List<TimetableEntry>,
): TimetableEntry? {
    val duration = target.endMinutes - target.startMinutes
    if (duration <= 0) return null

    val conflictingEntries = entriesList
        .asSequence()
        .filter { it.id != target.id && it.dayOfWeek == target.dayOfWeek && entriesShareAnyOccurrenceDate(it, target) }
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

fun countConflictPairs(entriesList: List<TimetableEntry>): Int {
    var pairs = 0
    val sorted = entriesList.sortedBy { it.startMinutes }
    for (index in sorted.indices) {
        val current = sorted[index]
        for (nextIndex in index + 1 until sorted.size) {
            val next = sorted[nextIndex]
            if (!timeRangesOverlap(current, next)) continue
            if (entriesShareAnyOccurrenceDate(current, next)) {
                pairs++
            }
        }
    }
    return pairs
}

internal fun entriesShareAnyOccurrenceDate(
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
            val firstRule = resolveWeekRule(first.weekRule) ?: WeekRule.ALL
            val secondRule = resolveWeekRule(second.weekRule) ?: WeekRule.ALL
            val firstSkips = parseWeekList(first.skipWeekList).orEmpty()
            val secondSkips = parseWeekList(second.skipWeekList).orEmpty()

            if (firstRule == WeekRule.ODD && secondRule == WeekRule.EVEN && firstSkips.isEmpty() && secondSkips.isEmpty()) {
                false
            } else if (firstRule == WeekRule.EVEN && secondRule == WeekRule.ODD && firstSkips.isEmpty() && secondSkips.isEmpty()) {
                false
            } else {
                val searchStart = maxOf(firstDate, secondDate)
                val candidateCount = maxOf(relevantSearchWeeks(first), relevantSearchWeeks(second)) + 6
                generateSequence(searchStart) { it.plusWeeks(1) }
                    .take(candidateCount.coerceIn(2, 30))
                    .any { date -> occursOnDate(first, date) && occursOnDate(second, date) }
            }
        }
    }
}

private fun timeRangesOverlap(first: TimetableEntry, second: TimetableEntry): Boolean {
    return first.startMinutes < second.endMinutes && second.startMinutes < first.endMinutes
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
