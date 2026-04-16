package com.example.timetable.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableShareCodec
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject

object CourseReminderScheduler {
    const val CHANNEL_ID = "course_reminder_channel"
    const val CHANNEL_NAME = "课程提醒"

    const val EXTRA_REQUEST_CODE = "extra_request_code"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_LOCATION = "extra_location"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_START_MINUTES = "extra_start_minutes"

    private const val ACTION_COURSE_REMINDER = "com.example.timetable.ACTION_COURSE_REMINDER"
    private const val PREFS_NAME = "course_reminder_prefs"
    private const val KEY_CODES = "scheduled_codes"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"
    private const val STORAGE_FILE_NAME = "timetable_entries.json"
    private const val SHARE_PAYLOAD_VERSION = 1
    private const val DEFAULT_REMINDER_MINUTES = 20
    private val reminderOptions = listOf(5, 10, 20, 30)

    private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun sync(context: Context, entries: List<TimetableEntry>) {
        ensureNotificationChannel(context)
        val reminderMinutes = getReminderMinutes(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldCodes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

        val now = System.currentTimeMillis()
        val newSchedules = mutableMapOf<Int, Pair<Long, TimetableEntry>>()

        entries.forEach { entry ->
            val triggerAtMillis = computeTriggerAtMillis(entry, reminderMinutes) ?: return@forEach
            if (triggerAtMillis <= now) return@forEach

            val requestCode = requestCodeFor(entry, reminderMinutes)
            newSchedules[requestCode] = triggerAtMillis to entry
        }

        val newCodes = newSchedules.keys
        val codesToCancel = oldCodes - newCodes
        val codesToSchedule = newCodes - oldCodes

        codesToCancel.forEach { code ->
            cancelAlarm(context, alarmManager, code)
        }

        codesToSchedule.forEach { requestCode ->
            val (triggerAtMillis, entry) = newSchedules[requestCode]!!
            val pendingIntent = createPendingIntent(
                context = context,
                entry = entry,
                requestCode = requestCode,
                includeNoCreateFlag = false,
            ) ?: return@forEach

            scheduleAlarm(alarmManager, triggerAtMillis, pendingIntent)
        }

        prefs.edit().putStringSet(KEY_CODES, newCodes.map { it.toString() }.toSet()).apply()
    }

    fun getReminderMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
        return stored.takeIf { it in reminderOptions } ?: DEFAULT_REMINDER_MINUTES
    }

    fun setReminderMinutes(context: Context, minutes: Int) {
        if (minutes !in reminderOptions) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_REMINDER_MINUTES, minutes).apply()
    }

    fun reminderMinuteOptions(): List<Int> = reminderOptions

    fun resyncFromStorage(context: Context) {
        val file = File(context.filesDir, STORAGE_FILE_NAME)
        if (!file.exists()) {
            sync(context, emptyList())
            return
        }

        val payload = runCatching { file.readText() }.getOrNull() ?: return
        val entries = decodeEntriesForResync(payload) ?: return

        sync(context, entries)
    }

    internal fun decodeEntriesForResync(payload: String): List<TimetableEntry>? {
        if (payload.isBlank()) return null

        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (root.optInt("version", -1) != SHARE_PAYLOAD_VERSION) return null

        val entriesNode = root.optJSONArray("entries") ?: return null
        val decoded = TimetableShareCodec.decode(payload)
        if (entriesNode.length() > 0 && decoded.isEmpty()) {
            return null
        }

        return decoded
    }

    fun notificationsEnabled(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "上课前提醒通知"
        }

        manager.createNotificationChannel(channel)
    }

    private fun computeTriggerAtMillis(entry: TimetableEntry, reminderMinutes: Int): Long? {
        return runCatching {
            val date = LocalDate.parse(entry.date)
            val start = LocalTime.of(entry.startMinutes / 60, entry.startMinutes % 60)
            date.atTime(start)
                .atZone(beijingZone)
                .minusMinutes(reminderMinutes.toLong())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun requestCodeFor(entry: TimetableEntry, reminderMinutes: Int): Int {
        return "${entry.id}|${entry.date}|${entry.startMinutes}|$reminderMinutes".hashCode()
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
        }

        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun createPendingIntent(
        context: Context,
        entry: TimetableEntry,
        requestCode: Int,
        includeNoCreateFlag: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_TITLE, entry.title)
            putExtra(EXTRA_LOCATION, entry.location)
            putExtra(EXTRA_DATE, entry.date)
            putExtra(EXTRA_START_MINUTES, entry.startMinutes)
        }

        var flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        if (includeNoCreateFlag) {
            flags = flags or PendingIntent.FLAG_NO_CREATE
        }

        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (hasPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}
