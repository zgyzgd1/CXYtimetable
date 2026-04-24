package com.example.timetable.data

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表示单个课程表条目（课程或事件）。
 *
 * @property id 唯一标识符，自动生成的 UUID
 * @property title 课程标题
 * @property date 课程日期 (yyyy-MM-dd 格式)
 * @property dayOfWeek 星期几 (1-7，周一到周日)
 * @property startMinutes 开始时间，从午夜开始的分钟数
 * @property endMinutes 结束时间，从午夜开始的分钟数
 * @property location 课程地点
 * @property note 附加备注
 */
@Entity(
    tableName = "timetable_entries",
    indices = [
        Index(value = ["date", "startMinutes"]),
        Index(value = ["dayOfWeek"]),
    ],
)
data class TimetableEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val location: String = "",
    val note: String = "",
    val recurrenceType: String = RecurrenceType.NONE.name,
    val semesterStartDate: String = "",
    val weekRule: String = WeekRule.ALL.name,
    val customWeekList: String = "",
    val skipWeekList: String = "",
) {
    companion object {
        /**
         * 创建一个带有完整数据验证的 [TimetableEntry]。
         * 用于 UI 层确保构造新条目时的数据有效性。
         * Room 反序列化通过主构造函数绕过验证以提高性能。
         */
        fun create(
            id: String = UUID.randomUUID().toString(),
            title: String,
            date: String,
            dayOfWeek: Int,
            startMinutes: Int,
            endMinutes: Int,
            location: String = "",
            note: String = "",
            recurrenceType: String = RecurrenceType.NONE.name,
            semesterStartDate: String = "",
            weekRule: String = WeekRule.ALL.name,
            customWeekList: String = "",
            skipWeekList: String = "",
        ): TimetableEntry {
            require(parseEntryDate(date) != null) { "课程日期无效: $date" }
            require(dayOfWeek in 1..7) { "dayOfWeek 必须在 1..7 范围内: $dayOfWeek" }
            require(startMinutes in 0 until 24 * 60) { "开始时间不合法: $startMinutes" }
            require(endMinutes in 1..24 * 60) { "结束时间不合法: $endMinutes" }
            require(startMinutes < endMinutes) { "结束时间需要晚于开始时间" }
            require(resolveRecurrenceType(recurrenceType) != null) { "重复规则无效: $recurrenceType" }
            require(resolveWeekRule(weekRule) != null) { "周次规则无效: $weekRule" }
            if (semesterStartDate.isNotBlank()) {
                require(parseEntryDate(semesterStartDate) != null) { "学期开学日期无效: $semesterStartDate" }
            }
            require(parseWeekList(customWeekList) != null) { "自定义周次格式错误: $customWeekList" }
            require(parseWeekList(skipWeekList) != null) { "跳过周次格式错误: $skipWeekList" }
            return TimetableEntry(
                id = id,
                title = title,
                date = date,
                dayOfWeek = dayOfWeek,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                location = location,
                note = note,
                recurrenceType = recurrenceType,
                semesterStartDate = semesterStartDate,
                weekRule = weekRule,
                customWeekList = customWeekList,
                skipWeekList = skipWeekList,
            )
        }
    }
}

/**
 * 重复类型枚举
 */
enum class RecurrenceType {
    /** 不重复 */
    NONE,
    /** 按周重复 */
    WEEKLY,
}

/**
 * 周次规则枚举
 */
enum class WeekRule {
    /** 每周 */
    ALL,
    /** 单周 */
    ODD,
    /** 双周 */
    EVEN,
    /** 自定义周次 */
    CUSTOM,
}

/**
 * 星期选项数据类，用于 UI 显示完整和缩写的星期标签。
 *
 * @property value 星期数字 (1-7)
 * @property label 完整标签 (例如 "Monday")
 * @property shortLabel 缩写标签 (例如 "Mon")
 */
data class DayOption(
    val value: Int,
    val label: String,
    val shortLabel: String,
)

