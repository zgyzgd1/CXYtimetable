package com.example.timetable.notify

import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CourseReminderSchedulerTest {
    @Test
    fun buildSchedulePlanCancelsOldCodesWhenNoFutureEntriesRemain() {
        val pastDate = LocalDate.of(2020, 1, 1)
        val oldCodes = setOf(101, 202)
        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(
                TimetableEntry(
                    id = "past-entry",
                    title = "高等数学",
                    date = pastDate.toString(),
                    dayOfWeek = pastDate.dayOfWeek.value,
                    startMinutes = 8 * 60,
                    endMinutes = 9 * 60,
                )
            ),
            reminderMinutes = 20,
            nowMillis = System.currentTimeMillis(),
            oldCodes = oldCodes,
        )

        assertTrue(plan.newSchedules.isEmpty())
        assertEquals(oldCodes, plan.codesToCancel)
    }

    @Test
    fun buildSchedulePlanKeepsOnlyNearestConcurrentEntries() {
        val zone = ZoneId.systemDefault()
        val courseDate = LocalDate.of(2099, 1, 5)
        val nowMillis = courseDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val earliestA = course("entry-a", courseDate, 8 * 60, 9 * 60)
        val earliestB = course("entry-b", courseDate, 8 * 60, 10 * 60)
        val later = course("entry-c", courseDate, 10 * 60, 11 * 60)

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(later, earliestA, earliestB),
            reminderMinutes = 20,
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        assertEquals(setOf("entry-a", "entry-b"), plan.newSchedules.values.map { it.second.id }.toSet())
        assertEquals(1, plan.newSchedules.values.map { it.first }.distinct().size)
        assertTrue(plan.codesToCancel.isEmpty())
    }

    private fun course(id: String, date: LocalDate, startMinutes: Int, endMinutes: Int): TimetableEntry {
        return TimetableEntry(
            id = id,
            title = id,
            date = date.toString(),
            dayOfWeek = date.dayOfWeek.value,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
        )
    }
}
