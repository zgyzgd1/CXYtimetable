package com.example.timetable.notify

import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderSchedulerTest {
    @Test
    fun buildSchedulePlanCancelsOldCodesWhenNoFutureEntriesRemain() {
        val pastDate = LocalDate.of(2020, 1, 1)
        val oldCodes = setOf(101, 202)
        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(
                TimetableEntry(
                    id = "past-entry",
                    title = "Past Course",
                    date = pastDate.toString(),
                    dayOfWeek = pastDate.dayOfWeek.value,
                    startMinutes = 8 * 60,
                    endMinutes = 9 * 60,
                ),
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

        assertEquals(setOf("entry-a", "entry-b"), plan.newSchedules.values.map { it.entry.id }.toSet())
        assertEquals(1, plan.newSchedules.values.map { it.triggerAtMillis }.distinct().size)
        assertTrue(plan.codesToCancel.isEmpty())
    }

    @Test
    fun buildSchedulePlanSchedulesNextWeeklyOccurrenceAfterFirstDatePassed() {
        val zone = ZoneId.systemDefault()
        val recurring = course(
            id = "weekly-entry",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val nowMillis = LocalDate.of(2026, 3, 4).atStartOfDay(zone).toInstant().toEpochMilli()

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(recurring),
            reminderMinutes = 20,
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        val scheduled = plan.newSchedules.values.single()
        val expectedDate = LocalDate.of(2026, 3, 9)
        val expectedTrigger = expectedDate.atTime(7, 40).atZone(zone).toInstant().toEpochMilli()

        assertEquals(expectedDate, scheduled.occurrenceDate)
        assertEquals(expectedTrigger, scheduled.triggerAtMillis)
    }

    @Test
    fun buildSchedulePlanReschedulesNextWeekWhenTodaysReminderWindowHasPassed() {
        val zone = ZoneId.systemDefault()
        val recurring = course(
            id = "weekly-entry",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val nowMillis = LocalDate.of(2026, 3, 9).atTime(7, 50).atZone(zone).toInstant().toEpochMilli()

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(recurring),
            reminderMinutes = 20,
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        val scheduled = plan.newSchedules.values.single()
        val expectedDate = LocalDate.of(2026, 3, 16)
        val expectedTrigger = expectedDate.atTime(7, 40).atZone(zone).toInstant().toEpochMilli()

        assertEquals(expectedDate, scheduled.occurrenceDate)
        assertEquals(expectedTrigger, scheduled.triggerAtMillis)
    }

    @Test
    fun buildSchedulePlanUsesEarliestConfiguredLeadTimeAcrossMultipleReminderSlots() {
        val zone = ZoneId.systemDefault()
        val courseDate = LocalDate.of(2099, 1, 5)
        val nowMillis = courseDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(course("entry-a", courseDate, 8 * 60, 9 * 60)),
            reminderMinutesOptions = listOf(10, 30),
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        val scheduled = plan.newSchedules.values.single()
        val expectedTrigger = courseDate.atTime(7, 30).atZone(zone).toInstant().toEpochMilli()

        assertEquals(30, scheduled.reminderMinutes)
        assertEquals(expectedTrigger, scheduled.triggerAtMillis)
    }

    @Test
    fun buildSchedulePlanKeepsLaterSameDayReminderWhenEarlierSlotAlreadyPassed() {
        val zone = ZoneId.systemDefault()
        val courseDate = LocalDate.of(2099, 1, 5)
        val nowMillis = courseDate.atTime(7, 35).atZone(zone).toInstant().toEpochMilli()

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = listOf(course("entry-a", courseDate, 8 * 60, 9 * 60)),
            reminderMinutesOptions = listOf(10, 30),
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        val scheduled = plan.newSchedules.values.single()
        val expectedTrigger = courseDate.atTime(7, 50).atZone(zone).toInstant().toEpochMilli()

        assertEquals(courseDate, scheduled.occurrenceDate)
        assertEquals(10, scheduled.reminderMinutes)
        assertEquals(expectedTrigger, scheduled.triggerAtMillis)
    }

    @Test
    fun buildSchedulePlanHandlesLargeDatasetAndKeepsEarliestReminder() {
        val zone = ZoneId.systemDefault()
        val earliestDate = LocalDate.of(2099, 1, 5)
        val nowMillis = earliestDate.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val bulkEntries = (0 until 1500).map { index ->
            val date = earliestDate.plusDays((index % 12).toLong())
            val startMinutes = 12 * 60 + (index % 4) * 30
            course(
                id = "bulk-$index",
                date = date,
                startMinutes = startMinutes,
                endMinutes = startMinutes + 45,
            )
        }
        val earliest = course(
            id = "earliest-entry",
            date = earliestDate,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )

        val plan = CourseReminderScheduler.buildSchedulePlan(
            entries = bulkEntries + earliest,
            reminderMinutesOptions = listOf(5, 20),
            nowMillis = nowMillis,
            oldCodes = emptySet(),
        )

        val scheduled = plan.newSchedules.values.single()
        val expectedTrigger = earliestDate.atTime(7, 40).atZone(zone).toInstant().toEpochMilli()

        assertEquals(1, plan.newSchedules.size)
        assertEquals("earliest-entry", scheduled.entry.id)
        assertEquals(earliestDate, scheduled.occurrenceDate)
        assertEquals(20, scheduled.reminderMinutes)
        assertEquals(expectedTrigger, scheduled.triggerAtMillis)
    }

    @Test
    fun normalizeReminderMinutesSortsDeduplicatesAndLimitsSelectionCount() {
        assertEquals(
            listOf(5, 10, 20, 30, 45),
            CourseReminderScheduler.normalizeReminderMinutes(listOf(30, 5, 20, 10, 45, 60, 5, 0, 181)),
        )
    }

    @Test
    fun reminderSelectionFormattingSummarizesMultipleSlots() {
        assertEquals(
            "5 分钟、20 分钟、45 分钟",
            CourseReminderScheduler.formatReminderSelection(listOf(45, 20, 5, 20)),
        )
        assertEquals(
            "3档",
            CourseReminderScheduler.formatReminderChipLabel(listOf(45, 20, 5)),
        )
        assertEquals(
            "5m/20m",
            CourseReminderScheduler.formatReminderChipLabel(listOf(20, 5)),
        )
    }

    @Test
    fun isReminderMinutesValidAcceptsCustomRange() {
        assertTrue(CourseReminderScheduler.isReminderMinutesValid(45))
        assertTrue(CourseReminderScheduler.isReminderMinutesValid(180))
        assertFalse(CourseReminderScheduler.isReminderMinutesValid(0))
        assertFalse(CourseReminderScheduler.isReminderMinutesValid(181))
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
