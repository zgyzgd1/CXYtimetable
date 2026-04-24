package com.example.timetable.notify

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CourseReminderReceiverTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun formatReminderLeadTimeSupportsMinutesAndHours() {
        val result20 = formatReminderLeadTime(20, context)
        val result60 = formatReminderLeadTime(60, context)
        val result90 = formatReminderLeadTime(90, context)
        assertTrue(result20.contains("20"))
        assertTrue(result60.contains("1"))
        assertTrue(result90.contains("1"))
        assertTrue(result90.contains("30"))
    }

    @Test
    fun buildReminderNotificationTitleUsesLeadTime() {
        val result = buildReminderNotificationTitle("Linear Algebra", 45, context)
        assertTrue(result.contains("Linear Algebra"))
        assertTrue(result.contains("45"))
    }

    @Test
    fun buildReminderNotificationTextIncludesLocationWhenPresent() {
        assertEquals(
            "2026-04-23 08:00 · A-203",
            buildReminderNotificationText(
                date = "2026-04-23",
                startMinutes = 8 * 60,
                location = "A-203",
            ),
        )
    }

    @Test
    fun buildReminderNotificationTextFallsBackToTimeOnlyWhenDateBlank() {
        assertEquals(
            "08:00",
            buildReminderNotificationText(
                date = "",
                startMinutes = 8 * 60,
                location = "",
            ),
        )
    }
}
