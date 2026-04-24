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
import com.example.timetable.R
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.nextOccurrenceDate
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.resolveRecurrenceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray

/**
 * 通过 AlarmManager 调度课程提醒闹钟。
 *
 * ## 设计：一次性向前调度
 *
 * 此调度器只调度**下一个**即将到来的提醒（具有最早触发时间的那个），而不是所有未来的提醒。
 * 当该提醒触发时，[CourseReminderReceiver] 会重新调用 [sync] 来调度*下一个*提醒，形成一个链条。
 * 这样可以避免预先调度数百个闹钟，保持内存和 AlarmManager 的使用最小化。
 *
 * 弹性设计：
 * - [ReminderFallbackWorker] 通过 WorkManager 每约 15 分钟运行一次，以捕获丢失的闹钟
 *   （例如在强制停止、进程死亡或精确闹钟权限被拒绝后）。
 * - [CourseReminderRescheduleReceiver] 在 BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED 时重新同步，
 *   以从设备重启和时间变更中恢复。
 * - 如果用户拒绝精确闹钟权限（Android 12+），闹钟会回退到通过 [AlarmManager.setAndAllowWhileIdle] 进行非精确窗口调度。
 */
object CourseReminderScheduler {
    const val CHANNEL_ID = "course_reminder_channel"

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
    private val syncMutex = Mutex()

    private val systemZone: ZoneId
        get() = ZoneId.systemDefault()

    /**
     * 调度计划，包含新的调度、需要取消的代码和需要调度的计划。
     *
     * @property newSchedules 新的调度映射，键为请求代码，值为调度的提醒
     * @property codesToCancel 需要取消的闹钟请求代码集合
     * @property schedulesToSchedule 需要调度的提醒映射，键为请求代码，值为调度的提醒
     */
    internal data class SchedulePlan(
        val newSchedules: Map<Int, ScheduledReminder>,
        val codesToCancel: Set<Int>,
        val schedulesToSchedule: Map<Int, ScheduledReminder>,
    )

    /**
     * 已调度的提醒信息。
     *
     * @property triggerAtMillis 触发时间戳（毫秒）
     * @property entry 课程表条目
     * @property occurrenceDate 提醒发生的日期
     * @property reminderMinutes 提醒提前的分钟数
     */
    internal data class ScheduledReminder(
        val triggerAtMillis: Long,
        val entry: TimetableEntry,
        val occurrenceDate: LocalDate,
        val reminderMinutes: Int,
    )

