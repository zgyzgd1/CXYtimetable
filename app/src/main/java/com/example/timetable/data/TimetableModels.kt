package com.example.timetable.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 课程表条目数据类
 * 表示单个课程或事件的基本信息
 *
 * @property id 唯一标识符，自动生成 UUID
 * @property title 课程标题
 * @property date 课程日期（yyyy-MM-dd）
 * @property dayOfWeek 星期几（1-7，分别代表周一到周日）
 * @property startMinutes 开始时间（从当天0点起的分钟数）
 * @property endMinutes 结束时间（从当天0点起的分钟数）
 * @property location 上课地点
 * @property note 备注信息
 */
data class TimetableEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val location: String = "",
    val note: String = "",
) {
    init {
        require(parseEntryDate(date) != null)
        // 验证数据的合法性
        require(dayOfWeek in 1..7)
        require(startMinutes in 0 until 24 * 60)
        require(endMinutes in 1..24 * 60)
        require(startMinutes < endMinutes)
    }
}

/**
 * 星期选项数据类
 * 用于 UI 显示星期的完整标签和缩写
 *
 * @property value 星期数值（1-7）
 * @property label 完整标签（如"周一"）
 * @property shortLabel 缩写标签（如"一"）
 */
data class DayOption(
    val value: Int,
    val label: String,
    val shortLabel: String,
)

// 定义一周七天的选项列表
val WeekdayOptions = listOf(
    DayOption(1, "周一", "一"),
    DayOption(2, "周二", "二"),
    DayOption(3, "周三", "三"),
    DayOption(4, "周四", "四"),
    DayOption(5, "周五", "五"),
    DayOption(6, "周六", "六"),
    DayOption(7, "周日", "日"),
)

/**
 * 将分钟数格式化为时间字符串（HH:mm 格式）
 *
 * @param minutes 从0点开始的分钟数
 * @return 格式化后的时间字符串，如 "08:30"
 */
fun formatMinutes(minutes: Int): String {
    val safeMinutes = minutes.coerceIn(0, 24 * 60)
    if (safeMinutes == 24 * 60) return "24:00"
    return "%02d:%02d".format(safeMinutes / 60, safeMinutes % 60)
}

/**
 * 解析时间字符串为分钟数
 *
 * @param text 时间字符串，格式为 "HH:mm"
 * @return 解析成功返回分钟数，失败返回 null
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
 * 根据星期数值获取完整的中文标签
 *
 * @param dayOfWeek 星期数值（1-7）
 * @return 中文标签，如"周一"，未找到返回空字符串
 */
fun dayLabel(dayOfWeek: Int): String {
    return WeekdayOptions.firstOrNull { it.value == dayOfWeek }?.label ?: ""
}

/**
 * 根据星期数值获取缩写的中文标签
 *
 * @param dayOfWeek 星期数值（1-7）
 * @return 缩写标签，如"一"，未找到返回空字符串
 */
fun dayShortLabel(dayOfWeek: Int): String {
    return WeekdayOptions.firstOrNull { it.value == dayOfWeek }?.shortLabel ?: ""
}

private val entryDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val minSupportedDate = LocalDate.of(1970, 1, 1)
private val maxSupportedDate = LocalDate.of(2100, 12, 31)

fun parseEntryDate(text: String): LocalDate? {
    val parsed = runCatching { LocalDate.parse(text.trim(), entryDateFormatter) }.getOrNull() ?: return null
    return parsed.takeIf { it in minSupportedDate..maxSupportedDate }
}

fun formatDateLabel(date: String): String {
    val parsed = parseEntryDate(date) ?: return date
    return "%d-%02d-%02d %s".format(parsed.year, parsed.monthValue, parsed.dayOfMonth, dayLabel(parsed.dayOfWeek.value))
}

fun defaultDateForWeekday(dayOfWeek: Int): String {
    val safeDay = dayOfWeek.coerceIn(1, 7)
    val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return monday.plusDays((safeDay - 1).toLong()).toString()
}

/**
 * 生成示例课程数据
 * 用于应用首次启动时展示示例内容
 *
 * @return 包含三个示例课程的列表
 */
fun sampleEntries(): List<TimetableEntry> = listOf(
    TimetableEntry(
        title = "高等数学",
        date = "2026-09-07",
        dayOfWeek = 1,
        startMinutes = 8 * 60,
        endMinutes = 9 * 60 + 35,
        location = "A-201",
        note = "第一周开始",
    ),
    TimetableEntry(
        title = "英语阅读",
        date = "2027-03-10",
        dayOfWeek = 3,
        startMinutes = 10 * 60,
        endMinutes = 11 * 60 + 30,
        location = "B-108",
    ),
    TimetableEntry(
        title = "计算机基础",
        date = "2028-11-24",
        dayOfWeek = 5,
        startMinutes = 13 * 60 + 30,
        endMinutes = 15 * 60,
        location = "机房 2",
        note = "带笔记本",
    ),
)
