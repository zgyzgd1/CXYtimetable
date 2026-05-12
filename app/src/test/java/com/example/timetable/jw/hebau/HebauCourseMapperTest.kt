package com.example.timetable.jw.hebau

import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.WeekRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebauCourseMapperTest {
    @Test
    fun mapConvertsCourseToRecurringTimetableEntry() {
        val payload = HebauAcademicImportPayload(
            source = "hebau-urp",
            sourceKind = "fixture",
            semesterName = "2025-2026-2",
            semesterStartDate = null,
            courses = listOf(
                HebauRawCourse(
                    name = "Advanced Math",
                    teacher = "Teacher A",
                    position = "A-101",
                    day = 2,
                    startSection = 1,
                    endSection = 2,
                    weeks = listOf(1, 2, 3, 5),
                    courseClass = "Math-2026-01",
                    remark = "Bring calculator",
                ),
            ),
            sectionTimes = emptyList(),
        )

        val result = HebauCourseMapper.map(payload, LocalDate.of(2026, 3, 2))
        val entry = result.entries.single()

        assertTrue(result.errors.isEmpty())
        assertTrue(entry.id.startsWith("hebau:"))
        assertEquals("Advanced Math", entry.title)
        assertEquals("2026-03-03", entry.date)
        assertEquals(2, entry.dayOfWeek)
        assertEquals(8 * 60, entry.startMinutes)
        assertEquals(9 * 60 + 40, entry.endMinutes)
        assertEquals("A-101", entry.location)
        assertTrue(entry.note.contains("Teacher A"))
        assertTrue(entry.note.contains("Math-2026-01"))
        assertTrue(entry.note.contains("Bring calculator"))
        assertEquals(RecurrenceType.WEEKLY.name, entry.recurrenceType)
        assertEquals("2026-03-02", entry.semesterStartDate)
        assertEquals(WeekRule.CUSTOM.name, entry.weekRule)
        assertEquals("1-3,5", entry.customWeekList)
    }

    @Test
    fun mapUsesPayloadSemesterDateBeforeFallbackDate() {
        val payload = HebauAcademicImportPayload(
            source = "hebau-urp",
            sourceKind = "fixture",
            semesterName = "2025-2026-2",
            semesterStartDate = "2026-09-07",
            courses = listOf(
                HebauRawCourse(
                    name = "Physics",
                    teacher = null,
                    position = null,
                    day = 5,
                    startSection = 9,
                    endSection = 10,
                    weeks = listOf(2),
                ),
            ),
            sectionTimes = emptyList(),
        )

        val result = HebauCourseMapper.map(payload, LocalDate.of(2026, 3, 2))
        val entry = result.entries.single()

        assertEquals("2026-09-18", entry.date)
        assertEquals(18 * 60 + 30, entry.startMinutes)
        assertEquals(20 * 60 + 10, entry.endMinutes)
    }
}
