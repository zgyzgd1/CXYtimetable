package com.example.timetable.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableModelsTest {

    @Test
    fun occursOnDateForNoneRecurrenceMatchesExactDateOnly() {
        val entry = TimetableEntry(
            title = "Discrete Math",
            date = "2026-03-09",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )

        assertTrue(occursOnDate(entry, LocalDate.of(2026, 3, 9)))
        assertFalse(occursOnDate(entry, LocalDate.of(2026, 3, 16)))
    }

    @Test
    fun occursOnDateForOddWeeklyRuleOnlyMatchesOddWeeks() {
        val entry = TimetableEntry(
            title = "Data Structure",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
        )

        assertTrue(occursOnDate(entry, LocalDate.of(2026, 3, 2)))
        assertFalse(occursOnDate(entry, LocalDate.of(2026, 3, 9)))
        assertTrue(occursOnDate(entry, LocalDate.of(2026, 3, 16)))
    }

    @Test
    fun occursOnDateForCustomRuleRespectsSkipWeeks() {
        val entry = TimetableEntry(
            title = "Operating System",
            date = "2026-03-03",
            dayOfWeek = 2,
            startMinutes = 14 * 60,
            endMinutes = 15 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "1,3,5",
            skipWeekList = "3",
        )

        assertTrue(occursOnDate(entry, LocalDate.of(2026, 3, 3)))
        assertFalse(occursOnDate(entry, LocalDate.of(2026, 3, 17)))
        assertTrue(occursOnDate(entry, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun nextOccurrenceDateFindsNextOddWeekOccurrence() {
        val entry = TimetableEntry(
            title = "Data Structure",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
        )

        val nextDate = nextOccurrenceDate(entry, LocalDate.of(2026, 3, 10))

        assertEquals(LocalDate.of(2026, 3, 16), nextDate)
    }

    @Test
    fun nextOccurrenceDateRespectsCustomWeeksAndSkipWeeks() {
        val entry = TimetableEntry(
            title = "Operating System",
            date = "2026-03-03",
            dayOfWeek = 2,
            startMinutes = 14 * 60,
            endMinutes = 15 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "1,3,8",
            skipWeekList = "3",
        )

        val nextDate = nextOccurrenceDate(entry, LocalDate.of(2026, 3, 10))

        assertEquals(LocalDate.of(2026, 4, 21), nextDate)
    }

    @Test
    fun parseWeekListSupportsRangeAndRejectsInvalidText() {
        assertEquals(setOf(1, 2, 3, 6), parseWeekList("1-3,6"))
        assertNull(parseWeekList("2-a"))
        assertNull(parseWeekList("${MAX_WEEK_LIST_WEEK + 1}"))
        assertNull(parseWeekList("${MAX_WEEK_LIST_WEEK - 1}-${MAX_WEEK_LIST_WEEK + 1}"))
    }
}
