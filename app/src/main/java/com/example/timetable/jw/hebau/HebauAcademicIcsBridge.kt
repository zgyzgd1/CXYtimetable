package com.example.timetable.jw.hebau

import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.TimetableEntry

object HebauAcademicIcsBridge {
    fun normalize(entries: List<TimetableEntry>, calendarName: String): List<TimetableEntry> {
        if (entries.isEmpty()) return emptyList()
        val ics = IcsCalendar.write(entries, calendarName)
        return IcsCalendar.parse(ics).ifEmpty { entries }
    }
}
