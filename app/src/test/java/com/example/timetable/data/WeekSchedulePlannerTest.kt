package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekSchedulePlannerTest {

    @Test
    fun buildWeekTimeSlotsFromFixedScheduleGeneratesUniformSlots() {
        val slots = buildWeekTimeSlotsFromFixedSchedule(
            FixedWeekScheduleConfig(
                firstStartMinutes = 8 * 60,
                lessonDurationMinutes = 45,
                breakDurationMinutes = 10,
                slotCount = 3,
            ),
        )

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 45),
                WeekTimeSlot(startMinutes = 8 * 60 + 55, endMinutes = 9 * 60 + 40),
                WeekTimeSlot(startMinutes = 9 * 60 + 50, endMinutes = 10 * 60 + 35),
            ),
            slots,
        )
    }

    @Test
    fun syncWeekTimeSlotsWithEntryChangeShiftsFollowingSlots() {
        val slots = listOf(
            WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 40),
            WeekTimeSlot(startMinutes = 8 * 60 + 45, endMinutes = 9 * 60 + 25),
            WeekTimeSlot(startMinutes = 9 * 60 + 30, endMinutes = 10 * 60 + 10),
        )
        val originalEntry = TimetableEntry(
            id = "course-1",
            title = "Math",
            date = "2026-04-22",
            dayOfWeek = 3,
            startMinutes = 8 * 60,
            endMinutes = 8 * 60 + 40,
        )
        val updatedEntry = originalEntry.copy(
            startMinutes = 8 * 60 + 10,
            endMinutes = 8 * 60 + 50,
        )

        val synced = syncWeekTimeSlotsWithEntryChange(
            currentSlots = slots,
            originalEntry = originalEntry,
            updatedEntry = updatedEntry,
        )

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 8 * 60 + 10, endMinutes = 8 * 60 + 50),
                WeekTimeSlot(startMinutes = 8 * 60 + 55, endMinutes = 9 * 60 + 35),
                WeekTimeSlot(startMinutes = 9 * 60 + 40, endMinutes = 10 * 60 + 20),
            ),
            synced,
        )
    }

    @Test
    fun syncWeekTimeSlotsWithEntryChangeRescalesMultiSlotRange() {
        val slots = listOf(
            WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 40),
            WeekTimeSlot(startMinutes = 8 * 60 + 45, endMinutes = 9 * 60 + 25),
            WeekTimeSlot(startMinutes = 9 * 60 + 30, endMinutes = 10 * 60 + 10),
        )
        val originalEntry = TimetableEntry(
            id = "course-2",
            title = "Physics",
            date = "2026-04-22",
            dayOfWeek = 3,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60 + 25,
        )
        val updatedEntry = originalEntry.copy(
            startMinutes = 8 * 60 + 10,
            endMinutes = 9 * 60 + 50,
        )

        val synced = syncWeekTimeSlotsWithEntryChange(
            currentSlots = slots,
            originalEntry = originalEntry,
            updatedEntry = updatedEntry,
        )

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 8 * 60 + 10, endMinutes = 8 * 60 + 57),
                WeekTimeSlot(startMinutes = 9 * 60 + 3, endMinutes = 9 * 60 + 50),
                WeekTimeSlot(startMinutes = 9 * 60 + 55, endMinutes = 10 * 60 + 35),
            ),
            synced,
        )
    }

    @Test
    fun inferFixedWeekScheduleConfigUsesFirstSlotPattern() {
        val config = inferFixedWeekScheduleConfig(
            listOf(
                WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 45),
                WeekTimeSlot(startMinutes = 8 * 60 + 55, endMinutes = 9 * 60 + 40),
                WeekTimeSlot(startMinutes = 9 * 60 + 50, endMinutes = 10 * 60 + 35),
            ),
        )

        assertEquals(
            FixedWeekScheduleConfig(
                firstStartMinutes = 8 * 60,
                lessonDurationMinutes = 45,
                breakDurationMinutes = 10,
                slotCount = 3,
            ),
            config,
        )
    }
}
