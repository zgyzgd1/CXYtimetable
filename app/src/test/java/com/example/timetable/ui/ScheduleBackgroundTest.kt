package com.example.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleBackgroundTest {
    @Test
    fun calculateInSampleSizeReturnsOneForSmallImages() {
        assertEquals(
            1,
            calculateInSampleSize(
                sourceWidth = 900,
                sourceHeight = 700,
                targetWidth = 1080,
                targetHeight = 1920,
            ),
        )
    }

    @Test
    fun calculateInSampleSizeDownsamplesLargeImagesByPowersOfTwo() {
        assertEquals(
            4,
            calculateInSampleSize(
                sourceWidth = 4000,
                sourceHeight = 3000,
                targetWidth = 1000,
                targetHeight = 750,
            ),
        )
    }
}
