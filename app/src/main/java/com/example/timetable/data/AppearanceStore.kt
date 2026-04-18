package com.example.timetable.data

import android.content.Context
import android.net.Uri

object AppearanceStore {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_BACKGROUND_URI = "background_uri"
    private const val KEY_WEEK_BACKGROUND_URI = "week_background_uri"
    private const val KEY_WEEK_CARD_ALPHA = "week_card_alpha"
    private const val KEY_WEEK_TIME_SLOTS = "week_time_slots"
    private const val DEFAULT_WEEK_CARD_ALPHA = 0.90f

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBackgroundImageUri(context: Context): String? {
        return normalizeStoredUri(prefs(context).getString(KEY_BACKGROUND_URI, null))
    }

    fun setBackgroundImageUri(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_BACKGROUND_URI, uri.toString()).apply()
    }

    fun clearBackgroundImage(context: Context) {
        prefs(context).edit().remove(KEY_BACKGROUND_URI).apply()
    }

    fun getWeekBackgroundImageUri(context: Context): String? {
        return normalizeStoredUri(prefs(context).getString(KEY_WEEK_BACKGROUND_URI, null))
    }

    fun setWeekBackgroundImageUri(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_WEEK_BACKGROUND_URI, uri.toString()).apply()
    }

    fun clearWeekBackgroundImage(context: Context) {
        prefs(context).edit().remove(KEY_WEEK_BACKGROUND_URI).apply()
    }

    fun getWeekCardAlpha(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_ALPHA, DEFAULT_WEEK_CARD_ALPHA)
            .coerceIn(0.35f, 1.0f)
    }

    fun setWeekCardAlpha(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_ALPHA, value.coerceIn(0.35f, 1.0f)).apply()
    }

    fun getWeekTimeSlots(context: Context): List<WeekTimeSlot> {
        val raw = prefs(context).getString(KEY_WEEK_TIME_SLOTS, null).orEmpty()
        val parsed = raw.split(';')
            .mapNotNull { token ->
                val parts = token.split('-')
                if (parts.size != 2) return@mapNotNull null
                val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                WeekTimeSlot(start, end).takeIf { start in 0 until 24 * 60 && end in 1..24 * 60 && start < end }
            }
            .sortedBy { it.startMinutes }
        return if (parsed.isNotEmpty()) parsed else defaultWeekTimeSlots()
    }

    fun setWeekTimeSlots(context: Context, slots: List<WeekTimeSlot>) {
        val normalized = slots
            .map { WeekTimeSlot(it.startMinutes.coerceIn(0, 24 * 60 - 1), it.endMinutes.coerceIn(1, 24 * 60)) }
            .filter { it.startMinutes < it.endMinutes }
            .sortedBy { it.startMinutes }
        val raw = normalized.joinToString(";") { "${it.startMinutes}-${it.endMinutes}" }
        prefs(context).edit().putString(KEY_WEEK_TIME_SLOTS, raw).apply()
    }

    internal fun normalizeStoredUri(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun defaultWeekTimeSlots(): List<WeekTimeSlot> = listOf(
        WeekTimeSlot(8 * 60, 8 * 60 + 45),
        WeekTimeSlot(8 * 60 + 55, 9 * 60 + 40),
        WeekTimeSlot(10 * 60 + 10, 10 * 60 + 55),
        WeekTimeSlot(11 * 60 + 5, 11 * 60 + 50),
        WeekTimeSlot(14 * 60 + 30, 15 * 60 + 15),
        WeekTimeSlot(15 * 60 + 25, 16 * 60 + 10),
        WeekTimeSlot(16 * 60 + 20, 17 * 60 + 5),
        WeekTimeSlot(17 * 60 + 15, 18 * 60),
    )
}

data class WeekTimeSlot(
    val startMinutes: Int,
    val endMinutes: Int,
)
