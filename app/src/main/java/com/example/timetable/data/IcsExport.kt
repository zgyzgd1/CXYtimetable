package com.example.timetable.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

object IcsExport {
    fun write(entries: List<TimetableEntry>, calendarName: String = "Timetable"): String {
        val builder = StringBuilder()

        icsAppendLine(builder, "BEGIN:VCALENDAR")
        icsAppendLine(builder, "VERSION:2.0")
        icsAppendLine(builder, "PRODID:-//TimetableMinimal//CN")
        icsAppendLine(builder, "CALSCALE:GREGORIAN")
        icsAppendLine(builder, "X-WR-TIMEZONE:${systemZone.id}")
        icsAppendLine(builder, "X-WR-CALNAME:${icsEscapeText(calendarName)}")
        val dtStamp = icsUtcFormatter.format(OffsetDateTime.now(ZoneOffset.UTC))

        entries
            .sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes })
            .flatMap(::buildEventsForExport)
            .sortedWith(compareBy<IcsExportedEvent> { it.start }.thenBy { it.entry.title })
            .forEach { event ->
                icsAppendLine(builder, "BEGIN:VEVENT")
                icsAppendLine(builder, "UID:${event.uid}@timetable")
                icsAppendLine(builder, "DTSTAMP:$dtStamp")
                icsAppendLine(builder, "SUMMARY:${icsEscapeText(event.entry.title)}")
                if (event.entry.location.isNotBlank()) {
                    icsAppendLine(builder, "LOCATION:${icsEscapeText(event.entry.location)}")
                }
                if (event.entry.note.isNotBlank()) {
                    icsAppendLine(builder, "DESCRIPTION:${icsEscapeText(event.entry.note)}")
                }
                icsAppendLine(builder, "DTSTART;TZID=${systemZone.id}:${icsFormatter.format(event.start)}")
                icsAppendLine(builder, "DTEND;TZID=${systemZone.id}:${icsFormatter.format(event.end)}")
                appendTimetableMetadata(builder, event.entry)
                event.rrule?.let { icsAppendLine(builder, "RRULE:$it") }
                if (event.exDates.isNotEmpty()) {
                    icsAppendLine(
                        builder,
                        "EXDATE;TZID=${systemZone.id}:${event.exDates.joinToString(",") { icsFormatter.format(it) }}",
                    )
                }
                icsAppendLine(builder, "END:VEVENT")
            }

        icsAppendLine(builder, "END:VCALENDAR")
        return builder.toString()
    }

    private fun buildEventsForExport(entry: TimetableEntry): List<IcsExportedEvent> {
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        if (recurrence != RecurrenceType.WEEKLY) {
            return buildSingleEventForExport(entry)?.let(::listOf).orEmpty()
        }

        return when (resolveWeekRule(entry.weekRule) ?: WeekRule.ALL) {
            WeekRule.CUSTOM -> buildCustomWeeklyEventsForExport(entry)
            WeekRule.ALL -> buildRepeatingWeeklyEventForExport(entry, WeekRule.ALL)?.let(::listOf).orEmpty()
            WeekRule.ODD -> buildRepeatingWeeklyEventForExport(entry, WeekRule.ODD)?.let(::listOf).orEmpty()
            WeekRule.EVEN -> buildRepeatingWeeklyEventForExport(entry, WeekRule.EVEN)?.let(::listOf).orEmpty()
        }
    }

    private fun buildSingleEventForExport(
        entry: TimetableEntry,
        uid: String = entry.id,
        occurrenceDate: LocalDate? = null,
    ): IcsExportedEvent? {
        val resolvedDate = occurrenceDate ?: parseEntryDate(entry.date) ?: return null
        val start = occurrenceStartForExport(resolvedDate, entry.startMinutes) ?: return null
        val end = occurrenceEndForExport(resolvedDate, entry.endMinutes) ?: return null
        return IcsExportedEvent(
            uid = uid,
            entry = entry,
            start = start,
            end = end,
        )
    }

    private fun buildRepeatingWeeklyEventForExport(
        entry: TimetableEntry,
        weekRule: WeekRule,
    ): IcsExportedEvent? {
        val byDay = dayOfWeekToken(entry.dayOfWeek) ?: return buildSingleEventForExport(entry)
        val baseEvent = buildSingleEventForExport(entry) ?: return null
        val rule = buildString {
            append("FREQ=WEEKLY;")
            if (weekRule != WeekRule.ALL) {
                append("INTERVAL=2;")
            }
            append("BYDAY=$byDay")
        }
        return baseEvent.copy(
            rrule = rule,
            exDates = skippedOccurrenceDateTimesForExport(entry, weekRule),
        )
    }

    private fun buildCustomWeeklyEventsForExport(entry: TimetableEntry): List<IcsExportedEvent> {
        return customOccurrenceDatesForExport(entry)
            .mapNotNull { occurrenceDate ->
                buildSingleEventForExport(
                    entry = entry,
                    uid = "${entry.id}#${occurrenceDate}",
                    occurrenceDate = occurrenceDate,
                )
            }
    }

    private fun customOccurrenceDatesForExport(entry: TimetableEntry): List<LocalDate> {
        val firstDate = parseEntryDate(entry.date) ?: return emptyList()
        val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
            ?: firstDate
        val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val customWeeks = parseWeekList(entry.customWeekList).orEmpty()
        val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()
        val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)

        return customWeeks
            .asSequence()
            .filter { it !in skippedWeeks }
            .sorted()
            .map { weekNumber ->
                semesterWeekStart
                    .plusWeeks((weekNumber - 1).toLong())
                    .plusDays(dayOffset.toLong())
            }
            .filter { !it.isBefore(firstDate) }
            .toList()
    }

    private fun skippedOccurrenceDateTimesForExport(
        entry: TimetableEntry,
        weekRule: WeekRule,
    ): List<LocalDateTime> {
        val firstDate = parseEntryDate(entry.date) ?: return emptyList()
        val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
            ?: firstDate
        val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()
        val startTime = LocalTime.of(entry.startMinutes / 60, entry.startMinutes % 60)
        val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)

        return skippedWeeks
            .asSequence()
            .sorted()
            .filter { weekNumber -> weekMatchesRuleForExport(weekRule, weekNumber) }
            .map { weekNumber ->
                semesterWeekStart
                    .plusWeeks((weekNumber - 1).toLong())
                    .plusDays(dayOffset.toLong())
            }
            .filter { !it.isBefore(firstDate) }
            .map { occurrenceDate -> LocalDateTime.of(occurrenceDate, startTime) }
            .toList()
    }

    private fun weekMatchesRuleForExport(weekRule: WeekRule, weekNumber: Int): Boolean {
        return when (weekRule) {
            WeekRule.ALL -> true
            WeekRule.ODD -> weekNumber % 2 == 1
            WeekRule.EVEN -> weekNumber % 2 == 0
            WeekRule.CUSTOM -> false
        }
    }

    private fun dayOfWeekToken(dayOfWeek: Int): String? {
        return when (dayOfWeek) {
            1 -> "MO"
            2 -> "TU"
            3 -> "WE"
            4 -> "TH"
            5 -> "FR"
            6 -> "SA"
            7 -> "SU"
            else -> null
        }
    }

    private fun occurrenceStartForExport(date: LocalDate, startMinutes: Int): LocalDateTime? {
        return runCatching {
            date.atTime(startMinutes / 60, startMinutes % 60)
        }.getOrNull()
    }

    private fun occurrenceEndForExport(date: LocalDate, endMinutes: Int): LocalDateTime? {
        return runCatching {
            if (endMinutes == 24 * 60) {
                date.plusDays(1).atStartOfDay()
            } else {
                date.atTime(endMinutes / 60, endMinutes % 60)
            }
        }.getOrNull()
    }

    private fun appendTimetableMetadata(builder: StringBuilder, entry: TimetableEntry) {
        icsAppendLine(builder, "$TIMETABLE_ENTRY_ID_KEY:${icsEscapeText(entry.id)}")
        icsAppendLine(builder, "$TIMETABLE_RECURRENCE_KEY:${entry.recurrenceType}")
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        if (recurrence != RecurrenceType.WEEKLY) return

        icsAppendLine(builder, "$TIMETABLE_SEMESTER_START_KEY:${icsEscapeText(entry.semesterStartDate)}")
        icsAppendLine(builder, "$TIMETABLE_WEEK_RULE_KEY:${entry.weekRule}")
        icsAppendLine(builder, "$TIMETABLE_CUSTOM_WEEKS_KEY:${icsEscapeText(entry.customWeekList)}")
        icsAppendLine(builder, "$TIMETABLE_SKIP_WEEKS_KEY:${icsEscapeText(entry.skipWeekList)}")
    }
}
