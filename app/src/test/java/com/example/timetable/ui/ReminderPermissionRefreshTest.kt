package com.example.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPermissionRefreshTest {
    @Test
    fun reminderPermissionRefreshIncrementsNotificationTokenAndReadsExactAlarmState() {
        val refreshed = refreshReminderPermissionState(
            currentNotificationPermissionRefreshToken = 4,
            canScheduleExactAlarms = { true },
        )

        assertEquals(5, refreshed.notificationPermissionRefreshToken)
        assertTrue(refreshed.exactAlarmEnabled)
    }

    @Test
    fun reminderPermissionRefreshKeepsLatestExactAlarmDeniedState() {
        val refreshed = refreshReminderPermissionState(
            currentNotificationPermissionRefreshToken = 7,
            canScheduleExactAlarms = { false },
        )

        assertEquals(8, refreshed.notificationPermissionRefreshToken)
        assertFalse(refreshed.exactAlarmEnabled)
    }
}
