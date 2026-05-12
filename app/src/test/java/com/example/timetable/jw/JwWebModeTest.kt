package com.example.timetable.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwWebModeTest {
    @Test
    fun fromSavedNameFallsBackToDesktopMode() {
        assertEquals(JwWebMode.DESKTOP, JwWebMode.fromSavedName(null))
        assertEquals(JwWebMode.DESKTOP, JwWebMode.fromSavedName(""))
        assertEquals(JwWebMode.DESKTOP, JwWebMode.fromSavedName("UNKNOWN"))
    }

    @Test
    fun userAgentUsesDesktopOverrideForDesktopMode() {
        val userAgent = JwUserAgent.forMode(JwWebMode.DESKTOP, defaultUserAgent = "Android WebView")

        assertTrue(userAgent.contains("Windows NT 10.0"))
        assertTrue(userAgent.contains("Chrome"))
    }

    @Test
    fun userAgentKeepsDefaultForMobileMode() {
        val defaultUserAgent = "Mozilla/5.0 Linux Android WebView"

        assertEquals(defaultUserAgent, JwUserAgent.forMode(JwWebMode.MOBILE, defaultUserAgent))
    }
}
