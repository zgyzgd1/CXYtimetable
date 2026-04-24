package com.example.timetable.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.timetable.data.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], instrumentedPackages = ["androidx.loader.content"])
class EntryEditorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveWithBlankTitleShowsError() {
        val entry = TimetableEntry(
            title = "",
            date = "2026-09-01",
            dayOfWeek = 2,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60
        )
        
        composeTestRule.setContent {
            EntryEditorDialog(
                initial = entry,
                onDismiss = {},
                onSave = {}
            )
        }

        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.onNodeWithText("请输入课程名称").assertExists()
    }

    @Test
    fun saveWithEndTimeBeforeStartTimeShowsError() {
        val entry = TimetableEntry(
            title = "Math",
            date = "2026-09-01",
            dayOfWeek = 2,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60
        )

        composeTestRule.setContent {
            EntryEditorDialog(
                initial = entry,
                onDismiss = {},
                onSave = {}
            )
        }

        // Change start time to be after end time
        composeTestRule.onNodeWithText("08:00").performTextClearance()
        composeTestRule.onNodeWithText("开始时间").performTextInput("10:00")
        
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.onNodeWithText("结束时间需要晚于开始时间").assertExists()
    }

    @Test
    fun saveWithValidInputCallsOnSaveWithCorrectData() {
        val entry = TimetableEntry(
            title = "Math",
            date = "2026-09-01",
            dayOfWeek = 2,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60
        )
        
        var savedEntry: TimetableEntry? = null

        composeTestRule.setContent {
            EntryEditorDialog(
                initial = entry,
                onDismiss = {},
                onSave = { savedEntry = it }
            )
        }

        composeTestRule.onNodeWithText("保存").performClick()
        assertNotNull(savedEntry)
        assertEquals("Math", savedEntry?.title)
        assertEquals("2026-09-01", savedEntry?.date)
        assertEquals(8 * 60, savedEntry?.startMinutes)
        assertEquals(9 * 60, savedEntry?.endMinutes)
    }
}
