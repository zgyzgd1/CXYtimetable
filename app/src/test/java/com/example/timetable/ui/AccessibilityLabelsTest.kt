package com.example.timetable.ui

import android.content.Context
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AccessibilityLabelsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun buildCalendarDayContentDescriptionIncludesStateAndCoursePresence() {
        val description = buildCalendarDayContentDescription(
            context = context,
            date = LocalDate.of(2026, 4, 23),
            selected = true,
            today = true,
            hasCourse = true,
        )

        assertTrue(description.contains("2026"))
        assertTrue(description.contains("4"))
        assertTrue(description.contains("23"))
    }

    @Test
    fun buildWeekEntryContentDescriptionIncludesLocationAndNote() {
        val entry = TimetableEntry(
            title = "数据库",
            date = "2026-04-23",
            dayOfWeek = 4,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            location = "B-201",
            note = "带实验报告",
        )

        val description = buildWeekEntryContentDescription(context, entry)

        assertTrue(description.contains("数据库"))
        assertTrue(description.contains("08:00"))
        assertTrue(description.contains("B-201"))
        assertTrue(description.contains("带实验报告"))
    }

    @Test
    fun buildTimeSlotContentDescriptionUsesIndexAndRange() {
        val description = buildTimeSlotContentDescription(
            context = context,
            index = 1,
            slot = WeekTimeSlot(startMinutes = 10 * 60, endMinutes = 11 * 60 + 30),
        )

        assertTrue(description.contains("2"))
        assertTrue(description.contains("10:00"))
        assertTrue(description.contains("11:30"))
    }
}
