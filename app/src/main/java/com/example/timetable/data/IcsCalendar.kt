package com.example.timetable.data

object IcsCalendar {
    fun write(entries: List<TimetableEntry>, calendarName: String = "Timetable"): String {
        return IcsExport.write(entries, calendarName)
    }

    fun parse(content: String): List<TimetableEntry> {
        return IcsImport.parse(content)
    }
}
