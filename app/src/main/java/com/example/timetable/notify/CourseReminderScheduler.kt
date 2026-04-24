package com.example.timetable.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.nextOccurrenceDate
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.resolveRecurrenceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray

object CourseReminderScheduler {
    const val CHANNEL_ID = "course_reminder_channel"
    const val CHANNEL_NAME = "课程提醒"

    const val EXTRA_REQUEST_CODE = "extra_request_code"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_LOCATION = "extra_location"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_START_MINUTES = "extra_start_minutes"
    const val EXTRA_REMINDER_MINUTES = "extra_reminder_minutes"

    private const val ACTION_COURSE_REMINDER = "com.example.timetable.ACTION_COURSE_REMINDER"
    private const val PREFS_NAME = "course_reminder_prefs"
    private const val KEY_CODES = "scheduled_codes"
    private const val KEY_SCHEDULE_SIGNATURES = "scheduled_signatures"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"
    private const val KEY_REMINDER_MINUTES_SET = "reminder_minutes_set"
    private const val DEFAULT_REMINDER_MINUTES = 20
    private const val MIN_REMINDER_MINUTES = 1
    private const val MAX_REMINDER_MINUTES = 180
    private const val MAX_REMINDER_SELECTION_COUNT = 5
    private val reminderOptions = listOf(5, 10, 20, 30)
    private val resyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val systemZone: ZoneId
        get() = ZoneId.systemDefault()

    internal data class SchedulePlan(
        val newSchedules: Map<Int, ScheduledReminder>,
        val codesToCancel: Set<Int>,
        val schedulesToSchedule: Map<Int, ScheduledReminder>,
    )

    internal data class ScheduledReminder(
        val triggerAtMillis: Long,
        val entry: TimetableEntry,
        val occurrenceDate: LocalDate,
        val reminderMinutes: Int,
    )

    @Synchronized
    fun sync(context: Context, entries: List<TimetableEntry>) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val reminderMinutes = getReminderMinutesSet(appContext)

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldCodes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val oldSignatures = prefs.getStringSet(KEY_SCHEDULE_SIGNATURES, emptySet())
            .orEmpty()
            .mapNotNull(::decodeScheduledSignature)
            .toMap()

        val plan = buildSchedulePlan(
            entries = entries,
            reminderMinutesOptions = reminderMinutes,
            nowMillis = System.currentTimeMillis(),
            oldCodes = oldCodes,
            oldSignatures = oldSignatures,
        )

        plan.codesToCancel.forEach { code ->
            cancelAlarm(appContext, alarmManager, code)
        }

        plan.schedulesToSchedule.forEach { (requestCode, scheduled) ->
            val pendingIntent = createPendingIntent(
                context = appContext,
                entry = scheduled.entry,
                occurrenceDate = scheduled.occurrenceDate,
                reminderMinutes = scheduled.reminderMinutes,
                requestCode = requestCode,
                includeNoCreateFlag = false,
            ) ?: return@forEach

            scheduleAlarm(alarmManager, scheduled.triggerAtMillis, pendingIntent)
        }

