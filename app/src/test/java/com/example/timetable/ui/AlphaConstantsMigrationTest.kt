package com.example.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaConstantsMigrationTest {

    @Test
    fun semanticAlphaConstantsStayInExpectedOrder() {
        assertTrue(OverlayAlpha.heavy > OverlayAlpha.medium)
        assertTrue(OverlayAlpha.medium > OverlayAlpha.light)
        assertTrue(OverlayAlpha.light > OverlayAlpha.subtle)
        assertEquals(0.25f, SurfaceAlpha.weekBoard, 0f)
        assertEquals(0.14f, AccentAlpha.medium, 0f)
    }
}
