package com.example.timetable.ui

data class AppLaunchTarget(
    val selectedDate: String? = null,
    val destination: AppDestination = AppDestination.DAY,
    val openAddCourse: Boolean = false,
    val launchRequestId: Long = 0L,
)
