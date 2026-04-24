package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.countConflictPairs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPreviewTest {

    @Test
    fun importPreviewDataClassHoldsAllFields() {
        val entries = listOf(
            TimetableEntry(
                title = "数学",
                date = "2026-09-07",
                dayOfWeek = 1,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
        )
        val preview = ImportPreview(
            validEntries = entries,
            invalidCount = 2,
            conflictCount = 1,
            totalParsed = 3,
        )

        assertEquals(1, preview.validEntries.size)
        assertEquals(2, preview.invalidCount)
        assertEquals(1, preview.conflictCount)
        assertEquals(3, preview.totalParsed)
    }

    @Test
    fun countConflictPairsDetectsOverlappingEntries() {
        val entries = listOf(
            TimetableEntry(
                title = "数学",
                date = "2026-09-07",
                dayOfWeek = 1,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
            TimetableEntry(
                title = "英语",
                date = "2026-09-07",
                dayOfWeek = 1,
                startMinutes = 8 * 60 + 30,
                endMinutes = 9 * 60 + 30,
            ),
        )

        assertEquals(1, countConflictPairs(entries))
    }

    @Test
    fun countConflictPairsReturnsZeroForNonOverlapping() {
        val entries = listOf(
            TimetableEntry(
                title = "数学",
                date = "2026-09-07",
                dayOfWeek = 1,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
            TimetableEntry(
                title = "英语",
                date = "2026-09-07",
                dayOfWeek = 1,
                startMinutes = 10 * 60,
                endMinutes = 11 * 60,
            ),
        )

        assertEquals(0, countConflictPairs(entries))
    }

    @Test
    fun emptyImportPreviewHasZeroCounts() {
        val preview = ImportPreview(
            validEntries = emptyList(),
            invalidCount = 0,
            conflictCount = 0,
            totalParsed = 0,
        )

        assertTrue(preview.validEntries.isEmpty())
        assertEquals(0, preview.conflictCount)
    }
}
