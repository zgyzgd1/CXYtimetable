package com.example.timetable.data

import android.content.Context
import android.net.Uri

object AppearanceStore {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_BACKGROUND_URI = "background_uri"
    private const val KEY_WEEK_CARD_ALPHA = "week_card_alpha"
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

    fun getWeekCardAlpha(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_ALPHA, DEFAULT_WEEK_CARD_ALPHA)
            .coerceIn(0.35f, 1.0f)
    }

    fun setWeekCardAlpha(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_ALPHA, value.coerceIn(0.35f, 1.0f)).apply()
    }

    internal fun normalizeStoredUri(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}
