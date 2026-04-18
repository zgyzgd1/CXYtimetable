package com.example.timetable.data

import android.content.Context
import android.content.Intent
import android.net.Uri

object BackgroundImageStore {
    private const val PREFS_NAME = "background_image_prefs"
    private const val KEY_BACKGROUND_URI = "background_image_uri"

    fun getBackgroundImageUri(context: Context): String? {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKGROUND_URI, null)
        return normalizeStoredUri(stored)
    }

    fun setBackgroundImageUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        val newValue = uri.toString()
        getBackgroundImageUri(appContext)
            ?.takeIf { it != newValue }
            ?.let { previous ->
                runCatching {
                    appContext.contentResolver.releasePersistableUriPermission(
                        Uri.parse(previous),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKGROUND_URI, newValue)
            .apply()
    }

    fun clearBackgroundImage(context: Context) {
        val appContext = context.applicationContext
        getBackgroundImageUri(appContext)?.let { stored ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BACKGROUND_URI)
            .apply()
    }

    internal fun normalizeStoredUri(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