// 整周的星期选项
val WeekdayOptions = listOf(
    DayOption(1, "Monday", "Mon"),
    DayOption(2, "Tuesday", "Tue"),
    DayOption(3, "Wednesday", "Wed"),
    DayOption(4, "Thursday", "Thu"),
    DayOption(5, "Friday", "Fri"),
    DayOption(6, "Saturday", "Sat"),
    DayOption(7, "Sunday", "Sun"),
)

/**
 * 将从午夜开始的分钟数格式化为时间字符串（HH:mm 格式）。
 *
 * @param minutes 从午夜开始的分钟数
 * @return 格式化的时间字符串，例如 "08:30"
 */
fun formatMinutes(minutes: Int): String {
    val safeMinutes = minutes.coerceIn(0, 24 * 60)
    if (safeMinutes == 24 * 60) return "24:00"
    return "%02d:%02d".format(safeMinutes / 60, safeMinutes % 60)
}

/**
 * 解析时间字符串为从午夜开始的分钟数。
 *
 * @param text "HH:mm" 格式的时间字符串
 * @return 成功时返回从午夜开始的分钟数，失败时返回 null
 */
fun parseMinutes(text: String): Int? {
    val trimmed = text.trim().replace('：', ':')
    if (trimmed.isBlank()) return null

    val (hour, minute) = if (':' in trimmed) {
        val parts = trimmed.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        h to m
    } else {
        if (!trimmed.all { it.isDigit() }) return null
        when (trimmed.length) {
            1, 2 -> (trimmed.toIntOrNull() ?: return null) to 0
            3 -> (trimmed.substring(0, 1).toIntOrNull() ?: return null) to (trimmed.substring(1).toIntOrNull() ?: return null)
            4 -> (trimmed.substring(0, 2).toIntOrNull() ?: return null) to (trimmed.substring(2).toIntOrNull() ?: return null)
            else -> return null
        }
    }

    if (hour !in 0..24 || minute !in 0..59) return null
    if (hour == 24 && minute != 0) return null
    return hour * 60 + minute
}

/**
 * 返回给定星期数字的完整星期标签。
 *
 * @param dayOfWeek 星期数字 (1-7)
 * @return 完整标签，例如 "Monday"，如果未找到则返回空字符串
 */
fun dayLabel(dayOfWeek: Int): String {
    return WeekdayOptions.firstOrNull { it.value == dayOfWeek }?.label ?: ""
}

/**
 * 返回给定星期数字的缩写星期标签。
 *
 * @param dayOfWeek 星期数字 (1-7)
 * @return 缩写标签，例如 "Mon"，如果未找到则返回空字符串
 */
fun dayShortLabel(dayOfWeek: Int): String {
    return WeekdayOptions.firstOrNull { it.value == dayOfWeek }?.shortLabel ?: ""
}

private val entryDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val minSupportedDate = LocalDate.of(1970, 1, 1)
private val maxSupportedDate = LocalDate.of(2100, 12, 31)

/** 安全上限：跳过周次时的最大迭代次数，防止由于大的 skipWeekList 导致长循环。 */
private const val MAX_SKIP_WEEK_ITERATIONS = 200

/**
 * 解析条目日期字符串为 LocalDate 对象。
 *
 * @param text 日期字符串
 * @return 解析成功时返回 LocalDate，失败时返回 null
 */
fun parseEntryDate(text: String): LocalDate? {
    val parsed = runCatching { LocalDate.parse(text.trim(), entryDateFormatter) }.getOrNull() ?: return null
    return parsed.takeIf { it in minSupportedDate..maxSupportedDate }
}

/**
 * 格式化日期标签。
 *
 * @param date 日期字符串
 * @return 格式化的日期标签，例如 "2026-09-01 Monday"
 */
fun formatDateLabel(date: String): String {
    val parsed = parseEntryDate(date) ?: return date
    return "%d-%02d-%02d %s".format(parsed.year, parsed.monthValue, parsed.dayOfMonth, dayLabel(parsed.dayOfWeek.value))
}

/**
 * 获取指定星期几的默认日期。
 *
 * @param dayOfWeek 星期数字 (1-7)
 * @return 默认日期字符串
 */
