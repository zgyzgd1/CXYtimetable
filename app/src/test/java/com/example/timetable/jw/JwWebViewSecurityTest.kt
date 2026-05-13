package com.example.timetable.jw

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class JwWebViewSecurityTest {
    @Test
    fun jwWebViewSettingsPolicyDisallowsMixedContent() {
        assertEquals(
            WebSettings.MIXED_CONTENT_NEVER_ALLOW,
            jwWebSettingsPolicy(JwWebMode.DESKTOP).mixedContentMode,
        )
    }

    @Test
    fun jwWebViewSettingsPolicyKeepsOverviewEnabledForMobileAndDesktop() {
        val desktop = jwWebSettingsPolicy(JwWebMode.DESKTOP)
        val mobile = jwWebSettingsPolicy(JwWebMode.MOBILE)

        assertEquals(true, desktop.useWideViewPort)
        assertEquals(true, desktop.loadWithOverviewMode)
        assertEquals(true, mobile.useWideViewPort)
        assertEquals(true, mobile.loadWithOverviewMode)
    }
}
