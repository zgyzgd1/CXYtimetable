package com.example.timetable.widget

import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableWidgetUpdaterTest {
    @Test
    fun buildTodayScheduleWidgetStateTruncatesOverflowEntries() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "高数", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
            entry(title = "英语", date = today, startMinutes = 10 * 60, endMinutes = 11 * 60),
            entry(title = "物理", date = today, startMinutes = 13 * 60, endMinutes = 14 * 60),
            entry(title = "化学", date = today, startMinutes = 15 * 60, endMinutes = 16 * 60),
        )

        val state = buildTodayScheduleWidgetState(entries, today)

        assertEquals("今天共 4 节", state.summaryText)
        assertEquals(3, state.entryLines.size)
        assertTrue(state.entryLines.first().contains("高数"))
        assertEquals("还有 1 节未显示", state.overflowText)
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildTodayScheduleWidgetStateShowsEmptyState() {
        val today = LocalDate.of(2026, 4, 23)

        val state = buildTodayScheduleWidgetState(emptyList(), today)

        assertEquals("今天暂无课程", state.summaryText)
        assertEquals(listOf("点按打开课表并添加课程"), state.entryLines)
        assertEquals("", state.overflowText)
    }

    @Test
    fun buildNextCourseWidgetStateUsesUpcomingCourseDate() {
        val today = LocalDate.of(2026, 4, 23)
        val tomorrow = today.plusDays(1)
        val entries = listOf(
            entry(title = "数据库", date = tomorrow, startMinutes = 8 * 60, endMinutes = 9 * 60, location = "B-201"),
            entry(title = "算法", date = tomorrow, startMinutes = 14 * 60, endMinutes = 15 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 18 * 60)

        assertEquals("数据库", state.title)
        assertEquals("B-201", state.locationText)
        assertTrue(state.timeLabel.startsWith("明天 08:00 - 09:00"))
        assertEquals(tomorrow, state.targetDate)
    }

    @Test
    fun buildNextCourseWidgetStateShowsOngoingCourseStatus() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "高数", date = today, startMinutes = 9 * 60, endMinutes = 10 * 60, location = "A-101"),
            entry(title = "英语", date = today, startMinutes = 11 * 60, endMinutes = 12 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 9 * 60 + 20)

        assertEquals("高数", state.title)
        assertTrue(state.statusText.contains("正在进行"))
        assertTrue(state.timeLabel.startsWith("今天 09:00 - 10:00"))
        assertEquals("A-101", state.locationText)
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildNextCourseWidgetStateUsesTodayFallbackWhenScheduleExistsButNoUpcomingCourse() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "早课", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 18 * 60)

        assertEquals("下一节课", state.statusText)
        assertEquals("当前没有待上课程", state.title)
        assertEquals(today, state.targetDate)
        assertEquals("点按打开完整课表", state.locationText)
    }

    private fun entry(
        title: String,
        date: LocalDate,
        startMinutes: Int,
        endMinutes: Int,
        location: String = "",
    ): TimetableEntry {
        return TimetableEntry(
            title = title,
            date = date.toString(),
            dayOfWeek = date.dayOfWeek.value,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            location = location,
        )
    }
}
