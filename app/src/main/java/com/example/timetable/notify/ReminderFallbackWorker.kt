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
 * WorkManager 兜底提醒同步 Worker。
 *
 * 作用：当精确闹钟权限被关闭、系统回收 AlarmManager 闹钟、
 * 或系统 Doze 模式延迟了非精确闹钟时，此 Worker 作为"安全网"
 * 定期检查并重新同步提醒计划。
 *
 * 设计约束：
 * - 不替代 AlarmManager 精确提醒路径，只做兜底补漏。
 * - 使用 PeriodicWork（间隔 15 分钟，WorkManager 最小粒度），
 *   确保即使精确闹钟全部失效，提醒延迟也不超过 15 分钟。
 * - Worker 执行时会检查是否有即将到来的课程提醒被遗漏，
 *   如有则立即触发提醒同步。
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
         * 确保兜底 Worker 已注册。
         *
         * 使用 KEEP 策略：如果已存在同名周期任务则保留，
         * 避免频繁取消和重建。
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
         * 取消兜底 Worker。仅在特殊场景使用（如用户关闭所有提醒）。
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
