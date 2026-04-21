package com.example.timetable.data

import android.content.Context

object AppearanceStore {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_WEEK_CARD_ALPHA = "week_card_alpha"
    private const val KEY_WEEK_CARD_HUE = "week_card_hue"
    private const val KEY_WEEK_TIME_SLOTS = "week_time_slots"
    private const val DEFAULT_WEEK_CARD_ALPHA = 0.90f
    private const val DEFAULT_WEEK_CARD_HUE = 0f

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWeekCardAlpha(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_ALPHA, DEFAULT_WEEK_CARD_ALPHA)
            .coerceIn(0.35f, 1.0f)
    }

    fun setWeekCardAlpha(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_ALPHA, value.coerceIn(0.35f, 1.0f)).apply()
    }

    fun getWeekCardHue(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_HUE, DEFAULT_WEEK_CARD_HUE)
            .coerceIn(0f, 360f)
    }

    fun setWeekCardHue(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_HUE, value.coerceIn(0f, 360f)).apply()
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

    fun defaultWeekTimeSlots(): List<WeekTimeSlot> {
        val slots = mutableListOf<WeekTimeSlot>()
        var start = 8 * 60
        repeat(20) {
            val end = (start + 40).coerceAtMost(24 * 60)
            if (start < end) {
                slots += WeekTimeSlot(start, end)
            }
            start += 45
        }
        return slots
    }
}

data class WeekTimeSlot(
    val startMinutes: Int,
    val endMinutes: Int,
)
