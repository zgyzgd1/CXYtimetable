package com.example.timetable.jw.hebau

import org.junit.Assert.assertEquals
import org.junit.Test

class HebauSectionTimesTest {
    @Test
    fun resolveUsesDefaultTimesWhenNoOverridesProvided() {
        val resolved = HebauSectionTimes.resolve(emptyList())

        assertEquals(8 * 60, resolved.getValue(1).startMinutes)
        assertEquals(8 * 60 + 45, resolved.getValue(1).endMinutes)
        assertEquals(21 * 60 + 15, resolved.getValue(12).startMinutes)
        assertEquals(22 * 60, resolved.getValue(12).endMinutes)
    }

    @Test
    fun resolveAppliesValidOverridesAndIgnoresInvalidOnes() {
        val resolved = HebauSectionTimes.resolve(
            listOf(
                HebauSectionTime(section = 1, start = "08:10", end = "09:00"),
                HebauSectionTime(section = 2, start = "bad", end = "09:40"),
                HebauSectionTime(section = 3, start = "11:00", end = "10:00"),
            ),
        )

        assertEquals(8 * 60 + 10, resolved.getValue(1).startMinutes)
        assertEquals(9 * 60, resolved.getValue(1).endMinutes)
        assertEquals(8 * 60 + 55, resolved.getValue(2).startMinutes)
        assertEquals(10 * 60 + 10, resolved.getValue(3).startMinutes)
    }
}
