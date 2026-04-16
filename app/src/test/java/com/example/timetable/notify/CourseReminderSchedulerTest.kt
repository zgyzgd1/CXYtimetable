package com.example.timetable.notify

import com.example.timetable.data.TimetableShareCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试课程数据解码逻辑
 * decode 逻辑已迁移至 TimetableRepository（内部使用 TimetableShareCodec），
 * 此处直接测试 TimetableShareCodec.decode 的正确性。
 */
class CourseReminderSchedulerTest {
    @Test
    fun decodeReturnsEntriesWhenPayloadValid() {
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

        val entries = TimetableShareCodec.decode(payload)

        assertEquals(1, entries.size)
        assertEquals("数据结构", entries[0].title)
        assertEquals("A-101", entries[0].location)
    }

    @Test
    fun decodeReturnsEmptyWhenPayloadMalformed() {
        val entries = TimetableShareCodec.decode("not-json")

        assertTrue(entries.isEmpty())
    }

    @Test
    fun decodeReturnsEmptyWhenVersionMismatch() {
        val payload = """
            {
              "version": 999,
              "entries": [
                {
                  "id": "entry-1",
                  "title": "损坏数据",
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

        val entries = TimetableShareCodec.decode(payload)

        assertTrue(entries.isEmpty())
    }
}
