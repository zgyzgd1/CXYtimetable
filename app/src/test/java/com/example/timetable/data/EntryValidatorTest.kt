package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryValidatorTest {
    @Test
    fun validateRejectsWeeklyEntryWhoseFirstDateDoesNotMatchCustomWeeks() {
        val entry = TimetableEntry(
            title = "Compiler",
            date = "2026-03-09",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "1",
        )

        assertEquals(EntryValidationError.WeekMismatch, EntryValidator.validate(entry))
    }

    @Test
    fun validateAcceptsWeeklyEntryWhoseFirstDateMatchesCustomWeeks() {
        val entry = TimetableEntry(
            title = "Compiler",
            date = "2026-03-09",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "2",
        )

        assertNull(EntryValidator.validate(entry))
    }
}
