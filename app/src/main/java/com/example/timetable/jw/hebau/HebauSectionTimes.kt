package com.example.timetable.jw.hebau

import com.example.timetable.data.parseMinutes

object HebauSectionTimes {
    val default: List<HebauSectionTime> = listOf(
        HebauSectionTime(1, "08:00", "08:45"),
        HebauSectionTime(2, "08:55", "09:40"),
        HebauSectionTime(3, "10:10", "10:55"),
        HebauSectionTime(4, "11:05", "11:50"),
        HebauSectionTime(5, "14:30", "15:15"),
        HebauSectionTime(6, "15:25", "16:10"),
        HebauSectionTime(7, "16:20", "17:05"),
        HebauSectionTime(8, "17:15", "18:00"),
        HebauSectionTime(9, "18:30", "19:15"),
        HebauSectionTime(10, "19:25", "20:10"),
        HebauSectionTime(11, "20:20", "21:05"),
        HebauSectionTime(12, "21:15", "22:00"),
    )

    fun resolve(sectionTimes: List<HebauSectionTime>): Map<Int, SectionMinutes> {
        val base = default.associate { time ->
            time.section to SectionMinutes(
                startMinutes = parseMinutes(time.start) ?: 0,
                endMinutes = parseMinutes(time.end) ?: 0,
            )
        }.toMutableMap()

        sectionTimes.forEach { time ->
            val start = parseMinutes(time.start)
            val end = parseMinutes(time.end)
            if (time.section > 0 && start != null && end != null && start < end) {
                base[time.section] = SectionMinutes(start, end)
            }
        }
        return base
    }
}

data class SectionMinutes(
    val startMinutes: Int,
    val endMinutes: Int,
)
