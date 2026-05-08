package com.example.timetable.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.R
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val appDestinationNameStateSaver = Saver<String, Any>(
    save = { it },
    restore = { savedValue ->
        AppDestination.fromSavedStateValue(savedValue).name
    },
)

/**
 * 文件操作启动器集合。
 *
 * 封装课程表应用中导入、导出和背景图片三个文件操作启动器，
 * 以便从 [ScheduleApp] 中提取出样板式的 launcher 注册逻辑。
 *
 * @param import ICS 文件导入启动器
 * @param export ICS 文件导出启动器
 * @param backgroundImage 背景图片选择启动器
 */
internal data class ScheduleLaunchers(
    val import: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val export: androidx.activity.result.ActivityResultLauncher<String>,
    val backgroundImage: androidx.activity.result.ActivityResultLauncher<String>,
)

/**
 * 创建并记住课程表应用所需的文件操作启动器。
 *
 * 包含三个启动器：
 * - **import**: 通过 `OpenDocument` 选择 `.ics` 文件并触发导入
 * - **export**: 通过 `CreateDocument` 创建 `.ics` 文件并写入导出内容
 * - **backgroundImage**: 通过 `GetContent` 选择图片并设置自定义背景
 *
 * @param viewModel 课程表视图模型
 * @param snackbarHostState 用于显示操作结果的 Snackbar 状态
 * @param onBackgroundAppearanceChange 背景外观变更回调
 * @param onShowBackgroundAdjustDialogChange 显示背景调整对话框回调
 */
@Composable
private fun rememberScheduleLaunchers(
    viewModel: ScheduleViewModel,
    snackbarHostState: SnackbarHostState,
    onBackgroundAppearanceChange: (com.example.timetable.data.BackgroundAppearance) -> Unit,
    onShowBackgroundAdjustDialogChange: (Boolean) -> Unit,
): ScheduleLaunchers {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Pre-read string resources for use in launcher callbacks
    val msgExportSuccess = stringResource(R.string.msg_export_success)
    val msgExportFailedTemplate = stringResource(R.string.msg_export_failed, "%s")
    val msgUnknownError = stringResource(R.string.msg_unknown_error)
    val msgBackgroundUpdated = stringResource(R.string.msg_background_updated)
    val msgBackgroundFailedTemplate = stringResource(R.string.msg_background_failed, "%s")

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromIcs(context.contentResolver, uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    }
                    snackbarHostState.showSnackbar(msgExportSuccess)
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(msgExportFailedTemplate.format(error.message ?: msgUnknownError))
                }
            }
        }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    BackgroundImageManager.saveCustomBackground(context, context.contentResolver, uri)
                    onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                    onShowBackgroundAdjustDialogChange(true)
                    snackbarHostState.showSnackbar(msgBackgroundUpdated)
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        msgBackgroundFailedTemplate.format(error.message ?: msgUnknownError),
                    )
                }
            }
        }
    }

    return ScheduleLaunchers(
        import = importLauncher,
        export = exportLauncher,
        backgroundImage = backgroundImageLauncher,
    )
}

/**
 * 课程表应用状态持有者。
 *
 * 将 [ScheduleApp] 中分散的 20+ 个状态变量和派生值集中管理，
 * 遵循 Compose 官方推荐的 State Holder 模式，降低主函数复杂度。
 *
 * 所有 `var` 属性均通过 `mutableStateOf` 委托实现，
 * 读取时自动注册为 Compose 订阅者，变更时触发重组。
 *
 * @param viewModel 课程表视图模型
 * @param snackbarHostState Snackbar 主机状态
 * @param scope 协程作用域
 * @param context Android 上下文
 * @param minDate 最小可选日期
 * @param maxDate 最大可选日期
 * @param selectedDateState 选中日期（rememberSaveable 状态）
 * @param currentDestinationNameState 当前导航目标名称（rememberSaveable 状态）
 * @param backgroundAppearanceState 背景外观状态
 * @param weekCardAlphaState 周卡片透明度状态
 * @param weekCardHueState 周卡片色相状态
 * @param weekTimeSlotsState 周时段列表状态
 */
