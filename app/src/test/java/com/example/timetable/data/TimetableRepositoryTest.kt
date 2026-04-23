package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableRepositoryTest {
    @Test
    fun shouldSeedSampleEntriesOnlyOnFirstEmptyLaunch() {
        assertTrue(TimetableRepository.shouldSeedSampleEntries(hasEntries = false, hasSeededSampleEntries = false))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = true, hasSeededSampleEntries = false))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = false, hasSeededSampleEntries = true))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = true, hasSeededSampleEntries = true))
    }

    @Test
    fun decodeLegacyEntriesSkipsInvalidEntriesButKeepsValidOnes() {
        val payload = """
            {
              "version": 1,
              "entries": [
                {
                  "id": "bad-1",
                  "title": "bad",
                  "date": "2026-04-15",
                  "dayOfWeek": 3,
                  "startMinutes": -1,
                  "endMinutes": 60,
                  "location": "",
                  "note": ""
                },
                {
                  "id": "good-1",
                  "title": "Mathematics",
                  "date": "2026-04-15",
                  "dayOfWeek": 3,
                  "startMinutes": 480,
                  "endMinutes": 540,
                  "location": "A-201",
                  "note": ""
                }
              ]
            }
        """.trimIndent()

        val decoded = TimetableRepository.decodeLegacyEntries(payload)

        assertEquals(1, decoded.size)
        assertEquals("Mathematics", decoded.first().title)
    }

    @Test
    fun decodeLegacyEntriesReturnsEmptyForVersionMismatch() {
        val payload = """
            {
              "version": 999,
              "entries": [
                {
                  "id": "entry-1",
                  "title": "History",
                  "date": "2026-04-16",
                  "dayOfWeek": 4,
                  "startMinutes": 480,
                  "endMinutes": 540,
                  "location": "A-101",
                  "note": ""
                }
              ]
            }
        """.trimIndent()

        val decoded = TimetableRepository.decodeLegacyEntries(payload)

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun shouldNotSeedSampleEntriesWhenLegacyPayloadExistsButDecodingFailed() {
        val legacyLoadResult = TimetableRepository.LegacyLoadResult(
            file = java.io.File("legacy.json"),
            payload = """{"version":999,"entries":[{"title":"broken"}]}""",
            entries = emptyList(),
        )

        val shouldSeed = TimetableRepository.shouldSeedSampleEntriesAfterLegacyLoad(
            legacyLoadResult = legacyLoadResult,
            hasEntries = false,
            hasSeededSampleEntries = false,
        )

        assertFalse(shouldSeed)
    }

    @Test
    fun shouldSeedSampleEntriesWhenLegacyPayloadIsBlankAndDatabaseIsEmpty() {
        val legacyLoadResult = TimetableRepository.LegacyLoadResult(
            file = java.io.File("legacy.json"),
            payload = "",
            entries = emptyList(),
        )

        val shouldSeed = TimetableRepository.shouldSeedSampleEntriesAfterLegacyLoad(
            legacyLoadResult = legacyLoadResult,
            hasEntries = false,
            hasSeededSampleEntries = false,
        )

        assertTrue(shouldSeed)
    }
}