fun defaultDateForWeekday(dayOfWeek: Int): String {
    val safeDay = dayOfWeek.coerceIn(1, 7)
    val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return monday.plusDays((safeDay - 1).toLong()).toString()
}

/**
 * 解析重复类型字符串为 RecurrenceType 枚举。
 *
 * @param value 重复类型字符串
 * @return 解析成功时返回 RecurrenceType，失败时返回 null
 */
fun resolveRecurrenceType(value: String): RecurrenceType? {
    return runCatching { RecurrenceType.valueOf(value.trim().uppercase()) }.getOrNull()
}

/**
 * 解析周次规则字符串为 WeekRule 枚举。
 *
 * @param value 周次规则字符串
 * @return 解析成功时返回 WeekRule，失败时返回 null
 */
fun resolveWeekRule(value: String): WeekRule? {
    return runCatching { WeekRule.valueOf(value.trim().uppercase()) }.getOrNull()
}

/**
 * 标准化周次列表文本。
 */
internal fun normalizeWeekListText(raw: String): String {
    return raw.trim()
        .replace('，', ',')
        .replace('—', '-')
        .replace('–', '-')
        .replace(" ", "")
}

/**
 * 解析周次列表字符串为整数集合。
 *
 * @param raw 周次列表字符串，例如 "1,3,5" 或 "1-16"
 * @return 解析成功时返回周次集合，失败时返回 null
 */
fun parseWeekList(raw: String): Set<Int>? {
    if (raw.isBlank()) return emptySet()
    val result = linkedSetOf<Int>()
    val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptySet()

    for (token in tokens) {
        if ('-' in token) {
            val parts = token.split('-', limit = 2).map { it.trim() }
            if (parts.size != 2) return null
            val start = parts[0].toIntOrNull() ?: return null
            val end = parts[1].toIntOrNull() ?: return null
            if (start <= 0 || end <= 0 || end < start) return null
            for (week in start..end) {
                result += week
            }
        } else {
            val week = token.toIntOrNull() ?: return null
            if (week <= 0) return null
            result += week
        }
    }
    return result
}

/**
 * 计算从学期开始到目标日期的周数。
 *
 * @param semesterStartDate 学期开始日期
 * @param targetDate 目标日期
 * @return 周数，如果目标日期在学期开始之前则返回 null
 */
fun weekIndexFromSemesterStart(
    semesterStartDate: LocalDate,
    targetDate: LocalDate,
): Int? {
    val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetWeekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    if (targetWeekStart.isBefore(semesterWeekStart)) return null
    val dayDiff = ChronoUnit.DAYS.between(semesterWeekStart, targetWeekStart)
    return (dayDiff / 7L).toInt() + 1
}

/**
 * 获取当前周数。
 *
 * @param semesterStartDateText 学期开始日期字符串
 * @param today 今天的日期，默认为当前日期
 * @return 当前周数，如果学期开始日期无效则返回 null
 */
fun currentWeekIndex(
    semesterStartDateText: String,
    today: LocalDate = LocalDate.now(),
): Int? {
    val semesterStartDate = parseEntryDate(semesterStartDateText) ?: return null
    return weekIndexFromSemesterStart(semesterStartDate, today)
}

/**
 * 计算下一次课程出现的日期。
 *
 * @param entry 课程条目
 * @param onOrAfter 从该日期开始查找
 * @return 下一次出现的日期，如果没有则返回 null
 */
