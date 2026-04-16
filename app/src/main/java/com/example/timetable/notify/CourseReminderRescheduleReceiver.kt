package com.example.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CourseReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> CourseReminderScheduler.resyncFromStorage(context)
        }
    }
}
