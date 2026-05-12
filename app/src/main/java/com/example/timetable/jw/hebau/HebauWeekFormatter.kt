package com.example.timetable.jw.hebau

object HebauWeekFormatter {
    fun format(weeks: Iterable<Int>): String {
        val sortedWeeks = weeks
            .filter { it > 0 }
            .distinct()
            .sorted()
        if (sortedWeeks.isEmpty()) return ""

        val ranges = mutableListOf<String>()
        var rangeStart = sortedWeeks.first()
        var previous = rangeStart

        for (week in sortedWeeks.drop(1)) {
            if (week == previous + 1) {
                previous = week
                continue
            }
            ranges += formatRange(rangeStart, previous)
            rangeStart = week
            previous = week
        }
        ranges += formatRange(rangeStart, previous)
        return ranges.joinToString(",")
    }

    private fun formatRange(start: Int, end: Int): String {
        return if (start == end) start.toString() else "$start-$end"
    }
}
