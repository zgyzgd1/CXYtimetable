package com.example.timetable.widget

import android.content.Context
import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 8 * 60, context = context)

        assertEquals(MAX_VISIBLE_COURSES, state.courseItems.size)
        assertTrue(state.dateLabel.contains("4/23"))
        assertEquals("高数", state.courseItems[0].title)
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildTodayScheduleWidgetStateShowsEmptyState() {
        val today = LocalDate.of(2026, 4, 23)

        val state = buildTodayScheduleWidgetState(emptyList(), today, nowMinutes = 12 * 60, context = context)

        assertTrue(state.courseItems.isEmpty())
        org.junit.Assert.assertNotNull(state.emptyText)
        assertEquals(today, state.targetDate)
    }

    @Test
    fun buildTodayScheduleWidgetStateClearsEmptyTextWhenCoursesExist() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "高数", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
        )

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 8 * 60, context = context)

        assertEquals(null, state.emptyText)
    }

    @Test
    fun isPastTrueWhenCourseEnded() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "早课", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
        )

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 10 * 60, context = context)

        assertTrue(state.courseItems[0].isPast)
    }

    @Test
    fun isPastFalseWhenCourseNotYetStarted() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "晚课", date = today, startMinutes = 18 * 60, endMinutes = 19 * 60),
        )

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 8 * 60, context = context)

        assertFalse(state.courseItems[0].isPast)
    }

    @Test
    fun isPastFalseWhenCourseOngoing() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "进行中", date = today, startMinutes = 9 * 60, endMinutes = 10 * 60),
        )

        // nowMinutes is 9:30, which is within startMinutes..endMinutes
        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 9 * 60 + 30, context = context)

        assertFalse(state.courseItems[0].isPast)
    }

    @Test
    fun isPastMixedStateInCourseList() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = listOf(
            entry(title = "早课", date = today, startMinutes = 8 * 60, endMinutes = 9 * 60),
            entry(title = "中课", date = today, startMinutes = 10 * 60, endMinutes = 11 * 60),
            entry(title = "晚课", date = today, startMinutes = 14 * 60, endMinutes = 15 * 60),
        )

        // nowMinutes is 10:30: first course ended, second is ongoing, third not started
        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 10 * 60 + 30, context = context)

        assertTrue(state.courseItems[0].isPast)   // 8:00-9:00 ended
        assertFalse(state.courseItems[1].isPast)   // 10:00-11:00 ongoing
        assertFalse(state.courseItems[2].isPast)   // 14:00-15:00 not started
    }

    @Test
    fun buildTodayScheduleWidgetStateTruncatesToMaxVisibleCourses() {
        val today = LocalDate.of(2026, 4, 23)
        val entries = (1..6).map { i ->
            entry(title = "课$i", date = today, startMinutes = (8 + i) * 60, endMinutes = (9 + i) * 60)
        }

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes = 8 * 60, context = context)

        assertEquals(MAX_VISIBLE_COURSES, state.courseItems.size)
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
