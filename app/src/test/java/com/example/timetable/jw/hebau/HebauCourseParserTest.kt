package com.example.timetable.jw.hebau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebauCourseParserTest {
    @Test
    fun parseReadsValidCoursesAndCollectsInvalidRows() {
        val json = """
            {
              "source": "hebau-urp",
              "sourceKind": "fixture",
              "semesterName": "2025-2026-2",
              "semesterStartDate": null,
              "courses": [
                {
                  "name": "Advanced Math",
                  "teacher": "Teacher A",
                  "position": "A-101",
                  "courseClass": "Math-2026-01",
                  "day": 1,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [1, 2, 3]
                },
                {
                  "name": "",
                  "day": 8,
                  "startSection": 2,
                  "endSection": 1,
                  "weeks": []
                },
                {
                  "name": "Physics",
                  "teacher": "Teacher B",
                  "position": "B-201",
                  "day": 5,
                  "startSection": 9,
                  "endSection": 10,
                  "weeks": "1-2,4"
                }
              ],
              "sectionTimes": [
                { "section": 1, "start": "08:00", "end": "08:45" }
              ]
            }
        """.trimIndent()

        val result = HebauCourseParser.parse(json)

        assertEquals(2, result.payload.courses.size)
        assertEquals(1, result.errors.size)
        assertEquals(listOf(1, 2, 3), result.payload.courses[0].weeks)
        assertEquals("Math-2026-01", result.payload.courses[0].courseClass)
        assertEquals(listOf(1, 2, 4), result.payload.courses[1].weeks)
        assertEquals(1, result.payload.sectionTimes.size)
    }

    @Test
    fun parseDeduplicatesIdenticalCourses() {
        val json = """
            {
              "courses": [
                { "name": "Math", "day": 1, "startSection": 1, "endSection": 2, "weeks": [1] },
                { "name": "Math", "day": 1, "startSection": 1, "endSection": 2, "weeks": [1] }
              ]
            }
        """.trimIndent()

        val result = HebauCourseParser.parse(json)

        assertEquals(1, result.payload.courses.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun parseUsesPlainTextFallbackWhenStructuredCoursesAreEmpty() {
        val json = """
            {
              "source": "hebau-urp",
              "sourceKind": "hebau-text",
              "semesterName": "2025-2026-2",
              "courses": [],
              "plainText": "课程名称：线性代数 任课教师：赵六 上课地点：A301 星期二 第7-8节 周次：3-6周",
              "sectionTimes": []
            }
        """.trimIndent()

        val result = HebauCourseParser.parse(json)

        assertEquals(1, result.payload.courses.size)
        val course = result.payload.courses.single()
        assertEquals("线性代数", course.name)
        assertEquals("赵六", course.teacher)
        assertEquals("A301", course.position)
        assertEquals(2, course.day)
        assertEquals(7, course.startSection)
        assertEquals(8, course.endSection)
        assertEquals(listOf(3, 4, 5, 6), course.weeks)
    }

    @Test
    fun parseMergesPlainTextFieldsIntoStructuredCourse() {
        val json = """
            {
              "source": "hebau-urp",
              "sourceKind": "hebau-dom-text",
              "semesterName": "2025-2026-2",
              "courses": [
                { "name": "Math", "day": 1, "startSection": 1, "endSection": 2, "weeks": [1, 2] }
              ],
              "plainText": "课程名称：Math 任课教师：Teacher A 上课地点：A101 星期一 第1-2节 周次：1-2周",
              "sectionTimes": []
            }
        """.trimIndent()

        val result = HebauCourseParser.parse(json)

        assertEquals(1, result.payload.courses.size)
        assertEquals("Teacher A", result.payload.courses.single().teacher)
        assertEquals("A101", result.payload.courses.single().position)
    }
}