fun nextOccurrenceDate(
    entry: TimetableEntry,
    onOrAfter: LocalDate,
): LocalDate? {
    val firstOccurrenceDate = parseEntryDate(entry.date) ?: return null
    val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
    if (recurrence == RecurrenceType.NONE) {
        return firstOccurrenceDate.takeIf { !it.isBefore(onOrAfter) }
    }

    val searchStart = maxOf(onOrAfter, firstOccurrenceDate)
    val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
        ?: firstOccurrenceDate
    val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)
    val weekRule = resolveWeekRule(entry.weekRule) ?: return null
    val skippedWeeks = parseWeekList(entry.skipWeekList) ?: return null

    if (weekRule == WeekRule.CUSTOM) {
        val customWeeks = parseWeekList(entry.customWeekList) ?: return null
        return customWeeks
            .asSequence()
            .filter { it !in skippedWeeks }
            .sorted()
            .map { weekNumber ->
                semesterWeekStart
                    .plusWeeks((weekNumber - 1).toLong())
                    .plusDays(dayOffset.toLong())
            }
            .firstOrNull { date ->
                !date.isBefore(searchStart) &&
                    !date.isBefore(firstOccurrenceDate) &&
                    occursOnDate(entry, date)
            }
    }

    val daysUntilTargetWeekday = (entry.dayOfWeek - searchStart.dayOfWeek.value + 7) % 7
    val firstCandidate = searchStart.plusDays(daysUntilTargetWeekday.toLong())
    var candidateWeek = weekIndexFromSemesterStart(semesterStartDate, firstCandidate) ?: return null
    if (weekRule == WeekRule.ODD && candidateWeek % 2 == 0) {
        candidateWeek++
    }
    if (weekRule == WeekRule.EVEN && candidateWeek % 2 == 1) {
        candidateWeek++
    }

    val weekStep = if (weekRule == WeekRule.ALL) 1 else 2
    var skipIterations = 0
    while (candidateWeek in skippedWeeks) {
        candidateWeek += weekStep
        if (++skipIterations > MAX_SKIP_WEEK_ITERATIONS) return null
    }

    val nextDate = semesterWeekStart
        .plusWeeks((candidateWeek - 1).toLong())
        .plusDays(dayOffset.toLong())
    return nextDate.takeIf { !it.isBefore(searchStart) && occursOnDate(entry, it) }
}

/**
 * 检查课程是否在指定日期出现。
 *
 * @param entry 课程条目
 * @param targetDate 目标日期
 * @return 如果课程在该日期出现则返回 true，否则返回 false
 */
fun occursOnDate(
    entry: TimetableEntry,
    targetDate: LocalDate,
): Boolean {
    val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
    if (recurrence == RecurrenceType.NONE) {
        return entry.date == targetDate.toString()
    }

    if (targetDate.dayOfWeek.value != entry.dayOfWeek) return false

    val firstOccurrenceDate = parseEntryDate(entry.date) ?: return false
    if (targetDate.isBefore(firstOccurrenceDate)) return false

    val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
        ?: firstOccurrenceDate
    val weekIndex = weekIndexFromSemesterStart(semesterStartDate, targetDate) ?: return false

    val weekRule = resolveWeekRule(entry.weekRule) ?: WeekRule.ALL
    val customWeeks = parseWeekList(entry.customWeekList) ?: return false
    val skippedWeeks = parseWeekList(entry.skipWeekList) ?: return false
    if (weekIndex in skippedWeeks) return false

    return when (weekRule) {
        WeekRule.ALL -> true
        WeekRule.ODD -> weekIndex % 2 == 1
        WeekRule.EVEN -> weekIndex % 2 == 0
        WeekRule.CUSTOM -> weekIndex in customWeeks
    }
}

/**
 * 生成首次启动演示内容的示例课程条目。
 *
 * @return 三个示例课程条目的列表
 */
fun sampleEntries(): List<TimetableEntry> = listOf(
    TimetableEntry.create(
        title = "Advanced Mathematics",
        date = "2026-09-07",
        dayOfWeek = 1,
        startMinutes = 8 * 60,
        endMinutes = 9 * 60 + 35,
        location = "A-201",
        note = "Starts from week 1",
    ),
    TimetableEntry.create(
        title = "English Reading",
        date = "2027-03-10",
        dayOfWeek = 3,
        startMinutes = 10 * 60,
        endMinutes = 11 * 60 + 30,
        location = "B-108",
    ),
    TimetableEntry.create(
        title = "Computer Fundamentals",
        date = "2028-11-24",
        dayOfWeek = 5,
        startMinutes = 13 * 60 + 30,
        endMinutes = 15 * 60,
        location = "Lab 2",
        note = "Bring laptop",
    ),
)
