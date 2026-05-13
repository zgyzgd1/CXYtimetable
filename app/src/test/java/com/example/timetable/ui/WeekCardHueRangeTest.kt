package com.example.timetable.ui

import com.example.timetable.data.MAX_WEEK_CARD_HUE
import com.example.timetable.data.MIN_WEEK_CARD_HUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCardHueRangeTest {
    @Test
    fun weekCardHueRangeIsSymmetricAroundZero() {
        assertEquals(-180f, MIN_WEEK_CARD_HUE, 0f)
        assertEquals(180f, MAX_WEEK_CARD_HUE, 0f)
        assertTrue(MIN_WEEK_CARD_HUE < 0f)
        assertTrue(MAX_WEEK_CARD_HUE > 0f)
    }
}
