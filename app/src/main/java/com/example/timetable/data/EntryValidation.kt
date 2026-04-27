package com.example.timetable.data

import androidx.annotation.StringRes
import com.example.timetable.R
import java.time.LocalDate

/**
 * 课程条目验证常量。
 */
object EntryConstants {
    const val MAX_TITLE_LENGTH = 64
    const val MAX_LOCATION_LENGTH = 64
    const val MAX_NOTE_LENGTH = 256
    const val SLOT_COUNT_MIN = 1
    const val SLOT_COUNT_MAX = 20
    const val MINUTES_PER_DAY = 24 * 60
}

/**
 * 课程条目验证错误类型。
 *
 * 每个错误关联一个字符串资源 ID，用于显示错误提示。
 */
enum class EntryValidationError(@StringRes val messageResId: Int) {
    EmptyTitle(R.string.error_empty_course_name),
    TitleTooLong(R.string.error_title_too_long),
    InvalidDate(R.string.error_invalid_date),
    InvalidTime(R.string.error_invalid_time),
    InvalidTimeRange(R.string.error_invalid_time_range),
    LocationTooLong(R.string.error_location_too_long),
    NoteTooLong(R.string.error_note_too_long),
    InvalidSemesterDate(R.string.error_invalid_semester_date),
    InvalidCustomWeeks(R.string.error_invalid_custom_weeks),
    InvalidSkipWeeks(R.string.error_invalid_skip_weeks),
    EmptyCustomWeeks(R.string.error_empty_custom_weeks),
    InvalidRecurrence(R.string.val_invalid_recurrence),
    InvalidWeekRule(R.string.val_invalid_week_rule),
    InvalidStartTime(R.string.val_invalid_start),
    InvalidEndTime(R.string.val_invalid_end),
    EndBeforeStart(R.string.val_end_before_start),
    WeekMismatch(R.string.val_week_mismatch),
    NonWeeklyOddEven(R.string.val_non_weekly_odd_even),
    NonWeeklyCustom(R.string.val_non_weekly_custom),
    NonWeeklySkip(R.string.val_non_weekly_skip),
}

/**
 * 课程条目验证器。
 *
 * 统一管理课程条目的验证逻辑，供 UI 层（对话框）和 ViewModel 共用，
 * 避免验证规则重复定义。
 */
object EntryValidator {
    /**
     * 验证已构造的课程条目。
     *
     * 适用于 ViewModel 中对已规范化条目的验证。
     *
     * @param entry 待验证的课程条目
     * @return 验证错误，或 null 表示验证通过
     */
    fun validate(entry: TimetableEntry): EntryValidationError? {
        val recurrence = resolveRecurrenceType(entry.recurrenceType)
            ?: return EntryValidationError.InvalidRecurrence
        val weekRule = resolveWeekRule(entry.weekRule)
            ?: return EntryValidationError.InvalidWeekRule
        val customWeeks = parseWeekList(entry.customWeekList)
            ?: return EntryValidationError.InvalidCustomWeeks
        val skipWeeks = parseWeekList(entry.skipWeekList)
            ?: return EntryValidationError.InvalidSkipWeeks
        val entryDate = parseEntryDate(entry.date)
            ?: return EntryValidationError.InvalidDate
        val semesterStartDate = entry.semesterStartDate
            .takeIf { it.isNotBlank() }
            ?.let { parseEntryDate(it) }

        return when {
            entry.title.isBlank() -> EntryValidationError.EmptyTitle
            entry.title.length > EntryConstants.MAX_TITLE_LENGTH -> EntryValidationError.TitleTooLong
            entry.location.length > EntryConstants.MAX_LOCATION_LENGTH -> EntryValidationError.LocationTooLong
            entry.note.length > EntryConstants.MAX_NOTE_LENGTH -> EntryValidationError.NoteTooLong
            entry.startMinutes !in 0 until EntryConstants.MINUTES_PER_DAY -> EntryValidationError.InvalidStartTime
            entry.endMinutes !in 1..EntryConstants.MINUTES_PER_DAY -> EntryValidationError.InvalidEndTime
            entry.startMinutes >= entry.endMinutes -> EntryValidationError.EndBeforeStart
            recurrence == RecurrenceType.WEEKLY && semesterStartDate == null -> EntryValidationError.InvalidSemesterDate
            recurrence == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isEmpty() -> EntryValidationError.EmptyCustomWeeks
            recurrence == RecurrenceType.WEEKLY && !occursOnDate(entry, entryDate) -> EntryValidationError.WeekMismatch
            recurrence != RecurrenceType.WEEKLY && weekRule != WeekRule.ALL -> EntryValidationError.NonWeeklyOddEven
            recurrence != RecurrenceType.WEEKLY && customWeeks.isNotEmpty() -> EntryValidationError.NonWeeklyCustom
            recurrence != RecurrenceType.WEEKLY && skipWeeks.isNotEmpty() -> EntryValidationError.NonWeeklySkip
            else -> null
        }
    }

    /**
     * 验证对话框中的原始输入数据。
     *
     * 适用于 UI 层在构造 TimetableEntry 之前的验证，
     * 包含解析失败的检查。
     *
     * @param title 标题
     * @param parsedDate 已解析的日期（null 表示解析失败）
     * @param parsedStart 已解析的开始时间（null 表示解析失败）
     * @param parsedEnd 已解析的结束时间（null 表示解析失败）
     * @param location 地点
     * @param note 备注
     * @param recurrenceType 重复类型
     * @param parsedSemesterStart 已解析的学期开始日期（null 表示解析失败或非周循环）
     * @param customWeekList 自定义周次文本
     * @param skipWeekList 跳过周次文本
     * @param weekRule 周规则
     * @return 验证错误，或 null 表示验证通过
     */
    fun validateDraft(
        title: String,
        parsedDate: LocalDate?,
        parsedStart: Int?,
        parsedEnd: Int?,
        location: String,
        note: String,
        recurrenceType: RecurrenceType,
        parsedSemesterStart: LocalDate?,
        customWeekList: String,
        skipWeekList: String,
        weekRule: WeekRule,
    ): EntryValidationError? {
        val customWeeks = parseWeekList(customWeekList)
        val skipWeeks = parseWeekList(skipWeekList)

        return when {
            title.trim().isBlank() -> EntryValidationError.EmptyTitle
            title.trim().length > EntryConstants.MAX_TITLE_LENGTH -> EntryValidationError.TitleTooLong
            parsedDate == null -> EntryValidationError.InvalidDate
            parsedStart == null || parsedEnd == null -> EntryValidationError.InvalidTime
            parsedStart >= parsedEnd -> EntryValidationError.InvalidTimeRange
            location.trim().length > EntryConstants.MAX_LOCATION_LENGTH -> EntryValidationError.LocationTooLong
            note.trim().length > EntryConstants.MAX_NOTE_LENGTH -> EntryValidationError.NoteTooLong
            recurrenceType == RecurrenceType.WEEKLY && parsedSemesterStart == null -> EntryValidationError.InvalidSemesterDate
            recurrenceType == RecurrenceType.WEEKLY && customWeeks == null -> EntryValidationError.InvalidCustomWeeks
            recurrenceType == RecurrenceType.WEEKLY && skipWeeks == null -> EntryValidationError.InvalidSkipWeeks
            recurrenceType == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isNullOrEmpty() -> EntryValidationError.EmptyCustomWeeks
            else -> null
        }
    }
}
