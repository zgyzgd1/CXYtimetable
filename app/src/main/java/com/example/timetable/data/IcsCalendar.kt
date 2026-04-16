package com.example.timetable.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * ICS 日历文件处理工具
 * 支持将课程表导出为标准的 iCalendar (.ics) 格式，以及从 .ics 文件导入课程数据
 * ICS 格式遵循 RFC 5545 标准，可被主流日历应用识别
 */
object IcsCalendar {
    // 日期时间格式化器，格式：yyyyMMdd'T'HHmmss
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    // 用于分割行的正则表达式，支持不同平台的换行符
    private val lineSplit = Regex("\\r?\\n")
    private val multiValueKeys = setOf("EXDATE")
    private val systemZone: ZoneId = ZoneId.systemDefault()

    /**
     * 将课程列表写入 ICS 格式的字符串
     *
     * @param entries 课程条目列表
     * @param calendarName 日历名称，默认为"课程表助手"
     * @return ICS 格式的文本内容
     */
    fun write(entries: List<TimetableEntry>, calendarName: String = "课程表助手"): String {
        val builder = StringBuilder()

        // 写入 ICS 文件头部信息
        builder.appendLine("BEGIN:VCALENDAR")
        builder.appendLine("VERSION:2.0")
        builder.appendLine("PRODID:-//TimetableMinimal//CN")
        builder.appendLine("CALSCALE:GREGORIAN")
        builder.appendLine("X-WR-TIMEZONE:${systemZone.id}")
        builder.appendLine("X-WR-CALNAME:${escapeText(calendarName)}")
        val dtStamp = utcFormatter.format(OffsetDateTime.now(ZoneOffset.UTC))

        // 按星期和时间排序后遍历所有课程
        entries
            .sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes })
            .forEach { entry ->
                // 计算课程的具体日期和时间
                val eventDate = LocalDate.parse(entry.date)
                val start = eventDate.atTime(entry.startMinutes / 60, entry.startMinutes % 60)
                val end = eventDate.atTime(entry.endMinutes / 60, entry.endMinutes % 60)

                // 写入事件详情
                builder.appendLine("BEGIN:VEVENT")
                builder.appendLine("UID:${entry.id}@timetable")
                builder.appendLine("DTSTAMP:$dtStamp")
                builder.appendLine("SUMMARY:${escapeText(entry.title)}")
                if (entry.location.isNotBlank()) {
                    builder.appendLine("LOCATION:${escapeText(entry.location)}")
                }
                if (entry.note.isNotBlank()) {
                    builder.appendLine("DESCRIPTION:${escapeText(entry.note)}")
                }
                builder.appendLine("DTSTART;TZID=${systemZone.id}:${formatter.format(start)}")
                builder.appendLine("DTEND;TZID=${systemZone.id}:${formatter.format(end)}")
                builder.appendLine("END:VEVENT")
            }

        // 写入 ICS 文件尾部
        builder.appendLine("END:VCALENDAR")
        return builder.toString()
    }

    /**
     * 解析 ICS 格式的文本内容为课程列表
     *
     * @param content ICS 格式的文本内容
     * @return 解析后的课程条目列表
     */
    fun parse(content: String): List<TimetableEntry> {
        val entries = mutableListOf<TimetableEntry>()
        val lines = unfoldLines(content)  // 处理续行
        var insideEvent = false
        var current = LinkedHashMap<String, String>()

        // 逐行解析 ICS 内容
        for (line in lines) {
            when {
                // 遇到事件开始标记
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    insideEvent = true
                    current = LinkedHashMap()
                }
                // 遇到事件结束标记
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (insideEvent) {
                        entries += parseEventEntries(current)
                    }
                    insideEvent = false
                }
                // 在事件内部，解析键值对
                insideEvent -> {
                    val separatorIndex = line.indexOf(':')
                    if (separatorIndex <= 0) {
                        continue
                    }
                    val rawKey = line.substring(0, separatorIndex)
                    val rawValue = line.substring(separatorIndex + 1)
                    // 提取属性名（去除参数部分）并转为大写
                    val key = rawKey.substringBefore(';').uppercase()
                    val value = unescapeText(rawValue)

                    val tzid = rawKey
                        .split(';')
                        .drop(1)
                        .firstNotNullOfOrNull { token ->
                            val name = token.substringBefore('=').uppercase()
                            if (name == "TZID") token.substringAfter('=', "").ifBlank { null } else null
                        }

                    current[key] = if (key in multiValueKeys && current[key] != null) {
                        "${current[key]},$value"
                    } else {
                        value
                    }

                    if (tzid != null) {
                        val timezoneKey = "${key}_TZID"
                        current[timezoneKey] = if (key in multiValueKeys && current[timezoneKey] != null) {
                            "${current[timezoneKey]},$tzid"
                        } else {
                            tzid
                        }
                    }
                }
            }
        }

        return entries.distinctBy {
            "${it.title}|${it.date}|${it.startMinutes}|${it.endMinutes}|${it.location}|${it.note}"
        }
    }

    /**
     * 解析单个 VEVENT 事件字段为课程条目
     *
     * @param fields 事件的所有字段映射
     * @return 解析成功的课程条目，失败返回 null
     */
    private fun parseEventEntries(fields: Map<String, String>): List<TimetableEntry> {
        val title = fields["SUMMARY"]?.trim().orEmpty()
        val startText = fields["DTSTART"] ?: return emptyList()
        val endText = fields["DTEND"]
        if (title.isBlank()) return emptyList()

        val startTzid = fields["DTSTART_TZID"]
        val endTzid = fields["DTEND_TZID"] ?: startTzid

        // 解析开始和结束时间
        val start = parseDateTime(startText, startTzid) ?: return emptyList()
        val end = parseDateTime(endText ?: "", endTzid) ?: start.plusHours(1)
        if (!end.isAfter(start)) return emptyList()

        val uidBase = fields["UID"]?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
        val location = fields["LOCATION"].orEmpty()
        val note = fields["DESCRIPTION"].orEmpty()
        val exDates = parseExDates(fields, startTzid)

        val rrule = parseRRule(fields["RRULE"].orEmpty(), startTzid)
        if (rrule == null) {
            if (normalizeMinute(start) in exDates) return emptyList()
            return buildEntry(
                id = uidBase,
                title = title,
                start = start,
                end = end,
                location = location,
                note = note,
            )?.let(::listOf).orEmpty()
        }

        val interval = rrule.interval.coerceAtLeast(1)
        val result = mutableListOf<TimetableEntry>()
        val durationMinutes = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(1)

        if (rrule.freq == "WEEKLY" && rrule.byDays.isNotEmpty()) {
            var weekAnchor = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var emitted = 0

            while (
                emitted < MAX_EXPANDED_OCCURRENCES &&
                    (rrule.count == null || emitted < rrule.count)
            ) {
                for (day in rrule.byDays) {
                    if (rrule.count != null && emitted >= rrule.count) break

                    val occurrenceDate = weekAnchor.plusDays((day.value - 1).toLong())
                    val occurrenceStart = LocalDateTime.of(occurrenceDate, start.toLocalTime())
                    if (occurrenceStart.isBefore(start)) continue
                    if (rrule.until != null && occurrenceStart.isAfter(rrule.until)) {
                        return result
                    }
                    if (normalizeMinute(occurrenceStart) in exDates) continue

                    val occurrenceEnd = occurrenceStart.plusMinutes(durationMinutes)
                    buildEntry(
                        id = "$uidBase#$emitted",
                        title = title,
                        start = occurrenceStart,
                        end = occurrenceEnd,
                        location = location,
                        note = note,
                    )?.let {
                        result += it
                        emitted++
                    }
                }

                weekAnchor = weekAnchor.plusWeeks(interval.toLong())
                val nextWeekFirstStart = LocalDateTime.of(weekAnchor, start.toLocalTime())
                if (rrule.until != null && nextWeekFirstStart.isAfter(rrule.until)) break
            }

            return result
        }

        var occurrenceStart = start
        var occurrenceEnd = end
        var occurrenceIndex = 0

        // 用次数上限保护异常 RRULE，避免死循环
        while (occurrenceIndex < MAX_EXPANDED_OCCURRENCES) {
            if (rrule.count != null && occurrenceIndex >= rrule.count) break
            if (rrule.until != null && occurrenceStart.isAfter(rrule.until)) break

            if (normalizeMinute(occurrenceStart) !in exDates) {
                buildEntry(
                    id = "$uidBase#$occurrenceIndex",
                    title = title,
                    start = occurrenceStart,
                    end = occurrenceEnd,
                    location = location,
                    note = note,
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
            TimetableEntry(
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

    private fun parseRRule(rule: String, defaultTzid: String? = null): RecurrenceRule? {
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
        val until = parts["UNTIL"]?.let { parseDateTime(it, defaultTzid) }
        val count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
        val byDays = parts["BYDAY"]
            ?.split(',')
            ?.mapNotNull(::parseByDay)
            ?.distinct()
            .orEmpty()
        return RecurrenceRule(freq = freq, interval = interval, until = until, count = count, byDays = byDays)
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
            .mapNotNull { token -> parseDateTime(token.trim(), exdateTzid) }
            .map(::normalizeMinute)
            .toSet()
    }

    private fun normalizeMinute(value: LocalDateTime): LocalDateTime {
        return LocalDateTime.of(value.toLocalDate(), LocalTime.of(value.hour, value.minute))
    }

    private data class RecurrenceRule(
        val freq: String,
        val interval: Int,
        val until: LocalDateTime?,
        val count: Int?,
        val byDays: List<DayOfWeek>,
    )

    /**
     * 处理 ICS 文件的续行（unfold lines）
     * ICS 规范中，长行可以用空格或制表符开头的续行来表示
     *
     * @param content 原始 ICS 文本内容
     * @return 处理后的行列表
     */
    private fun unfoldLines(content: String): List<String> {
        val result = mutableListOf<String>()
        content.split(lineSplit).forEach { rawLine ->
            when {
                // 如果是续行（以空格或制表符开头），合并到上一行
                rawLine.startsWith(' ') || rawLine.startsWith('\t') -> {
                    if (result.isNotEmpty()) {
                        result[result.lastIndex] = result.last() + rawLine.trimStart()
                    }
                }
                // 否则作为新行添加
                else -> result += rawLine.trimEnd()
            }
        }
        return result
    }

    /**
     * 解析日期时间字符串
     * 支持多种格式：纯日期（8位）、完整日期时间（15位）、ISO 格式
     *
     * @param value 日期时间字符串
     * @return 解析后的 LocalDateTime，失败返回 null
     */
    private fun parseDateTime(value: String, tzid: String? = null): LocalDateTime? {
        if (value.isBlank()) return null
        val raw = value.trim()
        val cleaned = raw.removeSuffix("Z")  // 移除 UTC 标识
        val sourceZone = resolveZone(tzid) ?: systemZone

        // 处理 RFC5545 UTC 时间（例如 20260413T080000Z）
        if (raw.endsWith("Z") && cleaned.length == 15) {
            return runCatching {
                OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                    .atZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrElse {
                runCatching {
                    LocalDateTime.parse(cleaned, formatter)
                        .atZone(sourceZone)
                        .withZoneSameInstant(systemZone)
                        .toLocalDateTime()
                }.getOrNull()
            }
        }

        // 处理带时区偏移时间（例如 20260413T080000+0800 / +08:00）
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
            // 8位纯日期格式（如 20240101）
            8 -> LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            // 15位完整日期时间格式（如 20240101T080000）
            15 -> runCatching {
                LocalDateTime.parse(cleaned, formatter)
                    .atZone(sourceZone)
                    .withZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
            // 其他格式尝试使用 ISO 格式解析
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

    private fun resolveZone(tzid: String?): ZoneId? {
        val value = tzid?.trim().orEmpty()
        if (value.isBlank()) return null
        return runCatching { ZoneId.of(value) }.getOrNull()
    }

    /**
     * 转义文本中的特殊字符，符合 ICS 规范
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    private fun escapeText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    /**
     * 反转义文本中的特殊字符
     *
     * @param value 转义后的文本
     * @return 原始文本
     */
    private fun unescapeText(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private const val MAX_EXPANDED_OCCURRENCES = 512
}