        prefs.edit()
            .putStringSet(KEY_CODES, plan.newSchedules.keys.map { it.toString() }.toSet())
            .putStringSet(KEY_SCHEDULE_SIGNATURES, encodeScheduledSignatures(plan.newSchedules))
            .apply()
    }

    internal fun buildSchedulePlan(
        entries: List<TimetableEntry>,
        reminderMinutes: Int,
        nowMillis: Long,
        oldCodes: Set<Int>,
        oldSignatures: Map<Int, String> = emptyMap(),
    ): SchedulePlan {
        return buildSchedulePlan(
            entries = entries,
            reminderMinutesOptions = listOf(reminderMinutes),
            nowMillis = nowMillis,
            oldCodes = oldCodes,
            oldSignatures = oldSignatures,
        )
    }

    internal fun buildSchedulePlan(
        entries: List<TimetableEntry>,
        reminderMinutesOptions: List<Int>,
        nowMillis: Long,
        oldCodes: Set<Int>,
        oldSignatures: Map<Int, String> = emptyMap(),
    ): SchedulePlan {
        val normalizedReminderMinutes = normalizeReminderMinutes(reminderMinutesOptions)
        if (normalizedReminderMinutes.isEmpty()) {
            return SchedulePlan(
                newSchedules = emptyMap(),
                codesToCancel = oldCodes,
                schedulesToSchedule = emptyMap(),
            )
        }

        val newSchedules = mutableMapOf<Int, ScheduledReminder>()
        var nextTriggerAtMillis: Long? = null
        val nextEntries = mutableListOf<ScheduledReminder>()
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(systemZone).toLocalDate()

        // Optimization: pre-sort entries so we scan near-future entries first.
        // For non-recurring entries we use their date; for recurring ones we use
        // today (they repeat and might fire today/tomorrow).
        val maxLeadMinutes = normalizedReminderMinutes.maxOrNull() ?: 0
        val sortedEntries = entries.sortedBy { entry ->
            val recurrence = resolveRecurrenceType(entry.recurrenceType)
            if (recurrence == null || recurrence == RecurrenceType.NONE) {
                parseEntryDate(entry.date)?.toEpochDay() ?: Long.MAX_VALUE
            } else {
                // Recurring entries might fire as early as today
                nowDate.toEpochDay()
            }
        }

        sortedEntries.forEach { entry ->
            // Early exit: for non-recurring entries, if entry date is beyond the
            // current best trigger + maxLead buffer, skip it entirely.
            val currentBest = nextTriggerAtMillis
            if (currentBest != null) {
                val recurrence = resolveRecurrenceType(entry.recurrenceType)
                if (recurrence == null || recurrence == RecurrenceType.NONE) {
                    val entryDate = parseEntryDate(entry.date)
                    if (entryDate != null) {
                        val earliestPossibleTrigger = entryDate.atStartOfDay(systemZone)
                            .minusMinutes(maxLeadMinutes.toLong())
                            .toInstant()
                            .toEpochMilli()
                        if (earliestPossibleTrigger > currentBest) {
                            return@forEach  // This and subsequent non-recurring entries are too far out
                        }
                    }
                }
            }

            normalizedReminderMinutes.forEach { reminderMinutes ->
                val scheduled = computeNextReminder(entry, reminderMinutes, nowMillis, nowDate) ?: return@forEach
                val triggerAtMillis = scheduled.triggerAtMillis
                if (triggerAtMillis <= nowMillis) return@forEach

                val currentNextTrigger = nextTriggerAtMillis
                if (currentNextTrigger == null || triggerAtMillis < currentNextTrigger) {
                    nextTriggerAtMillis = triggerAtMillis
                    nextEntries.clear()
                    nextEntries.add(scheduled)
                } else if (triggerAtMillis == currentNextTrigger) {
                    nextEntries.add(scheduled)
                }
            }
        }

        nextTriggerAtMillis?.let { scheduledTriggerAtMillis ->
            nextEntries.forEach { scheduled ->
                val requestCode = requestCodeFor(scheduled.entry, scheduled.reminderMinutes)
                newSchedules[requestCode] = scheduled.copy(triggerAtMillis = scheduledTriggerAtMillis)
            }
        }

        val newCodes = newSchedules.keys
        val schedulesToSchedule = newSchedules.filter { (requestCode, scheduled) ->
            oldSignatures[requestCode] != scheduleSignature(scheduled)
        }
        return SchedulePlan(
            newSchedules = newSchedules,
            codesToCancel = oldCodes - newCodes,
            schedulesToSchedule = schedulesToSchedule,
        )
    }

    fun getReminderMinutesSet(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedSet = prefs.getStringSet(KEY_REMINDER_MINUTES_SET, null)
            ?.mapNotNull { value -> value.toIntOrNull() }
            .orEmpty()
        val normalizedStored = normalizeReminderMinutes(storedSet)
        if (normalizedStored.isNotEmpty()) return normalizedStored

        val storedLegacy = prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
        return normalizeReminderMinutes(listOf(storedLegacy)).ifEmpty { defaultReminderMinutesSet() }
    }

    fun getReminderMinutes(context: Context): Int {
        return getReminderMinutesSet(context).firstOrNull() ?: DEFAULT_REMINDER_MINUTES
    }

    fun setReminderMinutes(context: Context, minutes: Int) {
        setReminderMinutes(context, listOf(minutes))
    }

    fun setReminderMinutes(context: Context, minutes: Iterable<Int>) {
        val normalized = normalizeReminderMinutes(minutes)
        if (normalized.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_REMINDER_MINUTES, normalized.first())
            .putStringSet(KEY_REMINDER_MINUTES_SET, normalized.map(Int::toString).toSet())
            .apply()
    }

    fun reminderMinuteOptions(): List<Int> = reminderOptions

    fun isReminderMinutesValid(minutes: Int): Boolean = minutes in MIN_REMINDER_MINUTES..MAX_REMINDER_MINUTES

    fun defaultReminderMinutes(): Int = DEFAULT_REMINDER_MINUTES

    fun defaultReminderMinutesSet(): List<Int> = listOf(DEFAULT_REMINDER_MINUTES)

    fun maxReminderSelectionCount(): Int = MAX_REMINDER_SELECTION_COUNT

    internal fun normalizeReminderMinutes(reminderMinutes: Iterable<Int>): List<Int> {
        return reminderMinutes
            .filter(::isReminderMinutesValid)
            .distinct()
            .sorted()
            .take(MAX_REMINDER_SELECTION_COUNT)
    }

    fun formatReminderSelection(reminderMinutes: Iterable<Int>): String {
        val normalized = normalizeReminderMinutes(reminderMinutes).ifEmpty { defaultReminderMinutesSet() }
        return normalized.joinToString("、") { "$it 分钟" }
    }

    fun formatReminderChipLabel(reminderMinutes: Iterable<Int>): String {
        val normalized = normalizeReminderMinutes(reminderMinutes).ifEmpty { defaultReminderMinutesSet() }
        return when (normalized.size) {
            1 -> "${normalized.first()}m"
            2 -> normalized.joinToString("/") { "${it}m" }
            else -> "${normalized.size}档"
        }
    }

    fun resyncFromStorage(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        resyncScope.launch {
            try {
                val entries = com.example.timetable.data.TimetableRepository.getEntriesNow(appContext)
                sync(appContext, entries)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun notificationsEnabled(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun exactAlarmPermissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (!exactAlarmPermissionRequired()) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun buildExactAlarmSettingsIntent(context: Context): Intent? {
        if (!exactAlarmPermissionRequired()) return null
        val packageUri = Uri.parse("package:${context.packageName}")
        val packageManager = context.packageManager
        val requestExactAlarmIntent = try {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } catch (_: ActivityNotFoundException) {
            null
        }
        if (requestExactAlarmIntent?.resolveActivity(packageManager) != null) {
            return requestExactAlarmIntent
        }

        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        return appDetailsIntent.takeIf { it.resolveActivity(packageManager) != null }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
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

    private fun computeNextReminder(
        entry: TimetableEntry,
        reminderMinutes: Int,
        nowMillis: Long,
        nowDate: LocalDate,
    ): ScheduledReminder? {
        val firstDate = nextOccurrenceDate(entry, nowDate) ?: return null
        val firstTriggerAtMillis = computeTriggerAtMillis(firstDate, entry.startMinutes, reminderMinutes) ?: return null
        if (firstTriggerAtMillis > nowMillis) {
            return ScheduledReminder(
                triggerAtMillis = firstTriggerAtMillis,
                entry = entry,
                occurrenceDate = firstDate,
                reminderMinutes = reminderMinutes,
            )
        }

        val fallbackDate = nextOccurrenceDate(entry, firstDate.plusDays(1)) ?: return null
        val fallbackTriggerAtMillis = computeTriggerAtMillis(fallbackDate, entry.startMinutes, reminderMinutes) ?: return null
        return ScheduledReminder(
            triggerAtMillis = fallbackTriggerAtMillis,
            entry = entry,
            occurrenceDate = fallbackDate,
            reminderMinutes = reminderMinutes,
        ).takeIf { it.triggerAtMillis > nowMillis }
    }

    private fun computeTriggerAtMillis(
        occurrenceDate: LocalDate,
        startMinutes: Int,
        reminderMinutes: Int,
    ): Long? {
        return runCatching {
            val start = LocalTime.of(startMinutes / 60, startMinutes % 60)
            occurrenceDate.atTime(start)
                .atZone(systemZone)
                .minusMinutes(reminderMinutes.toLong())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun requestCodeFor(entry: TimetableEntry, reminderMinutes: Int): Int {
        return "${entry.id}|${entry.date}|${entry.startMinutes}|$reminderMinutes".hashCode()
    }

    internal fun scheduleSignature(scheduled: ScheduledReminder): String {
        return JSONArray(
            listOf(
                scheduled.triggerAtMillis,
                scheduled.occurrenceDate.toString(),
                scheduled.reminderMinutes,
                scheduled.entry.id,
                scheduled.entry.title,
                scheduled.entry.location,
                scheduled.entry.date,
                scheduled.entry.startMinutes,
            ),
        ).toString()
    }

    private fun encodeScheduledSignatures(schedules: Map<Int, ScheduledReminder>): Set<String> {
        return schedules.map { (requestCode, scheduled) ->
            "$requestCode=${scheduleSignature(scheduled)}"
        }.toSet()
    }

    private fun decodeScheduledSignature(encoded: String): Pair<Int, String>? {
        val separatorIndex = encoded.indexOf('=')
        if (separatorIndex <= 0 || separatorIndex == encoded.lastIndex) return null
        val requestCode = encoded.substring(0, separatorIndex).toIntOrNull() ?: return null
        val signature = encoded.substring(separatorIndex + 1)
        if (signature.isBlank()) return null
        return requestCode to signature
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
        occurrenceDate: LocalDate,
        reminderMinutes: Int,
        requestCode: Int,
        includeNoCreateFlag: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_TITLE, entry.title)
            putExtra(EXTRA_LOCATION, entry.location)
            putExtra(EXTRA_DATE, occurrenceDate.toString())
            putExtra(EXTRA_START_MINUTES, entry.startMinutes)
            putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
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
