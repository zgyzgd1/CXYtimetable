package com.example.timetable.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Global semester configuration store.
 *
 * Provides a unified entry point for the semester start date, used for:
 * - Auto-filling the default semester start date when creating/editing weekly courses
 * - Computing "current week number"
 * - Displaying week labels in the week view
 *
 * When the user sets a semester start date in the course edit dialog,
 * this global config is also updated so subsequent new courses inherit it.
 */
object SemesterStore {
    private const val PREFS_NAME = "semester_prefs"
    private const val KEY_SEMESTER_START_DATE = "semester_start_date"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the saved global semester start date, or null if not configured.
     */
    fun getSemesterStartDate(context: Context): LocalDate? {
        val raw = prefs(context).getString(KEY_SEMESTER_START_DATE, null)
        return raw?.let { parseEntryDate(it) }
    }

    /**
     * Saves the global semester start date.
     */
    fun setSemesterStartDate(context: Context, date: LocalDate) {
        prefs(context).edit()
            .putString(KEY_SEMESTER_START_DATE, date.toString())
            .apply()
    }

    /**
     * Clears the global semester start date.
     */
    fun clearSemesterStartDate(context: Context) {
        prefs(context).edit()
            .remove(KEY_SEMESTER_START_DATE)
            .apply()
    }

    /**
     * Computes the current week number based on the global semester start date.
     * Returns null if the semester is not configured or the date is before semester start.
     */
    fun getCurrentWeekNumber(context: Context, today: LocalDate = LocalDate.now()): Int? {
        val semesterStart = getSemesterStartDate(context) ?: return null
        return weekIndexFromSemesterStart(semesterStart, today)?.takeIf { it >= 1 }
    }

    /**
     * Returns the Monday of the week containing the global semester start date (start of semester week).
     */
    fun getSemesterWeekStart(context: Context): LocalDate? {
        val semesterStart = getSemesterStartDate(context) ?: return null
        return semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
