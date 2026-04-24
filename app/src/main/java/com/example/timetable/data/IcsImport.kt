package com.example.timetable.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

object IcsImport {
    fun parse(content: String): List<TimetableEntry> {
        val eventFields = mutableListOf<Map<String, String>>()
        val lines = icsUnfoldLines(content)
        var insideEvent = false
        var current = LinkedHashMap<String, String>()

        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    insideEvent = true
                    current = LinkedHashMap()
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (insideEvent) {
                        eventFields += current.toMap()
                    }
                    insideEvent = false
                }
                insideEvent -> {
                    val separatorIndex = line.indexOf(':')
                    if (separatorIndex <= 0) {
                        continue
                    }
                    val rawKey = line.substring(0, separatorIndex)
                    val rawValue = line.substring(separatorIndex + 1)
                    val key = rawKey.substringBefore(';').uppercase()
                    val value = icsUnescapeText(rawValue)

                    val tzid = rawKey
                        .split(';')
                        .drop(1)
                        .firstNotNullOfOrNull { token ->
                            val name = token.substringBefore('=').uppercase()
                            if (name == "TZID") token.substringAfter('=', "").ifBlank { null } else null
                        }

                    current[key] = if (key in icsMultiValueKeys && current[key] != null) {
                        "${current[key]},$value"
                    } else {
                        value
                    }

                    if (tzid != null) {
                        val timezoneKey = "${key}_TZID"
                        current[timezoneKey] = if (key in icsMultiValueKeys && current[timezoneKey] != null) {
                            "${current[timezoneKey]},$tzid"
                        } else {
                            tzid
                        }
                    }
                }
            }
        }

        return buildEntriesFromEventFields(eventFields)
    }

    private fun parseEventEntries(fields: Map<String, String>): List<TimetableEntry> {
        val occurrence = parseOccurrence(fields) ?: return emptyList()
        val uidBase = fields["UID"]?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
        val exDates = parseExDates(fields, fields["DTSTART_TZID"])

        val rrule = parseRRule(fields["RRULE"].orEmpty(), fields["DTSTART_TZID"])
        if (rrule == null) {
            if (icsNormalizeMinute(occurrence.start) in exDates) return emptyList()
            return buildEntry(
                id = uidBase,
                title = occurrence.title,
                start = occurrence.start,
                end = occurrence.end,
                location = occurrence.location,
                note = occurrence.note,
            )?.let(::listOf).orEmpty()
        }

        val interval = rrule.interval.coerceAtLeast(1)
        val result = mutableListOf<TimetableEntry>()
        val durationMinutes = java.time.Duration.between(occurrence.start, occurrence.end).toMinutes().coerceAtLeast(1)

        if (rrule.freq == "WEEKLY" && rrule.byDays.isNotEmpty()) {
            var weekAnchor = occurrence.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var emitted = 0
            var reachedUntil = false

            while (
                emitted < MAX_EXPANDED_OCCURRENCES &&
                    (rrule.count == null || emitted < rrule.count)
            ) {
                for (day in rrule.byDays) {
                    if (rrule.count != null && emitted >= rrule.count) break

                    val occurrenceDate = weekAnchor.plusDays((day.value - 1).toLong())
                    val recurringStart = LocalDateTime.of(occurrenceDate, occurrence.start.toLocalTime())
                    if (recurringStart.isBefore(occurrence.start)) continue
                    if (rrule.until != null && recurringStart.isAfter(rrule.until)) {
                        reachedUntil = true
                        break
                    }
                    if (icsNormalizeMinute(recurringStart) in exDates) continue

                    val occurrenceEnd = recurringStart.plusMinutes(durationMinutes)
                    buildEntry(
                        id = "$uidBase#$emitted",
                        title = occurrence.title,
                        start = recurringStart,
                        end = occurrenceEnd,
                        location = occurrence.location,
                        note = occurrence.note,
                    )?.let {
                        result += it
                        emitted++
                    }
                }

                if (reachedUntil) break
                weekAnchor = weekAnchor.plusWeeks(interval.toLong())
                val nextWeekFirstStart = LocalDateTime.of(weekAnchor, occurrence.start.toLocalTime())
                if (rrule.until != null && nextWeekFirstStart.isAfter(rrule.until)) break
            }

            return result
        }

        var occurrenceStart = occurrence.start
        var occurrenceEnd = occurrence.end
        var occurrenceIndex = 0

        while (occurrenceIndex < MAX_EXPANDED_OCCURRENCES) {
            if (rrule.count != null && occurrenceIndex >= rrule.count) break
            if (rrule.until != null && occurrenceStart.isAfter(rrule.until)) break

            if (icsNormalizeMinute(occurrenceStart) !in exDates) {
                buildEntry(
                    id = "$uidBase#$occurrenceIndex",
                    title = occurrence.title,
                    start = occurrenceStart,
                    end = occurrenceEnd,
                    location = occurrence.location,
                    note = occurrence.note,
                )?.let(result::add)
            }

            val nextStart = incrementByFreq(occurrenceStart, rrule.freq, interval) ?: break
            val nextEnd = incrementByFreq(occurrenceEnd, rrule.freq, interval) ?: break
            occurrenceStart = nextStart
            occurrenceEnd = nextEnd
            occurrenceIndex++
        }

        return result
    }

    private fun buildEntry(
        id: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        location: String,
        note: String,
    ): TimetableEntry? {
        val dateText = start.toLocalDate().toString()
        if (parseEntryDate(dateText) == null) return null

        val startMinutes = start.hour * 60 + start.minute
        val endMinutes = if (end.toLocalDate().isAfter(start.toLocalDate())) {
            24 * 60
        } else {
            end.hour * 60 + end.minute
        }
        if (endMinutes <= startMinutes) return null

        return runCatching {
            TimetableEntry.create(
                id = id,
                title = title,
                date = dateText,
                dayOfWeek = start.dayOfWeek.value,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                location = location,
                note = note,
            )
        }.getOrNull()
    }

    private fun incrementByFreq(value: LocalDateTime, freq: String, interval: Int): LocalDateTime? {
        return when (freq) {
            "DAILY" -> value.plusDays(interval.toLong())
            "WEEKLY" -> value.plusWeeks(interval.toLong())
            "MONTHLY" -> value.plusMonths(interval.toLong())
            "YEARLY" -> value.plusYears(interval.toLong())
            else -> null
        }
    }

    private fun parseRRule(rule: String, defaultTzid: String? = null): IcsRecurrenceRule? {
        if (rule.isBlank()) return null

        val parts = rule.split(';')
            .mapNotNull { token ->
                val index = token.indexOf('=')
                if (index <= 0) return@mapNotNull null
                token.substring(0, index).uppercase() to token.substring(index + 1)
            }
            .toMap()

        val freq = parts["FREQ"]?.uppercase() ?: return null
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val until = parts["UNTIL"]?.let { icsParseDateTime(it, defaultTzid) }
        val count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
        val byDays = parts["BYDAY"]
            ?.split(',')
            ?.mapNotNull(::parseByDay)
            ?.distinct()
            ?.sortedBy { it.value }
            .orEmpty()
        return IcsRecurrenceRule(freq = freq, interval = interval, until = until, count = count, byDays = byDays)
    }

    private fun parseByDay(token: String): DayOfWeek? {
        return when (token.trim().uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun parseExDates(fields: Map<String, String>, defaultTzid: String?): Set<LocalDateTime> {
        val raw = fields["EXDATE"].orEmpty()
        if (raw.isBlank()) return emptySet()

        val exdateTzid = fields["EXDATE_TZID"]
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
            ?: defaultTzid

        return raw.split(',')
            .mapNotNull { token -> icsParseDateTime(token.trim(), exdateTzid) }
            .map(::icsNormalizeMinute)
            .toSet()
    }

    private fun buildEntriesFromEventFields(eventFields: List<Map<String, String>>): List<TimetableEntry> {
        if (eventFields.isEmpty()) return emptyList()

        val entries = mutableListOf<TimetableEntry>()
        val handled = BooleanArray(eventFields.size)
        val indexedGroups = eventFields.withIndex()
            .mapNotNull { indexed ->
                indexed.value[TIMETABLE_ENTRY_ID_KEY]
                    ?.trim()
                    ?.ifBlank { null }
                    ?.let { it to indexed }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        indexedGroups.values.forEach { group ->
            val parsed = parseMetadataEntryGroup(group.map { it.value }) ?: return@forEach
            entries += parsed
            group.forEach { handled[it.index] = true }
        }

        eventFields.forEachIndexed { index, fields ->
            if (!handled[index]) {
                entries += parseEventEntries(fields)
            }
        }

        return entries.distinctBy { it.id }
    }

    private fun parseMetadataEntryGroup(fieldGroup: List<Map<String, String>>): TimetableEntry? {
        val metadata = parseTimetableMetadata(fieldGroup.firstOrNull() ?: return null) ?: return null
        if (fieldGroup.size > 1 && metadata.recurrenceType != RecurrenceType.WEEKLY) return null

        val parsedOccurrences = fieldGroup
            .mapNotNull(::parseOccurrence)
            .sortedBy { it.start }
        if (parsedOccurrences.size != fieldGroup.size || parsedOccurrences.isEmpty()) return null

        val firstOccurrence = parsedOccurrences.first()
        val sameIdentity = parsedOccurrences.all { occurrence ->
            occurrence.title == firstOccurrence.title &&
                occurrence.location == firstOccurrence.location &&
                occurrence.note == firstOccurrence.note &&
                occurrence.start.toLocalTime() == firstOccurrence.start.toLocalTime() &&
                occurrence.end.toLocalTime() == firstOccurrence.end.toLocalTime()
        }
        if (!sameIdentity) return null

        return buildEntryFromMetadata(
            metadata = metadata,
            occurrence = firstOccurrence,
        )
    }

    private fun parseTimetableMetadata(fields: Map<String, String>): IcsTimetableMetadata? {
        val entryId = fields[TIMETABLE_ENTRY_ID_KEY]?.trim().orEmpty().ifBlank { return null }
        val recurrenceType = resolveRecurrenceType(fields[TIMETABLE_RECURRENCE_KEY].orEmpty()) ?: return null
        if (recurrenceType != RecurrenceType.WEEKLY) {
            return IcsTimetableMetadata(
                entryId = entryId,
                recurrenceType = recurrenceType,
            )
        }

        val semesterStartDate = fields[TIMETABLE_SEMESTER_START_KEY]?.trim().orEmpty()
        val weekRule = resolveWeekRule(fields[TIMETABLE_WEEK_RULE_KEY].orEmpty()) ?: return null
        val customWeekList = fields[TIMETABLE_CUSTOM_WEEKS_KEY].orEmpty()
        val skipWeekList = fields[TIMETABLE_SKIP_WEEKS_KEY].orEmpty()
        if (semesterStartDate.isBlank() || parseEntryDate(semesterStartDate) == null) return null
        if (parseWeekList(customWeekList) == null || parseWeekList(skipWeekList) == null) return null

        return IcsTimetableMetadata(
            entryId = entryId,
            recurrenceType = recurrenceType,
            semesterStartDate = semesterStartDate,
            weekRule = weekRule,
            customWeekList = customWeekList,
            skipWeekList = skipWeekList,
        )
    }

    private fun buildEntryFromMetadata(
        metadata: IcsTimetableMetadata,
        occurrence: IcsParsedOccurrence,
    ): TimetableEntry? {
        return runCatching {
            TimetableEntry.create(
                id = metadata.entryId,
                title = occurrence.title,
                date = occurrence.start.toLocalDate().toString(),
                dayOfWeek = occurrence.start.dayOfWeek.value,
                startMinutes = occurrence.start.hour * 60 + occurrence.start.minute,
                endMinutes = if (occurrence.end.toLocalDate().isAfter(occurrence.start.toLocalDate())) {
                    24 * 60
                } else {
                    occurrence.end.hour * 60 + occurrence.end.minute
                },
                location = occurrence.location,
                note = occurrence.note,
                recurrenceType = metadata.recurrenceType.name,
                semesterStartDate = metadata.semesterStartDate,
                weekRule = metadata.weekRule.name,
                customWeekList = metadata.customWeekList,
                skipWeekList = metadata.skipWeekList,
            )
        }.getOrNull()
    }

    private fun parseOccurrence(fields: Map<String, String>): IcsParsedOccurrence? {
        val title = fields["SUMMARY"]?.trim().orEmpty()
        val startText = fields["DTSTART"] ?: return null
        val endText = fields["DTEND"]
        if (title.isBlank()) return null

        val startTzid = fields["DTSTART_TZID"]
        val endTzid = fields["DTEND_TZID"] ?: startTzid
        val start = icsParseDateTime(startText, startTzid) ?: return null
        val end = icsParseDateTime(endText ?: "", endTzid) ?: start.plusHours(1)
        if (!end.isAfter(start)) return null

        return IcsParsedOccurrence(
            title = title,
            start = start,
            end = end,
            location = fields["LOCATION"].orEmpty(),
            note = fields["DESCRIPTION"].orEmpty(),
        )
    }
}
