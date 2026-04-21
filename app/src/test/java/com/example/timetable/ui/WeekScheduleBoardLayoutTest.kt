package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekScheduleBoardLayoutTest {

    private val slots = listOf(
        WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 40),
        WeekTimeSlot(startMinutes = 8 * 60 + 45, endMinutes = 9 * 60 + 25),
        WeekTimeSlot(startMinutes = 9 * 60 + 30, endMinutes = 10 * 60 + 10),
    )

    @Test
    fun spanningCourseBuildsSingleTallCard() {
        val entry = sampleEntry(
            id = "course-1",
            startMinutes = 8 * 60,
            endMinutes = 9 * 60 + 25,
        )

        val layouts = buildWeekEntryLayouts(
            entries = listOf(entry),
            slots = slots,
            laneWidthPx = 300f,
            slotHeightPx = 120f,
            slotGapPx = 8f,
        )

        assertEquals(1, layouts.size)
        assertEquals(0f, layouts.single().topPx, 0.01f)
        assertTrue(layouts.single().heightPx > 200f)
        assertEquals(300f, layouts.single().widthPx, 0.01f)
    }

    @Test
    fun overlappingCoursesShareTheDayColumn() {
        val first = sampleEntry(
            id = "course-1",
            startMinutes = 8 * 60,
            endMinutes = 9 * 60 + 25,
        )
        val second = sampleEntry(
            id = "course-2",
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60,
        )

        val layouts = buildWeekEntryLayouts(
            entries = listOf(first, second),
            slots = slots,
            laneWidthPx = 300f,
            slotHeightPx = 120f,
            slotGapPx = 8f,
        )

        assertEquals(2, layouts.size)
        assertTrue(layouts.all { it.widthPx < 300f })
        assertTrue(layouts[0].leftPx != layouts[1].leftPx)
    }

    private fun sampleEntry(
        id: String,
        startMinutes: Int,
        endMinutes: Int,
    ): TimetableEntry {
        return TimetableEntry(
            id = id,
            title = "高等数学",
            date = "2026-04-20",
            dayOfWeek = 1,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            location = "A-201",
        )
    }
}
