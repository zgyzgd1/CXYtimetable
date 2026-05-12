package com.example.timetable.jw

enum class JwWebMode {
    DESKTOP,
    MOBILE;

    companion object {
        fun fromSavedName(name: String?): JwWebMode {
            if (name.isNullOrBlank()) return DESKTOP
            return entries.firstOrNull { it.name == name } ?: DESKTOP
        }
    }
}

object JwUserAgent {
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun forMode(mode: JwWebMode, defaultUserAgent: String): String {
        return when (mode) {
            JwWebMode.DESKTOP -> DESKTOP_USER_AGENT
            JwWebMode.MOBILE -> defaultUserAgent
        }
    }
}
