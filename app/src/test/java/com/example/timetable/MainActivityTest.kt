package com.example.timetable

import android.os.Looper
import com.example.timetable.ui.AppDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test
    fun launchesDefaultScheduleAppWithoutCrashing() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)

        val activity = controller.setup().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
    }

    @Test
    fun resolveLaunchTargetTrimsDateAndMapsDestination() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = " 2026-04-23 ",
            destination = "WEEK",
        )

        assertEquals("2026-04-23", target.selectedDate)
        assertEquals(AppDestination.WEEK, target.destination)
    }

    @Test
    fun resolveLaunchTargetFallsBackWhenValuesAreBlankOrInvalid() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = "   ",
            destination = "UNKNOWN",
        )

        assertNull(target.selectedDate)
        assertEquals(AppDestination.DAY, target.destination)
    }

    @Test
    fun resolveLaunchTargetDropsInvalidDateValues() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = "2026-13-99",
            destination = "DAY",
        )

        assertNull(target.selectedDate)
        assertEquals(AppDestination.DAY, target.destination)
    }
}