@Stable
internal class ScheduleAppState(
    val viewModel: ScheduleViewModel,
    val snackbarHostState: SnackbarHostState,
    val scope: CoroutineScope,
    val context: Context,
    val minDate: LocalDate,
    val maxDate: LocalDate,

    // rememberSaveable 状态（由 composable 创建并传入）
    private val selectedDateState: MutableState<String>,
    private val currentDestinationNameState: MutableState<String>,

    // 外观持久化状态（由 composable 通过 remember(context) 创建并传入）
    private val backgroundAppearanceState: MutableState<BackgroundAppearance>,
    private val weekCardAlphaState: MutableFloatState,
    private val weekCardHueState: MutableFloatState,
    private val weekTimeSlotsState: MutableState<List<WeekTimeSlot>>,
) {
    // ── 课程数据（由 ViewModel Flow 更新）────────────────────

    var entries by mutableStateOf(emptyList<TimetableEntry>())

    // ── 下一节课快照（由 LaunchedEffect 更新）────────────────

    var nextCourseSnapshot by mutableStateOf<NextCourseSnapshot?>(null)

    // ── 导航 ─────────────────────────────────────────────────

    var selectedDate: String
        get() = selectedDateState.value
        set(value) { selectedDateState.value = value }

    var currentDestinationName: String
        get() = currentDestinationNameState.value
        set(value) { currentDestinationNameState.value = value }

    val currentDestination: AppDestination
        get() = AppDestination.fromSavedName(currentDestinationName)

    val isWeekMode: Boolean
        get() = currentDestination == AppDestination.WEEK

    val isSettingsPage: Boolean
        get() = currentDestination == AppDestination.SETTINGS

    val selectedLocalDate: LocalDate by derivedStateOf {
        parseEntryDate(selectedDate) ?: minDate
    }

    val selectedWeekStart: LocalDate by derivedStateOf {
        selectedLocalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    val selectedWeekEnd: LocalDate by derivedStateOf {
        selectedWeekStart.plusDays(6)
    }

    // ── 外观 ─────────────────────────────────────────────────

    var backgroundAppearance: BackgroundAppearance
        get() = backgroundAppearanceState.value
        set(value) { backgroundAppearanceState.value = value }

    var weekCardAlpha: Float
        get() = weekCardAlphaState.floatValue
        set(value) { weekCardAlphaState.floatValue = value }

    var weekCardHue: Float
        get() = weekCardHueState.floatValue
        set(value) { weekCardHueState.floatValue = value }

    var weekTimeSlots: List<WeekTimeSlot>
        get() = weekTimeSlotsState.value
        set(value) { weekTimeSlotsState.value = value }

    // ── 对话框状态 ────────────────────────────────────────────

    var editingEntry by mutableStateOf<TimetableEntry?>(null)
    var pendingConflict by mutableStateOf<PendingEntryConflict?>(null)
    var editingWeekSlotIndex by mutableStateOf<Int?>(null)
    var addingWeekSlotInitial by mutableStateOf<WeekTimeSlot?>(null)
    var editingWeekSlotCount by mutableStateOf(false)
    var editingFixedWeekSchedule by mutableStateOf(false)
    var showBackgroundAdjustDialog by mutableStateOf(false)
    var clearingCache by mutableStateOf(false)
    var deletingEntry by mutableStateOf<TimetableEntry?>(null)
    var importPreviewState by mutableStateOf<ImportPreview?>(null)

    // ── 提醒设置 ──────────────────────────────────────────────

    var reminderMinutes by mutableStateOf(CourseReminderScheduler.getReminderMinutesSet(context))
    var exactAlarmEnabled by mutableStateOf(CourseReminderScheduler.canScheduleExactAlarms(context))
    var notificationPermissionRefreshToken by mutableIntStateOf(0)
    val reminderOptions: List<Int> = CourseReminderScheduler.reminderMinuteOptions()

    // ── 文件/权限启动器（composable 创建后设置）───────────────

    lateinit var launchers: ScheduleLaunchers
    lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    lateinit var exactAlarmSettingsLauncher: ActivityResultLauncher<Intent>
}

/**
 * 创建并记住 [ScheduleAppState] 实例。
 *
 * 在 composable 上下文中初始化所有状态，处理 `rememberSaveable` 持久化、
 * 外观存储回读、启动器注册和副作用收集，然后将结果封装到状态持有者中。
 *
 * @param viewModel 课程表视图模型
 * @param launchTarget 启动目标参数
 * @return 初始化完成的 [ScheduleAppState] 实例
 */
@Composable
internal fun rememberScheduleAppState(
    viewModel: ScheduleViewModel = viewModel(),
    launchTarget: AppLaunchTarget = AppLaunchTarget(),
): ScheduleAppState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    // ── 日期边界 ──
    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    @Suppress("MagicNumber") // Fallback date for initial state
    val initialDate = parseEntryDate(launchTarget.selectedDate.orEmpty())
        ?.takeIf { it in minDate..maxDate }
        ?: LocalDate.now().takeIf { it in minDate..maxDate }
        ?: LocalDate.of(2026, 1, 1)

    // ── Saveable 状态 ──
    val selectedDateState = rememberSaveable { mutableStateOf(initialDate.toString()) }
    val currentDestinationNameState = rememberSaveable(stateSaver = appDestinationNameStateSaver) {
        mutableStateOf(launchTarget.destination.name)
    }

    // ── 外观持久化状态 ──
    val backgroundAppearanceState = remember(context) {
        mutableStateOf(AppearanceStore.getBackgroundAppearance(context))
    }
    val weekCardAlphaState = remember(context) {
        mutableFloatStateOf(AppearanceStore.getWeekCardAlpha(context))
    }
    val weekCardHueState = remember(context) {
        mutableFloatStateOf(AppearanceStore.getWeekCardHue(context))
    }
    val weekTimeSlotsState = remember(context) {
        mutableStateOf(AppearanceStore.getWeekTimeSlots(context))
    }

    // ── 创建状态持有者 ──
    val state = remember {
        ScheduleAppState(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            scope = scope,
            context = context,
            minDate = minDate,
            maxDate = maxDate,
            selectedDateState = selectedDateState,
            currentDestinationNameState = currentDestinationNameState,
            backgroundAppearanceState = backgroundAppearanceState,
            weekCardAlphaState = weekCardAlphaState,
            weekCardHueState = weekCardHueState,
            weekTimeSlotsState = weekTimeSlotsState,
        )
    }

    // ── 同步 ViewModel 数据 ──
    state.entries = entries

    // ── 文件启动器注册 ──
    state.launchers = rememberScheduleLaunchers(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onBackgroundAppearanceChange = { state.backgroundAppearance = it },
        onShowBackgroundAdjustDialogChange = { state.showBackgroundAdjustDialog = it },
    )

    // ── 通知/闹钟权限启动器 ──
    val msgNotificationsEnabled = stringResource(R.string.msg_notifications_enabled)
    val msgNotificationsDisabledWarning = stringResource(R.string.msg_notifications_disabled_warning)
    val msgExactAlarmEnabled = stringResource(R.string.msg_exact_alarm_enabled)
    val msgExactAlarmDisabledWarning = stringResource(R.string.msg_exact_alarm_disabled_warning)
    val msgExactAlarmStillDisabled = stringResource(R.string.msg_exact_alarm_still_disabled)

    state.notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state.notificationPermissionRefreshToken++
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) msgNotificationsEnabled else msgNotificationsDisabledWarning,
            )
        }
    }

    state.exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val enabled = CourseReminderScheduler.canScheduleExactAlarms(context)
        state.exactAlarmEnabled = enabled
        scope.launch {
            if (enabled) {
                viewModel.resyncReminderSchedule()
                snackbarHostState.showSnackbar(msgExactAlarmEnabled)
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar(msgExactAlarmDisabledWarning)
            } else {
                snackbarHostState.showSnackbar(msgExactAlarmStillDisabled)
            }
        }
    }

    // ── 副作用 ──

    // 导航目标规范化
    LaunchedEffect(state.currentDestinationName) {
        val normalizedDestinationName = state.currentDestination.name
        if (normalizedDestinationName != state.currentDestinationName) {
            state.currentDestinationName = normalizedDestinationName
        }
    }

    // 启动目标同步
    LaunchedEffect(launchTarget.selectedDate, launchTarget.destination) {
        val targetDate = parseEntryDate(launchTarget.selectedDate.orEmpty())
            ?.takeIf { it in minDate..maxDate }
        if (targetDate != null && targetDate.toString() != state.selectedDate) {
            state.selectedDate = targetDate.toString()
        }
        if (state.currentDestinationName != launchTarget.destination.name) {
            state.currentDestinationName = launchTarget.destination.name
        }
    }

    // 消息收集
    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }

    // 导入预览收集
    LaunchedEffect(Unit) {
        viewModel.importPreview.collect { preview ->
            state.importPreviewState = preview
        }
    }

    // 下一节课轮询
    LaunchedEffect(entries) {
        while (true) {
            val nowDate = LocalDate.now()
            val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            state.nextCourseSnapshot = findNextCourseSnapshot(
                entries = entries,
                nowDate = nowDate,
                nowMinutes = nowMinutes,
                context = context,
            )
            delay(30_000L)
        }
    }

    return state
}
