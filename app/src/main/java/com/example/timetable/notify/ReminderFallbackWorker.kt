package com.example.timetable.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager fallback reminder sync worker.
 *
 * Role: When exact alarm permission is disabled, the system clears AlarmManager alarms,
 * or Doze mode delays non-exact alarms, this worker acts as a safety net by periodically
 * checking and re-syncing reminder schedules.
 *
 * Design constraints:
 * - Does not replace the AlarmManager exact reminder path; only provides fallback coverage.
 * - Uses PeriodicWork (minimum interval 15 minutes enforced by WorkManager),
 *   ensuring that even if all exact alarms fail, reminder delay does not exceed 15 minutes.
 * - On each run, checks whether any upcoming course reminders were missed,
 *   and triggers immediate reminder sync if so.
 */
class ReminderFallbackWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Fallback reminder sync triggered")
            val entries = com.example.timetable.data.TimetableRepository
                .getEntriesNow(applicationContext)
            CourseReminderScheduler.sync(applicationContext, entries)
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Fallback reminder sync failed", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReminderFallbackWorker"
        private const val UNIQUE_WORK_NAME = "reminder_fallback_sync"

        /**
         * Ensures the fallback worker is scheduled.
         *
         * Uses KEEP policy: keeps existing periodic work if one with the same name exists,
         * avoiding frequent cancel-and-reschedule cycles.
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderFallbackWorker>(
                15, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            Log.d(TAG, "Fallback reminder worker ensured")
        }

        /**
         * Cancels the fallback worker. Use only in special cases (e.g., user disables all reminders).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
