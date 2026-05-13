package com.example.timetable.notify

import android.content.Context
import android.content.Intent
import com.example.timetable.util.OneTimeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun oneTimeActionRunsOnlyOnce() {
        val oneTimeAction = OneTimeAction()
        var invokeCount = 0

        repeat(3) {
            oneTimeAction.run {
                invokeCount++
            }
        }

        assertEquals(1, invokeCount)
    }

    @Test
    fun formatReminderLeadTimeSupportsMinutesAndHours() {
        assertEquals(context.getString(com.example.timetable.R.string.notify_minutes_later, 20), formatReminderLeadTime(20, context))
        assertEquals(context.getString(com.example.timetable.R.string.notify_hours_later, 1), formatReminderLeadTime(60, context))
        assertEquals(context.getString(com.example.timetable.R.string.notify_hours_minutes_later, 1, 30), formatReminderLeadTime(90, context))
    }

    @Test
    fun buildReminderNotificationTitleUsesLeadTime() {
        val result = buildReminderNotificationTitle("Linear Algebra", 45, context)
        assertEquals(
            "${context.getString(com.example.timetable.R.string.notify_minutes_later, 45)}\uFF1ALinear Algebra",
            result,
        )
    }

    @Test
    fun reminderRequestCodeFromReturnsNullWhenMissing() {
        assertNull(reminderRequestCodeFrom(Intent()))
    }

    @Test
    fun reminderRequestCodeFromReadsExplicitValue() {
        val intent = Intent().putExtra(CourseReminderScheduler.EXTRA_REQUEST_CODE, 42)

        assertEquals(42, reminderRequestCodeFrom(intent))
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
