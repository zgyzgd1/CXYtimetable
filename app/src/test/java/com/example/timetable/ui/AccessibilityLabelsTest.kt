package com.example.timetable.ui

import android.content.Context
import com.example.timetable.R
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.dayLabel
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
        val date = LocalDate.of(2026, 4, 23)
        val description = buildCalendarDayContentDescription(
            context = context,
            date = date,
            selected = true,
            today = true,
            hasCourse = true,
        )

        assertEquals(
            listOf(
                context.getString(R.string.a11y_date_full, 2026, 4, 23, dayLabel(date.dayOfWeek.value, context)),
                context.getString(R.string.a11y_today),
                context.getString(R.string.a11y_selected),
                context.getString(R.string.a11y_has_course),
            ).joinToString("\uFF0C"),
            description,
        )
    }

    @Test
    fun buildWeekEntryContentDescriptionIncludesLocationAndNote() {
        val entry = TimetableEntry(
            title = "Database",
            date = "2026-04-23",
            dayOfWeek = 4,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            location = "B-201",
            note = "Bring report",
        )

        val description = buildWeekEntryContentDescription(context, entry)

        assertEquals(
            listOf(
                "Database",
                context.getString(R.string.a11y_time_to, "08:00", "09:00"),
                context.getString(R.string.a11y_location, "B-201"),
                context.getString(R.string.a11y_note, "Bring report"),
                context.getString(R.string.a11y_tap_to_edit),
            ).joinToString("\uFF0C"),
            description,
        )
    }

    @Test
    fun buildTimeSlotContentDescriptionUsesIndexAndRange() {
        val description = buildTimeSlotContentDescription(
            context = context,
            index = 1,
            slot = WeekTimeSlot(startMinutes = 10 * 60, endMinutes = 11 * 60 + 30),
        )

        assertEquals(
            context.getString(R.string.a11y_slot_index, 2, "10:00", "11:30"),
            description,
        )
    }
}
