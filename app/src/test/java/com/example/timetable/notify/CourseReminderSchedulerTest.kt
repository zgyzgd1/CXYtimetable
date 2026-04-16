package com.example.timetable.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CourseReminderSchedulerTest {
    @Test
    fun decodeEntriesForResyncReturnsEntriesWhenPayloadValid() {
        val payload = """
            {
              "version": 1,
              "entries": [
                {
                  "id": "entry-1",
                  "title": "数据结构",
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

        val entries = CourseReminderScheduler.decodeEntriesForResync(payload)

        assertNotNull(entries)
        assertEquals(1, entries?.size)
    }

    @Test
    fun decodeEntriesForResyncReturnsNullWhenPayloadMalformed() {
        val entries = CourseReminderScheduler.decodeEntriesForResync("not-json")

        assertNull(entries)
    }

    @Test
    fun decodeEntriesForResyncReturnsNullWhenEntriesNodeInvalid() {
        val payload = """
            {
              "version": 1,
              "entries": [
                {
                  "id": "entry-1",
                  "title": "损坏数据",
                  "date": "2026-04-16",
                  "dayOfWeek": 4,
                  "startMinutes": -1,
                  "endMinutes": 540,
                  "location": "A-101",
                  "note": ""
                }
              ]
            }
        """.trimIndent()

        val entries = CourseReminderScheduler.decodeEntriesForResync(payload)

        assertNull(entries)
    }
}
