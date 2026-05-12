package com.example.timetable.ui

import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekRule
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.findNextCourseSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleScreenTest {

    @Test
    fun nextWeekTimeSlotReturnsNullWhenNoFullSlotFits() {
        val previous = WeekTimeSlot(
            startMinutes = 23 * 60 - 15,
            endMinutes = 23 * 60 + 35,
        )

        val next = nextWeekTimeSlot(previous)

        assertNull(next)
    }

    @Test
    fun resizeWeekTimeSlotsStopsExpandingWhenTimeRunsOut() {
        val slots = listOf(
            WeekTimeSlot(
                startMinutes = 22 * 60 + 40,
                endMinutes = 23 * 60 + 20,
            ),
        )

        val resized = resizeWeekTimeSlots(slots, targetCount = 3)

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 22 * 60 + 40, endMinutes = 23 * 60 + 20),
            ),
            resized,
        )
    }

    @Test
    fun findNextCourseSnapshotPrefersOngoingCourse() {
        val today = LocalDate.of(2026, 4, 22)
        val ongoing = timetableEntry(
            title = "Linear Algebra",
            date = today,
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
        )
        val later = timetableEntry(
            title = "Networks",
            date = today,
            startMinutes = 11 * 60,
            endMinutes = 12 * 60,
        )

        val snapshot = findNextCourseSnapshot(
            entries = listOf(ongoing, later),
            nowDate = today,
            nowMinutes = 9 * 60 + 20,
        )

        assertEquals("Linear Algebra", snapshot?.entry?.title)
        assertEquals(today, snapshot?.occurrenceDate)
        assertTrue(snapshot?.statusText?.contains("正在进行") == true)
    }

    @Test
    fun findNextCourseSnapshotFallsBackToFutureDate() {
        val today = LocalDate.of(2026, 4, 22)
        val tomorrow = today.plusDays(1)
        val endedToday = timetableEntry(
            title = "Calculus",
            date = today,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )
        val tomorrowMorning = timetableEntry(
            title = "English",
            date = tomorrow,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )

        val snapshot = findNextCourseSnapshot(
            entries = listOf(endedToday, tomorrowMorning),
            nowDate = today,
            nowMinutes = 13 * 60,
        )

        assertEquals("English", snapshot?.entry?.title)
        assertEquals(tomorrow, snapshot?.occurrenceDate)
        assertTrue(snapshot?.statusText?.startsWith("明天") == true)
    }

    @Test
    fun findNextCourseSnapshotTracksOccurrenceDateForRecurringEntry() {
        val today = LocalDate.of(2026, 4, 22)
        val nextMonday = LocalDate.of(2026, 4, 27)
        val recurring = timetableEntry(
            title = "Data Structure",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )

        val snapshot = findNextCourseSnapshot(
            entries = listOf(recurring),
            nowDate = today,
            nowMinutes = 18 * 60,
        )

        assertEquals(nextMonday, snapshot?.occurrenceDate)
    }

    @Test
    fun findNextCourseSnapshotSupportsFarCustomOccurrenceBeyondOneYear() {
        val today = LocalDate.of(2026, 4, 22)
        val farFutureMonday = LocalDate.of(2026, 3, 2).plusWeeks(79)
        val recurring = timetableEntry(
            title = "Compiler",
            date = farFutureMonday,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "80",
        )

        val snapshot = findNextCourseSnapshot(
            entries = listOf(recurring),
            nowDate = today,
            nowMinutes = 18 * 60,
        )

        assertEquals(farFutureMonday, snapshot?.occurrenceDate)
    }

    @Test
    fun findNextCourseSnapshotReturnsNullWhenNoUpcomingCourse() {
        val today = LocalDate.of(2026, 4, 22)

        val snapshot = findNextCourseSnapshot(
            entries = emptyList(),
            nowDate = today,
            nowMinutes = 10 * 60,
        )

        assertNull(snapshot)
    }

    @Test
    fun createQuickEntryTemplateUsesDefaultWhenDayIsEmpty() {
        val date = LocalDate.of(2026, 4, 22)

        val template = createQuickEntryTemplate(date, existingEntries = emptyList(), groupId = "group-a")

        assertEquals(date.toString(), template.date)
        assertEquals("group-a", template.groupId)
        assertEquals(8 * 60, template.startMinutes)
        assertEquals(9 * 60, template.endMinutes)
    }

    @Test
    fun createQuickEntryTemplateFollowsLastCourseEndTime() {
        val date = LocalDate.of(2026, 4, 22)
        val existing = listOf(
            timetableEntry(
                title = "Calculus",
                date = date,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
            timetableEntry(
                title = "English",
                date = date,
                startMinutes = 10 * 60,
                endMinutes = 11 * 60,
            ),
        )

        val template = createQuickEntryTemplate(date, existing)

        assertEquals(11 * 60 + 5, template.startMinutes)
        assertEquals(12 * 60 + 5, template.endMinutes)
    }

    @Test
    fun duplicateEntryTemplateKeepsFieldsButGeneratesNewId() {
        val source = timetableEntry(
            title = "Database",
            date = LocalDate.of(2026, 4, 22),
            startMinutes = 13 * 60,
            endMinutes = 14 * 60,
        ).copy(
            location = "A-203",
            note = "Bring report",
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
            customWeekList = "1,3,5",
            skipWeekList = "3",
        )

        val duplicated = duplicateEntryTemplate(source)

        assertEquals(source.title, duplicated.title)
        assertEquals(source.date, duplicated.date)
        assertEquals(source.dayOfWeek, duplicated.dayOfWeek)
        assertEquals(source.startMinutes, duplicated.startMinutes)
        assertEquals(source.endMinutes, duplicated.endMinutes)
        assertEquals(source.location, duplicated.location)
        assertEquals(source.note, duplicated.note)
        assertEquals(source.recurrenceType, duplicated.recurrenceType)
        assertEquals(source.semesterStartDate, duplicated.semesterStartDate)
        assertEquals(source.weekRule, duplicated.weekRule)
        assertEquals(source.customWeekList, duplicated.customWeekList)
        assertEquals(source.skipWeekList, duplicated.skipWeekList)
        assertEquals(source.groupId, duplicated.groupId)
        assertTrue(source.id != duplicated.id)
    }

    @Test
    fun backgroundModeSelectionRequestsImageWhenCustomImageIsMissing() {
        assertEquals(
            BackgroundModeSelection.REQUEST_CUSTOM_IMAGE,
            resolveBackgroundModeSelection(
                mode = AppBackgroundMode.CUSTOM_IMAGE,
                hasCustomBackground = false,
            ),
        )
    }

    @Test
    fun backgroundModeSelectionAppliesCustomModeWhenImageExists() {
        assertEquals(
            BackgroundModeSelection.APPLY_MODE,
            resolveBackgroundModeSelection(
                mode = AppBackgroundMode.CUSTOM_IMAGE,
                hasCustomBackground = true,
            ),
        )
    }

    private fun timetableEntry(
        title: String,
        date: LocalDate,
        startMinutes: Int,
        endMinutes: Int,
    ): TimetableEntry {
        return TimetableEntry(
            title = title,
            date = date.toString(),
            dayOfWeek = date.dayOfWeek.value,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
        )
    }
}
