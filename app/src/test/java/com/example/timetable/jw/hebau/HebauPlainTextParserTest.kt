package com.example.timetable.jw.hebau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebauPlainTextParserTest {
    @Test
    fun parseReadsLabeledSingleLineCourses() {
        val result = HebauPlainTextParser.parse(
            """
            2025-2026学年第二学期课表
            课程名称：高等数学 任课教师：张三 上课地点：一教A101 星期一 第1-2节 周次：1-16周
            课程名称：大学英语 教师：李四 教室：东校区B202 周三 第3-4节 1-8周(单周)
            """.trimIndent(),
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.courses.size)
        assertEquals("高等数学", result.courses[0].name)
        assertEquals("张三", result.courses[0].teacher)
        assertEquals("一教A101", result.courses[0].position)
        assertEquals(1, result.courses[0].day)
        assertEquals(1, result.courses[0].startSection)
        assertEquals(2, result.courses[0].endSection)
        assertEquals((1..16).toList(), result.courses[0].weeks)
        assertEquals(listOf(1, 3, 5, 7), result.courses[1].weeks)
    }

    @Test
    fun parseReadsMultilineCourseBlocksWithoutTreatingTeacherAsCourse() {
        val result = HebauPlainTextParser.parse(
            """
            植物生理学
            王五
            星期四
            第5-6节
            第2-10周
            西校区实验楼305
            """.trimIndent(),
        )

        assertEquals(1, result.courses.size)
        val course = result.courses.single()
        assertEquals("植物生理学", course.name)
        assertEquals("王五", course.teacher)
        assertEquals("西校区实验楼305", course.position)
        assertEquals(4, course.day)
        assertEquals(5, course.startSection)
        assertEquals(6, course.endSection)
        assertEquals((2..10).toList(), course.weeks)
    }
}
