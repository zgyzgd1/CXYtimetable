package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.notify.CourseReminderScheduler

internal fun reminderSyncToken(
    entries: List<TimetableEntry>,
    reminderMinutes: List<Int>,
): String {
    val normalizedReminderMinutes = CourseReminderScheduler.normalizeReminderMinutes(reminderMinutes)
    return buildToken {
        appendListHeader("reminders", normalizedReminderMinutes.size)
        normalizedReminderMinutes.forEach { appendInt(it) }
        appendEntries(entries)
    }
}

internal fun widgetRefreshToken(entries: List<TimetableEntry>): String {
    return buildToken {
        appendEntries(entries)
    }
}

private fun buildToken(block: StringBuilder.() -> Unit): String {
    return buildString(block)
}

private fun StringBuilder.appendEntries(entries: List<TimetableEntry>) {
    appendListHeader("entries", entries.size)
    entries.forEach { entry ->
        appendField(entry.id)
        appendField(entry.title)
        appendField(entry.date)
        appendInt(entry.dayOfWeek)
        appendInt(entry.startMinutes)
        appendInt(entry.endMinutes)
        appendField(entry.location)
        appendField(entry.note)
        appendField(entry.recurrenceType)
        appendField(entry.semesterStartDate)
        appendField(entry.weekRule)
        appendField(entry.customWeekList)
        appendField(entry.skipWeekList)
    }
}

private fun StringBuilder.appendListHeader(name: String, size: Int) {
    append(name)
    append('#')
    append(size)
    append(';')
}

private fun StringBuilder.appendInt(value: Int) {
    append(value)
    append(';')
}

private fun StringBuilder.appendField(value: String) {
    append(value.length)
    append(':')
    append(value)
    append(';')
}
