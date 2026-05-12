package com.example.timetable.jw.hebau

import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekRule
import com.example.timetable.data.parseEntryDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object HebauCourseMapper {
    fun map(
        payload: HebauAcademicImportPayload,
        fallbackSemesterStartDate: LocalDate,
    ): HebauMappingResult {
        val semesterStartDate = payload.semesterStartDate
            ?.let(::parseEntryDate)
            ?: fallbackSemesterStartDate
        val firstWeekMonday = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sectionMinutes = HebauSectionTimes.resolve(payload.sectionTimes)
        val errors = mutableListOf<String>()
        val entries = payload.courses.mapIndexedNotNull { index, course ->
            runCatching {
                mapCourse(
                    payload = payload,
                    course = course,
                    firstWeekMonday = firstWeekMonday,
                    sectionMinutes = sectionMinutes,
                )
            }.onFailure {
                errors += "Course ${index + 1} ${course.name}: ${it.message ?: "mapping failed"}"
            }.getOrNull()
        }
        return HebauMappingResult(entries = entries, errors = errors)
    }

    private fun mapCourse(
        payload: HebauAcademicImportPayload,
        course: HebauRawCourse,
        firstWeekMonday: LocalDate,
        sectionMinutes: Map<Int, SectionMinutes>,
    ): TimetableEntry {
        require(course.day in 1..7) { "day must be in 1..7." }
        val start = sectionMinutes[course.startSection]
            ?: throw IllegalArgumentException("unknown start section ${course.startSection}.")
        val end = sectionMinutes[course.endSection]
            ?: throw IllegalArgumentException("unknown end section ${course.endSection}.")
        require(start.startMinutes < end.endMinutes) { "section time range is invalid." }

        val normalizedWeeks = course.weeks.filter { it > 0 }.distinct().sorted()
        require(normalizedWeeks.isNotEmpty()) { "weeks is empty." }

        val firstCourseDate = firstWeekMonday
            .plusWeeks((normalizedWeeks.first() - 1).toLong())
            .plusDays((course.day - 1).toLong())
        val note = buildNote(course)

        return TimetableEntry.create(
            id = HebauStableId.forCourse(payload, course.copy(weeks = normalizedWeeks)),
            title = course.name.trim(),
            date = firstCourseDate.toString(),
            dayOfWeek = course.day,
            startMinutes = start.startMinutes,
            endMinutes = end.endMinutes,
            location = course.position.orEmpty().trim(),
            note = note,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = firstWeekMonday.toString(),
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = HebauWeekFormatter.format(normalizedWeeks),
            skipWeekList = "",
        )
    }

    private fun buildNote(course: HebauRawCourse): String {
        return listOfNotNull(
            course.teacher?.trim()?.takeIf { it.isNotEmpty() }?.let { "教师: $it" },
            course.courseClass?.trim()?.takeIf { it.isNotEmpty() }?.let { "教学班: $it" },
            course.remark?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString("\n")
    }
}
