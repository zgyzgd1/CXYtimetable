package com.example.timetable.jw.hebau

import org.junit.Assert.assertEquals
import org.junit.Test

class HebauWeekFormatterTest {
    @Test
    fun formatCompactsConsecutiveWeeks() {
        assertEquals("1-4,6,8-10", HebauWeekFormatter.format(listOf(4, 1, 2, 3, 6, 8, 9, 10)))
    }

    @Test
    fun formatDropsDuplicateAndInvalidWeeks() {
        assertEquals("1,3", HebauWeekFormatter.format(listOf(0, 1, 1, -2, 3)))
    }
}
