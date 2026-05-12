package com.example.timetable.jw.hebau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebauStableIdTest {
    private val payload = HebauAcademicImportPayload(
        source = "hebau-urp",
        sourceKind = "fixture",
        semesterName = "2025-2026-2",
        semesterStartDate = null,
        courses = emptyList(),
        sectionTimes = emptyList(),
    )

    @Test
    fun stableIdNormalizesWhitespaceAndWeekOrder() {
        val first = course(name = " Advanced   Math ", weeks = listOf(3, 1, 2))
        val second = course(name = "advanced math", weeks = listOf(1, 2, 3))

        assertEquals(HebauStableId.forCourse(payload, first), HebauStableId.forCourse(payload, second))
    }

    @Test
    fun stableIdChangesWhenCourseIdentityChanges() {
        val first = course(teacher = "Teacher A")
        val second = course(teacher = "Teacher B")

        assertNotEquals(HebauStableId.forCourse(payload, first), HebauStableId.forCourse(payload, second))
        assertTrue(HebauStableId.forCourse(payload, first).startsWith("hebau:"))
    }

    private fun course(
        name: String = "Advanced Math",
        teacher: String = "Teacher A",
        weeks: List<Int> = listOf(1, 2, 3),
    ) = HebauRawCourse(
        name = name,
        teacher = teacher,
        position = "A-101",
        day = 1,
        startSection = 1,
        endSection = 2,
        weeks = weeks,
    )
}
