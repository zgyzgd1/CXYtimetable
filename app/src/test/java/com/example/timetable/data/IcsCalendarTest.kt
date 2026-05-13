package com.example.timetable.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test(timeout = 1000)
    fun parseWeeklyByDayBeyondSupportedDateRangeTerminates() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-upper-bound
            SUMMARY:边界课程
            DTSTART;TZID=Asia/Shanghai:21001231T080000
            DTEND;TZID=Asia/Shanghai:21001231T090000
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun unescapeTextProtectsLiteralBackslashBeforeNewlineEscape() {
        assertEquals("\\n", icsUnescapeText("\\\\n"))
        assertEquals("\n", icsUnescapeText("\\n"))
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

    @Test
    fun writeSupportsEntriesEndingAtMidnight() {
        val entry = TimetableEntry(
            id = "entry-midnight",
            title = "晚课",
            date = "2026-04-15",
            dayOfWeek = 3,
            startMinutes = 23 * 60,
            endMinutes = 24 * 60,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(
            text.contains("DTEND;TZID=${ZoneId.systemDefault().id}:20260416T000000"),
        )
    }

    @Test
    fun writeWeeklyEntryIncludesRRule() {
        val entry = TimetableEntry(
            id = "weekly-entry",
            title = "Operating Systems",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
    }

    @Test
    fun writeWeeklyEntryRoundTripsRecurringMetadata() {
        val entry = TimetableEntry(
            id = "weekly-entry",
            title = "Operating Systems",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
            skipWeekList = "3,7",
        )

        val text = IcsCalendar.write(listOf(entry))
        val parsed = IcsCalendar.parse(text)

        assertTrue(text.contains("X-TIMETABLE-ENTRY-ID:weekly-entry"))
        assertEquals(1, parsed.size)
        assertEquals(entry.id, parsed.single().id)
        assertEquals(entry.recurrenceType, parsed.single().recurrenceType)
        assertEquals(entry.semesterStartDate, parsed.single().semesterStartDate)
        assertEquals(entry.weekRule, parsed.single().weekRule)
        assertEquals(entry.skipWeekList, parsed.single().skipWeekList)
    }

    @Test
    fun writeOddWeekEntryIncludesIntervalAndExDate() {
        val entry = TimetableEntry(
            id = "odd-entry",
            title = "Physics",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
            skipWeekList = "3",
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"))
        assertTrue(
            text.contains("EXDATE;TZID=${ZoneId.systemDefault().id}:20260316T080000"),
        )
    }

    @Test
    fun writeCustomWeekEntryExportsEveryOccurrenceAndRoundTripsRecurringEntry() {
        val entry = TimetableEntry(
            id = "custom-entry",
            title = "Compiler",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "1,3,5",
            skipWeekList = "3",
        )

        val text = IcsCalendar.write(listOf(entry))
        val parsed = IcsCalendar.parse(text)

        assertFalse(text.contains("RRULE:"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(text).count())
        assertEquals(1, parsed.size)
        assertEquals(entry.id, parsed.single().id)
        assertEquals(entry.date, parsed.single().date)
        assertEquals(entry.recurrenceType, parsed.single().recurrenceType)
        assertEquals(entry.semesterStartDate, parsed.single().semesterStartDate)
        assertEquals(entry.weekRule, parsed.single().weekRule)
        assertEquals(entry.customWeekList, parsed.single().customWeekList)
        assertEquals(entry.skipWeekList, parsed.single().skipWeekList)
    }

    @Test
    fun parseWeeklyRRuleSortsByDayBeforeApplyingUntil() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-ordered
            SUMMARY:Algorithms
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            RRULE:FREQ=WEEKLY;UNTIL=20260414T235959;BYDAY=WE,MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals("2026-04-13", parsed.single().date)
    }

    @Test
    fun parseAllDayDateEventWithoutDtEndUsesFullDay() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:all-day
            SUMMARY:Exam Day
            DTSTART;VALUE=DATE:20260413
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals(0, parsed.single().startMinutes)
        assertEquals(24 * 60, parsed.single().endMinutes)
    }

    @Test(timeout = 1000)
    fun parseUnsupportedOrdinalMonthlyByDayDoesNotExpandAsPlainMonthly() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:monthly-ordinal
            SUMMARY:Faculty Meeting
            DTSTART;TZID=Asia/Shanghai:20260414T080000
            DTEND;TZID=Asia/Shanghai:20260414T090000
            RRULE:FREQ=MONTHLY;BYDAY=2TU
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals("2026-04-14", parsed.single().date)
    }

    @Test
    fun writeWeeklyEntryBoundsRRule() {
        val entry = TimetableEntry(
            id = "bounded-weekly",
            title = "Operating Systems",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=512"))
    }

    @Test
    fun writeFoldsLongUtf8LinesAndRemainsParsable() {
        val entry = TimetableEntry(
            id = "long-note",
            title = "软件工程基础",
            date = "2026-04-15",
            dayOfWeek = 3,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            note = "这是一个很长的备注。".repeat(20),
        )

        val text = IcsCalendar.write(listOf(entry))
        val parsed = IcsCalendar.parse(text)

        assertTrue(text.contains("\r\n "))
        assertEquals(1, parsed.size)
        assertEquals(entry.note, parsed.single().note)
    }

    @Test
    fun parseKeepsEventsWithSameContentButDifferentUid() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:event-a
            SUMMARY:Database
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            END:VEVENT
            BEGIN:VEVENT
            UID:event-b
            SUMMARY:Database
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(2, parsed.size)
        assertEquals(setOf("event-a", "event-b"), parsed.map { it.id }.toSet())
    }

    @Test
    fun parseKeepsRecurrenceOverridesWithSameUid() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:event-a
            SUMMARY:Database
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            END:VEVENT
            BEGIN:VEVENT
            UID:event-a
            RECURRENCE-ID;TZID=Asia/Shanghai:20260420T080000
            SUMMARY:Database
            DTSTART;TZID=Asia/Shanghai:20260420T100000
            DTEND;TZID=Asia/Shanghai:20260420T110000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(2, parsed.size)
        assertEquals(setOf("event-a", "event-a#20260420T080000"), parsed.map { it.id }.toSet())
    }

    @Test
    fun parseKeepsDuplicateUidEventsWithUniqueImportedIds() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:event-a
            SUMMARY:Database A
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            END:VEVENT
            BEGIN:VEVENT
            UID:event-a
            SUMMARY:Database B
            DTSTART;TZID=Asia/Shanghai:20260414T080000
            DTEND;TZID=Asia/Shanghai:20260414T090000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(2, parsed.size)
        assertEquals(setOf("event-a", "event-a#1"), parsed.map { it.id }.toSet())
    }
}
