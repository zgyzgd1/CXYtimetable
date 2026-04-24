package com.example.timetable.data

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

internal val icsFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
internal val icsUtcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
internal val icsLineSplit = Regex("\\r?\\n")
internal val icsMultiValueKeys = setOf("EXDATE")
internal val systemZone: ZoneId
    get() = ZoneId.systemDefault()
internal const val MAX_FOLDED_LINE_OCTETS = 75
internal const val MAX_EXPANDED_OCCURRENCES = 512

internal const val TIMETABLE_ENTRY_ID_KEY = "X-TIMETABLE-ENTRY-ID"
internal const val TIMETABLE_RECURRENCE_KEY = "X-TIMETABLE-RECURRENCE"
internal const val TIMETABLE_SEMESTER_START_KEY = "X-TIMETABLE-SEMESTER-START"
internal const val TIMETABLE_WEEK_RULE_KEY = "X-TIMETABLE-WEEK-RULE"
internal const val TIMETABLE_CUSTOM_WEEKS_KEY = "X-TIMETABLE-CUSTOM-WEEKS"
internal const val TIMETABLE_SKIP_WEEKS_KEY = "X-TIMETABLE-SKIP-WEEKS"

internal data class IcsExportedEvent(
    val uid: String,
    val entry: TimetableEntry,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val rrule: String? = null,
    val exDates: List<LocalDateTime> = emptyList(),
)

internal data class IcsParsedOccurrence(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val note: String,
)

internal data class IcsTimetableMetadata(
    val entryId: String,
    val recurrenceType: RecurrenceType,
    val semesterStartDate: String = "",
    val weekRule: WeekRule = WeekRule.ALL,
    val customWeekList: String = "",
    val skipWeekList: String = "",
)

internal data class IcsRecurrenceRule(
    val freq: String,
    val interval: Int,
    val until: LocalDateTime?,
    val count: Int?,
    val byDays: List<DayOfWeek>,
)

internal fun icsAppendLine(builder: StringBuilder, line: String) {
    if (line.isEmpty()) {
        builder.append("\r\n")
        return
    }
    var currentOctets = 0
    line.forEach { char ->
        val charOctets = char.toString().toByteArray(Charsets.UTF_8).size
        if (currentOctets + charOctets > MAX_FOLDED_LINE_OCTETS) {
            builder.append("\r\n ")
            currentOctets = 1
        }
        builder.append(char)
        currentOctets += charOctets
    }
    builder.append("\r\n")
}

internal fun icsEscapeText(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")
}

internal fun icsUnescapeText(value: String): String {
    return value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}

internal fun icsParseDateTime(value: String, tzid: String? = null): LocalDateTime? {
    if (value.isBlank()) return null
    val raw = value.trim()
    val cleaned = raw.removeSuffix("Z")
    val sourceZone = icsResolveZone(tzid) ?: systemZone

    if (raw.endsWith("Z") && cleaned.length == 15) {
        return runCatching {
            OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                .atZoneSameInstant(systemZone)
                .toLocalDateTime()
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(cleaned, icsFormatter)
                    .atZone(sourceZone)
                    .withZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
        }
    }

    if (Regex("\\d{8}T\\d{6}[+-]\\d{4}").matches(raw)) {
        return runCatching {
            OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXX"))
                .atZoneSameInstant(systemZone)
                .toLocalDateTime()
        }.getOrNull()
    }
    if (Regex("\\d{8}T\\d{6}[+-]\\d{2}:\\d{2}").matches(raw)) {
        return runCatching {
            OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXXX"))
                .atZoneSameInstant(systemZone)
                .toLocalDateTime()
        }.getOrNull()
    }

    return when (cleaned.length) {
        8 -> java.time.LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
        15 -> runCatching {
            LocalDateTime.parse(cleaned, icsFormatter)
                .atZone(sourceZone)
                .withZoneSameInstant(systemZone)
                .toLocalDateTime()
        }.getOrNull()
        else -> runCatching {
            OffsetDateTime.parse(cleaned, DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(systemZone)
                .toLocalDateTime()
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_DATE_TIME)
                    .atZone(sourceZone)
                    .withZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
        }
    }
}

internal fun icsResolveZone(tzid: String?): ZoneId? {
    val value = tzid?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching { ZoneId.of(value) }.getOrNull()
}

internal fun icsUnfoldLines(content: String): List<String> {
    val result = mutableListOf<String>()
    content.split(icsLineSplit).forEach { rawLine ->
        when {
            rawLine.startsWith(' ') || rawLine.startsWith('\t') -> {
                if (result.isNotEmpty()) {
                    result[result.lastIndex] = result.last() + rawLine.trimStart()
                }
            }
            else -> result += rawLine.trimEnd()
        }
    }
    return result
}

internal fun icsNormalizeMinute(value: LocalDateTime): LocalDateTime {
    return LocalDateTime.of(value.toLocalDate(), LocalTime.of(value.hour, value.minute))
}
