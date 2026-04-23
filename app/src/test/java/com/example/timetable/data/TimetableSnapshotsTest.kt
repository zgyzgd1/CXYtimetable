package com.example.timetable.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSnapshotsTest {
    @Test
    fun entriesByDateInRangeReturnsEmptyMapForReversedRange() {
        val result = entriesByDateInRange(
            entries = emptyList(),
            startDate = LocalDate.of(2026, 4, 24),
            endDate = LocalDate.of(2026, 4, 20),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun entriesByDateInRangeIndexesRecurringEntriesAcrossVisibleWindow() {
        val weekStart = LocalDate.of(2026, 4, 20)
        val weekEnd = weekStart.plusDays(6)
        val recurring = TimetableEntry(
            title = "Compiler",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val single = TimetableEntry(
            title = "Database",
            date = "2026-04-22",
            dayOfWeek = 3,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )

        val result = entriesByDateInRange(listOf(recurring, single), weekStart, weekEnd)

        assertEquals(listOf("Compiler"), result.getValue(LocalDate.of(2026, 4, 20)).map { it.title })
        assertEquals(listOf("Database"), result.getValue(LocalDate.of(2026, 4, 22)).map { it.title })
        assertTrue(result.getValue(LocalDate.of(2026, 4, 24)).isEmpty())
    }

    @Test
    fun entriesByDateInRangeSortsEntriesWithinSameDay() {
        val date = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            TimetableEntry(
                title = "Later",
                date = date.toString(),
                dayOfWeek = date.dayOfWeek.value,
                startMinutes = 11 * 60,
                endMinutes = 12 * 60,
            ),
            TimetableEntry(
                title = "Earlier",
                date = date.toString(),
                dayOfWeek = date.dayOfWeek.value,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
        )

        val result = entriesByDateInRange(entries, date, date)

        assertEquals(listOf("Earlier", "Later"), result.getValue(date).map { it.title })
    }

    @Test
    fun entriesByDateInRangeRespectsCustomWeeksAndSkippedWeeks() {
        val rangeStart = LocalDate.of(2026, 4, 20)
        val rangeEnd = LocalDate.of(2026, 5, 3)
        val recurring = TimetableEntry(
            title = "Operating Systems",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "8,9",
            skipWeekList = "9",
        )

        val result = entriesByDateInRange(listOf(recurring), rangeStart, rangeEnd)

        assertEquals(listOf("Operating Systems"), result.getValue(LocalDate.of(2026, 4, 20)).map { it.title })
        assertTrue(result.getValue(LocalDate.of(2026, 4, 27)).isEmpty())
    }

    @Test
    fun entriesByDateInRangeSupportsLargeRecurringWindow() {
        val rangeStart = LocalDate.of(2026, 3, 2)
        val rangeEnd = rangeStart.plusWeeks(59).plusDays(6)
        val recurring = TimetableEntry(
            title = "Large Window Course",
            date = rangeStart.toString(),
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = rangeStart.toString(),
            weekRule = WeekRule.ALL.name,
        )

        val result = entriesByDateInRange(listOf(recurring), rangeStart, rangeEnd)
        val daysWithEntries = result.filterValues { it.isNotEmpty() }.keys.sorted()

        assertEquals(60, daysWithEntries.size)
        assertEquals(rangeStart, daysWithEntries.first())
        assertEquals(rangeStart.plusWeeks(59), daysWithEntries.last())
    }

    @Test
    fun dateRangeEntriesCacheReusesAndEvictsRanges() {
        val baseDate = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            TimetableEntry(
                title = "Compiler",
                date = baseDate.toString(),
                dayOfWeek = baseDate.dayOfWeek.value,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
        )
        val cache = DateRangeEntriesCache(entries = entries, maxRanges = 2)

        val first = cache.resolve(baseDate, baseDate)
        val firstAgain = cache.resolve(baseDate, baseDate)
        cache.resolve(baseDate.plusDays(1), baseDate.plusDays(1))
        cache.resolve(baseDate.plusDays(2), baseDate.plusDays(2))
        val firstAfterEviction = cache.resolve(baseDate, baseDate)

        assertTrue(first === firstAgain)
        assertTrue(first !== firstAfterEviction)
    }
}
