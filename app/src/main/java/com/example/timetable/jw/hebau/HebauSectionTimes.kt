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
        return resolve(defaultSectionTimes = default, sectionTimes = sectionTimes)
    }

    internal fun resolve(
        defaultSectionTimes: List<HebauSectionTime>,
        sectionTimes: List<HebauSectionTime>,
    ): Map<Int, SectionMinutes> {
        val base = defaultSectionTimes.associate { time ->
            time.section to requireSectionMinutes(time, label = "default section ${time.section}")
        }.toMutableMap()

        sectionTimes.forEach { time ->
            if (time.section > 0) {
                parseSectionMinutes(time)?.let { base[time.section] = it }
            }
        }
        return base
    }

    private fun requireSectionMinutes(time: HebauSectionTime, label: String): SectionMinutes {
        val start = parseMinutes(time.start)
            ?: throw IllegalArgumentException("$label start time is invalid.")
        val end = parseMinutes(time.end)
            ?: throw IllegalArgumentException("$label end time is invalid.")
        require(start < end) { "$label time range is invalid." }
        return SectionMinutes(startMinutes = start, endMinutes = end)
    }

    private fun parseSectionMinutes(time: HebauSectionTime): SectionMinutes? {
        val start = parseMinutes(time.start) ?: return null
        val end = parseMinutes(time.end) ?: return null
        if (start >= end) return null
        return SectionMinutes(startMinutes = start, endMinutes = end)
    }
}

data class SectionMinutes(
    val startMinutes: Int,
    val endMinutes: Int,
)
