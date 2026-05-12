package com.example.timetable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertFalse
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
        val source = sourceFile("src/main/java/com/example/timetable/ui/WeekCalendarStrip.kt").readText()

        assertFalse(source.contains("LocalDate.of(2020, 1, 1)"))
        assertFalse(source.contains("LocalDate.of(2035, 12, 31)"))
    }

    private fun sourceFile(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        return File("app", path)
    }
}
