package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarTest {
    @Test(timeout = 1000)
    fun parseWeeklyRRuleRespectsCount() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-1
            SUMMARY:离散数学
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            RRULE:FREQ=WEEKLY;COUNT=1;BYDAY=MO,WE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals("2026-04-13", parsed.first().date)
    }

    @Test
    fun writeAddsDtStampField() {
        val entry = TimetableEntry(
            id = "entry-1",
            title = "线性代数",
            date = "2026-04-15",
            dayOfWeek = 3,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("DTSTAMP:"))
    }
}
