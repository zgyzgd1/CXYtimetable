package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduleViewModelSyncTokenTest {
    @Test
    fun reminderSyncTokenIsStableForSameEntriesAndReminderMinutes() {
        val entries = listOf(sampleEntry())

        val first = reminderSyncToken(entries, listOf(20, 5))
        val second = reminderSyncToken(entries, listOf(5, 20, 20))

        assertEquals(first, second)
    }

    @Test
    fun reminderSyncTokenChangesWhenReminderMinutesChange() {
        val entries = listOf(sampleEntry())

        val first = reminderSyncToken(entries, listOf(20))
        val second = reminderSyncToken(entries, listOf(10))

        assertNotEquals(first, second)
    }

    @Test
    fun reminderSyncTokenChangesWhenReminderContentChanges() {
        val original = listOf(sampleEntry(title = "高数", location = "A-101"))
        val updated = listOf(sampleEntry(title = "线代", location = "B-202"))

        val first = reminderSyncToken(original, listOf(20))
        val second = reminderSyncToken(updated, listOf(20))

        assertNotEquals(first, second)
    }

    @Test
    fun widgetRefreshTokenChangesWhenWidgetVisibleFieldsChange() {
        val original = listOf(sampleEntry(title = "高数", location = "A-101"))
        val updated = listOf(sampleEntry(title = "线代", location = "B-202"))

        val first = widgetRefreshToken(original)
        val second = widgetRefreshToken(updated)

        assertNotEquals(first, second)
    }

    @Test
    fun reminderSyncTokenDoesNotCollideWhenFieldContainsDelimiters() {
        val first = listOf(sampleEntry(title = "A|B", location = "C\nD"))
        val second = listOf(sampleEntry(title = "A", location = "B|C\nD"))

        val firstToken = reminderSyncToken(first, listOf(20))
        val secondToken = reminderSyncToken(second, listOf(20))

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun widgetRefreshTokenDoesNotCollideWhenFieldContainsDelimiters() {
        val first = listOf(sampleEntry(title = "A|B", location = "C\nD"))
        val second = listOf(sampleEntry(title = "A", location = "B|C\nD"))

        val firstToken = widgetRefreshToken(first)
        val secondToken = widgetRefreshToken(second)

        assertNotEquals(firstToken, secondToken)
    }

    private fun sampleEntry(
        title: String = "高等数学",
        location: String = "A-101",
    ): TimetableEntry {
        return TimetableEntry(
            id = "entry-1",
            title = title,
            date = "2026-04-27",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            location = location,
            recurrenceType = "WEEKLY",
            semesterStartDate = "2026-02-23",
            weekRule = "ALL",
        )
    }
}
