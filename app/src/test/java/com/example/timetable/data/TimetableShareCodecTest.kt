package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableShareCodecTest {
    @Test
    fun decodeSkipsInvalidEntriesButKeepsValidOnes() {
        val payload = """
            {
              "version": 1,
              "entries": [
                {
                  "id": "bad-1",
                  "title": "坏数据",
                  "date": "2026-04-15",
                  "dayOfWeek": 3,
                  "startMinutes": -1,
                  "endMinutes": 60,
                  "location": "",
                  "note": ""
                },
                {
                  "id": "good-1",
                  "title": "高等数学",
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

        val decoded = TimetableShareCodec.decode(payload)

        assertEquals(1, decoded.size)
        assertEquals("高等数学", decoded.first().title)
    }

    @Test
    fun decodeReturnsEmptyForMalformedJson() {
        val decoded = TimetableShareCodec.decode("not-json")

        assertTrue(decoded.isEmpty())
    }
}
