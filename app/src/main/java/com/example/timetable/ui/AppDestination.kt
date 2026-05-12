package com.example.timetable.ui

enum class AppDestination {
    DAY,
    WEEK,
    SETTINGS,
    JW_IMPORT;

    companion object {
        fun fromSavedName(name: String?): AppDestination {
            if (name.isNullOrBlank()) return DAY
            return entries.firstOrNull { it.name == name } ?: DAY
        }

        fun fromSavedStateValue(value: Any?): AppDestination {
            return when (value) {
                is String -> fromSavedName(value)
                is Boolean -> if (value) WEEK else DAY
                is AppDestination -> value
                else -> DAY
            }
        }
    }
}
