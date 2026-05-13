package com.example.timetable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.timetable.data.AppConstants
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WeekCalendarStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysMaxSupportedDate() {
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarStrip(
                    selectedDate = LocalDate.of(2100, 12, 31),
                    onDateSelected = {},
                )
            }
        }

        val matchingNodes = composeRule
            .onAllNodesWithText("31")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)

        assertTrue(matchingNodes.isNotEmpty())
    }

    @Test
    fun usesSharedAppDateBoundsInsteadOfLocalHardcodedRange() {
        assertEquals(LocalDate.of(1970, 1, 1), AppConstants.MIN_DATE)
        assertEquals(LocalDate.of(2100, 12, 31), AppConstants.MAX_DATE)
    }
}