    /**
     * 同步课程提醒闹钟。
     *
     * 此函数会根据提供的课程表条目，计算并调度下一个即将到来的提醒，同时取消不再需要的闹钟。
     *
     * @param context 应用上下文
     * @param entries 课程表条目列表
     */
    suspend fun sync(context: Context, entries: List<TimetableEntry>) = syncMutex.withLock {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val reminderMinutes = getReminderMinutesSet(appContext)

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return@withLock
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

    /**
     * 构建调度计划（单个提醒时间版本）。
     *
     * @param entries 课程表条目列表
     * @param reminderMinutes 提醒提前的分钟数
     * @param nowMillis 当前时间戳（毫秒）
     * @param oldCodes 旧的闹钟请求代码集合
     * @param oldSignatures 旧的调度签名映射
     * @return 构建的调度计划
     */
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

    /**
     * 构建调度计划（多个提醒时间版本）。
     *
     * 此函数会计算下一个即将到来的提醒，并生成相应的调度计划。
     *
     * @param entries 课程表条目列表
     * @param reminderMinutesOptions 提醒提前的分钟数选项列表
     * @param nowMillis 当前时间戳（毫秒）
     * @param oldCodes 旧的闹钟请求代码集合
     * @param oldSignatures 旧的调度签名映射
     * @return 构建的调度计划
     */
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

        // 优化：预先排序条目，以便先扫描近期的条目。
        // 对于非重复条目，使用其日期；对于重复条目，使用今天（它们会重复，可能在今天/明天触发）。
        val maxLeadMinutes = normalizedReminderMinutes.maxOrNull() ?: 0
        val sortedEntries = entries.sortedBy { entry ->
            val recurrence = resolveRecurrenceType(entry.recurrenceType)
            if (recurrence == null || recurrence == RecurrenceType.NONE) {
                parseEntryDate(entry.date)?.toEpochDay() ?: Long.MAX_VALUE
            } else {
                // 重复条目可能最早在今天触发
                nowDate.toEpochDay()
            }
        }

        sortedEntries.forEach { entry ->
            // 提前退出：对于非重复条目，如果条目日期超出当前最佳触发时间 + 最大提前缓冲，完全跳过。
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
                            return@forEach  // 此条目和后续非重复条目太远，跳过
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
                val existing = newSchedules[requestCode]
                if (existing != null && existing.entry.id != scheduled.entry.id) {
                    // 检测到哈希冲突 — 通过添加盐值来消除歧义。
                    // 使用 Objects.hash 时这种情况极不可能发生，但我们仍需防范。
                    val saltedCode = requestCode xor scheduled.entry.id.hashCode()
                    newSchedules[saltedCode] = scheduled.copy(triggerAtMillis = scheduledTriggerAtMillis)
                } else {
                    newSchedules[requestCode] = scheduled.copy(triggerAtMillis = scheduledTriggerAtMillis)
                }
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

    /**
     * 获取已设置的提醒分钟数列表。
     *
     * 此函数会从共享首选项中读取已设置的提醒分钟数，并进行标准化处理。
     * 如果没有设置，会返回默认值。
     *
     * @param context 应用上下文
     * @return 标准化后的提醒分钟数列表
     */
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

    /**
     * 获取提醒分钟数（单个值）。
     *
     * 此函数返回已设置的提醒分钟数列表中的第一个值，如果没有设置则返回默认值。
     *
     * @param context 应用上下文
     * @return 提醒分钟数
     */
    fun getReminderMinutes(context: Context): Int {
        return getReminderMinutesSet(context).firstOrNull() ?: DEFAULT_REMINDER_MINUTES
    }

    /**
     * 设置提醒分钟数（单个值）。
     *
     * @param context 应用上下文
     * @param minutes 提醒分钟数
     */
    fun setReminderMinutes(context: Context, minutes: Int) {
        setReminderMinutes(context, listOf(minutes))
    }

    /**
     * 设置提醒分钟数（多个值）。
     *
     * 此函数会对输入的分钟数进行标准化处理，然后保存到共享首选项中。
     *
     * @param context 应用上下文
     * @param minutes 提醒分钟数集合
     */
    fun setReminderMinutes(context: Context, minutes: Iterable<Int>) {
        val normalized = normalizeReminderMinutes(minutes)
        if (normalized.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_REMINDER_MINUTES, normalized.first())
            .putStringSet(KEY_REMINDER_MINUTES_SET, normalized.map(Int::toString).toSet())
            .apply()
    }

    /**
     * 获取提醒分钟数选项列表。
     *
     * @return 提醒分钟数选项列表
     */
    fun reminderMinuteOptions(): List<Int> = reminderOptions

    /**
     * 检查提醒分钟数是否有效。
     *
     * @param minutes 提醒分钟数
     * @return 是否有效
     */
    fun isReminderMinutesValid(minutes: Int): Boolean = minutes in MIN_REMINDER_MINUTES..MAX_REMINDER_MINUTES

    /**
     * 获取默认提醒分钟数。
     *
     * @return 默认提醒分钟数
     */
    fun defaultReminderMinutes(): Int = DEFAULT_REMINDER_MINUTES

    /**
     * 获取默认提醒分钟数集合。
     *
     * @return 默认提醒分钟数集合
     */
    fun defaultReminderMinutesSet(): List<Int> = listOf(DEFAULT_REMINDER_MINUTES)

    /**
     * 获取最大提醒选择数量。
     *
     * @return 最大提醒选择数量
     */
    fun maxReminderSelectionCount(): Int = MAX_REMINDER_SELECTION_COUNT

    /**
     * 标准化提醒分钟数列表。
     *
     * 此函数会过滤掉无效的分钟数，去重，排序，并限制最大数量。
     *
     * @param reminderMinutes 提醒分钟数集合
     * @return 标准化后的提醒分钟数列表
     */
    internal fun normalizeReminderMinutes(reminderMinutes: Iterable<Int>): List<Int> {
        return reminderMinutes
            .filter(::isReminderMinutesValid)
            .distinct()
            .sorted()
            .take(MAX_REMINDER_SELECTION_COUNT)
    }

    /**
     * 格式化提醒选择。
     *
     * 此函数会将提醒分钟数列表格式化为可读的字符串。
     *
     * @param reminderMinutes 提醒分钟数集合
     * @return 格式化后的字符串
     */
    fun formatReminderSelection(reminderMinutes: Iterable<Int>): String {
        val normalized = normalizeReminderMinutes(reminderMinutes).ifEmpty { defaultReminderMinutesSet() }
        return normalized.joinToString(", ") { "$it min" }
    }

    /**
     * 格式化提醒芯片标签。
     *
     * 此函数会根据提醒分钟数的数量，返回不同格式的芯片标签。
     *
     * @param reminderMinutes 提醒分钟数集合
     * @return 格式化后的芯片标签
     */
    fun formatReminderChipLabel(reminderMinutes: Iterable<Int>): String {
        val normalized = normalizeReminderMinutes(reminderMinutes).ifEmpty { defaultReminderMinutesSet() }
        return when (normalized.size) {
            1 -> "${normalized.first()}m"
            2 -> normalized.joinToString("/") { "${it}m" }
            else -> "${normalized.size} options"
        }
    }

    /**
     * 从存储中重新同步提醒。
     *
     * 此函数会从存储中获取课程表条目，然后调用 sync 函数重新同步提醒。
     *
     * @param context 应用上下文
     * @param onComplete 完成回调
     */
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

    /**
     * 检查通知是否启用。
     *
     * 此函数会检查应用是否具有发送通知的权限。
     *
     * @param context 应用上下文
     * @return 通知是否启用
     */
    fun notificationsEnabled(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否需要精确闹钟权限。
     *
     * 此函数会检查当前 Android 版本是否需要精确闹钟权限。
     *
     * @return 是否需要精确闹钟权限
     */
    fun exactAlarmPermissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * 检查是否可以调度精确闹钟。
     *
     * 此函数会检查应用是否具有调度精确闹钟的权限。
     *
     * @param context 应用上下文
     * @return 是否可以调度精确闹钟
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (!exactAlarmPermissionRequired()) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * 构建精确闹钟设置意图。
     *
     * 此函数会构建一个意图，用于打开精确闹钟权限设置页面。
     *
     * @param context 应用上下文
     * @return 精确闹钟设置意图，或 null 如果不需要权限
     */
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

    /**
     * 确保通知渠道存在。
     *
     * 此函数会检查并创建通知渠道（如果不存在）。
     *
     * @param context 应用上下文
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notify_channel_description)
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * 计算下一个提醒。
     *
     * 此函数会计算课程的下一个提醒时间。
     *
     * @param entry 课程表条目
     * @param reminderMinutes 提醒提前的分钟数
     * @param nowMillis 当前时间戳（毫秒）
     * @param nowDate 当前日期
     * @return 下一个提醒，或 null 如果没有下一个提醒
     */
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

    /**
     * 计算触发时间戳。
     *
     * 此函数会根据课程的发生日期、开始时间和提醒提前分钟数，计算提醒的触发时间戳。
     *
     * @param occurrenceDate 课程发生日期
     * @param startMinutes 课程开始时间（从午夜开始的分钟数）
     * @param reminderMinutes 提醒提前的分钟数
     * @return 触发时间戳（毫秒），或 null 如果计算失败
     */
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

    /**
     * 为提醒生成请求代码。
     *
     * 此函数会为课程提醒生成一个唯一的请求代码，用于 PendingIntent。
     *
     * @param entry 课程表条目
     * @param reminderMinutes 提醒提前的分钟数
     * @return 请求代码
     */
    private fun requestCodeFor(entry: TimetableEntry, reminderMinutes: Int): Int {
        // 使用 Objects.hash 比 String.hashCode 连接具有更好的分布性。
        // 条目 ID 是 UUID（全局唯一），因此将其与日期/时间/提醒分钟数结合
        // 可以为 PendingIntent 请求代码生成分布良好的整数键。
        return java.util.Objects.hash(entry.id, entry.date, entry.startMinutes, reminderMinutes)
    }

    /**
     * 生成调度签名。
     *
     * 此函数会为已调度的提醒生成一个签名，用于比较提醒是否发生变化。
     *
     * @param scheduled 已调度的提醒
     * @return 调度签名
     */
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

    /**
     * 编码调度签名。
     *
     * 此函数会将调度映射编码为字符串集合，用于存储到共享首选项中。
     *
     * @param schedules 调度映射
     * @return 编码后的字符串集合
     */
    private fun encodeScheduledSignatures(schedules: Map<Int, ScheduledReminder>): Set<String> {
        return schedules.map { (requestCode, scheduled) ->
            "$requestCode=${scheduleSignature(scheduled)}"
        }.toSet()
    }

    /**
     * 解码调度签名。
     *
     * 此函数会将编码的调度签名解码为请求代码和签名的配对。
     *
     * @param encoded 编码的调度签名
     * @return 请求代码和签名的配对，或 null 如果解码失败
     */
    private fun decodeScheduledSignature(encoded: String): Pair<Int, String>? {
        val separatorIndex = encoded.indexOf('=')
        if (separatorIndex <= 0 || separatorIndex == encoded.lastIndex) return null
        val requestCode = encoded.substring(0, separatorIndex).toIntOrNull() ?: return null
        val signature = encoded.substring(separatorIndex + 1)
        if (signature.isBlank()) return null
        return requestCode to signature
    }

    /**
     * 取消闹钟。
     *
     * 此函数会取消指定请求代码的闹钟。
     *
     * @param context 应用上下文
     * @param alarmManager 闹钟管理器
     * @param requestCode 请求代码
     */
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

    /**
     * 创建待处理意图。
     *
     * 此函数会为课程提醒创建一个待处理意图。
     *
     * @param context 应用上下文
     * @param entry 课程表条目
     * @param occurrenceDate 提醒发生的日期
     * @param reminderMinutes 提醒提前的分钟数
     * @param requestCode 请求代码
     * @param includeNoCreateFlag 是否包含 NO_CREATE 标志
     * @return 待处理意图，或 null 如果创建失败
     */
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

    /**
     * 调度闹钟。
     *
     * 此函数会根据设备的 Android 版本和权限状态，选择合适的方式调度闹钟。
     *
     * @param alarmManager 闹钟管理器
     * @param triggerAtMillis 触发时间戳（毫秒）
     * @param pendingIntent 待处理意图
     */
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
