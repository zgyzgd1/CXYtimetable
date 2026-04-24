package com.example.timetable.widget

import android.content.Context
import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class TimetableWidgetUpdaterTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun buildTodayScheduleWidgetStateTruncatesOverflowEntries() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "高数", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
            entry(title = "英语", date = today, startMinutes = 10 * 60, endMinutes = 11 * 60),
            entry(title = "物理", date = today, startMinutes = 13 * 60, endMinutes = 14 * 60),
            entry(title = "化学", date = today, startMinutes = 15 * 60, endMinutes = 16 * 60),
        )

        val state = buildTodayScheduleWidgetState(entries, today, context = context)

        assertTrue(state.summaryText.contains("4"))
        assertEquals(3, state.entryLines.size)
        assertTrue(state.entryLines.first().contains("高数"))
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildTodayScheduleWidgetStateShowsEmptyState() {
        val today = LocalDate.of(2026, 4, 23)

        val state = buildTodayScheduleWidgetState(emptyList(), today, context = context)

        assertTrue(state.entryLines.isNotEmpty())
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildNextCourseWidgetStateUsesUpcomingCourseDate() {
        val today = LocalDate.of(2026, 4, 23)
        val tomorrow = today.plusDays(1)
        val entries = listOf(
            entry(title = "数据库", date = tomorrow, startMinutes = 8 * 60, endMinutes = 9 * 60, location = "B-201"),
            entry(title = "算法", date = tomorrow, startMinutes = 14 * 60, endMinutes = 15 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 18 * 60, context = context)

        assertEquals("数据库", state.title)
        assertEquals(tomorrow, state.targetDate)
    }

    @Test
    fun buildNextCourseWidgetStateShowsOngoingCourseStatus() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "高数", date = today, startMinutes = 9 * 60, endMinutes = 10 * 60, location = "A-101"),
            entry(title = "英语", date = today, startMinutes = 11 * 60, endMinutes = 12 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 9 * 60 + 20, context = context)

        assertEquals("高数", state.title)
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildNextCourseWidgetStateUsesTodayFallbackWhenScheduleExistsButNoUpcomingCourse() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "早课", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
        )

        val state = buildNextCourseWidgetState(entries, today = today, nowMinutes = 18 * 60, context = context)

        assertEquals(today, state.targetDate)
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
