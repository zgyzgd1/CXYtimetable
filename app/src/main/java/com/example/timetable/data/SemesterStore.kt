package com.example.timetable.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 全局学期配置存储。
 *
 * 提供学期开学日期的统一入口，用于：
 * - 新建/编辑周循环课程时自动填充默认学期开始日期
 * - 计算"当前第几周"
 * - 周视图中显示周次标签
 *
 * 当用户在课程编辑弹窗中设置学期开始日期时，同时更新此全局配置，
 * 使后续新建课程自动继承。
 */
object SemesterStore {
    private const val PREFS_NAME = "semester_prefs"
    private const val KEY_SEMESTER_START_DATE = "semester_start_date"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取已保存的全局学期开学日期。
     * 返回 null 表示用户尚未配置。
     */
    fun getSemesterStartDate(context: Context): LocalDate? {
        val raw = prefs(context).getString(KEY_SEMESTER_START_DATE, null)
        return raw?.let { parseEntryDate(it) }
    }

    /**
     * 保存全局学期开学日期。
     */
    fun setSemesterStartDate(context: Context, date: LocalDate) {
        prefs(context).edit()
            .putString(KEY_SEMESTER_START_DATE, date.toString())
            .apply()
    }

    /**
     * 清除全局学期开学日期。
     */
    fun clearSemesterStartDate(context: Context) {
        prefs(context).edit()
            .remove(KEY_SEMESTER_START_DATE)
            .apply()
    }

    /**
     * 计算当前是第几周（基于全局学期开始日期）。
     * 返回 null 表示学期未配置或日期早于学期开始。
     */
    fun getCurrentWeekNumber(context: Context, today: LocalDate = LocalDate.now()): Int? {
        val semesterStart = getSemesterStartDate(context) ?: return null
        return weekIndexFromSemesterStart(semesterStart, today)?.takeIf { it >= 1 }
    }

    /**
     * 获取全局学期开学日期的周一（学期起始周的开始）。
     */
    fun getSemesterWeekStart(context: Context): LocalDate? {
        val semesterStart = getSemesterStartDate(context) ?: return null
        return semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
