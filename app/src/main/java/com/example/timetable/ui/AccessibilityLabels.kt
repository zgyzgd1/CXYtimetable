package com.example.timetable.ui

import android.content.Context
import com.example.timetable.R
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.dayLabel
import com.example.timetable.data.formatMinutes
import java.time.LocalDate

internal fun buildCalendarDayContentDescription(
    context: Context,
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    hasCourse: Boolean,
): String {
    val parts = mutableListOf(
        context.getString(R.string.a11y_date_full, date.year, date.monthValue, date.dayOfMonth, dayLabel(date.dayOfWeek.value)),
    )
    if (today) parts += context.getString(R.string.a11y_today)
    if (selected) parts += context.getString(R.string.a11y_selected)
    if (hasCourse) parts += context.getString(R.string.a11y_has_course)
    return parts.joinToString("，")
}

internal fun buildWeekCalendarDayContentDescription(
    context: Context,
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
): String {
    val parts = mutableListOf(
        context.getString(R.string.a11y_date_short, date.monthValue, date.dayOfMonth, dayLabel(date.dayOfWeek.value)),
    )
    if (today) parts += context.getString(R.string.a11y_today)
    if (selected) parts += context.getString(R.string.a11y_currently_selected)
    return parts.joinToString("，")
}

internal fun buildWeekEntryContentDescription(context: Context, entry: TimetableEntry): String {
    val parts = mutableListOf(
        entry.title.ifBlank { context.getString(R.string.label_unnamed_course) },
        context.getString(R.string.a11y_time_to, formatMinutes(entry.startMinutes), formatMinutes(entry.endMinutes)),
    )
    if (entry.location.isNotBlank()) parts += context.getString(R.string.a11y_location, entry.location)
    if (entry.note.isNotBlank()) parts += context.getString(R.string.a11y_note, entry.note)
    parts += context.getString(R.string.a11y_tap_to_edit)
    return parts.joinToString("，")
}

internal fun buildTimeSlotContentDescription(context: Context, index: Int, slot: WeekTimeSlot): String {
    return context.getString(R.string.a11y_slot_index, index + 1, formatMinutes(slot.startMinutes), formatMinutes(slot.endMinutes))
}

internal fun buildHeroActionContentDescription(context: Context, label: String): String {
    return context.getString(R.string.a11y_button, label)
}
