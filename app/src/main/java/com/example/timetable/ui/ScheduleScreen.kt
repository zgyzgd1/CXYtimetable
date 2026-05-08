package com.example.timetable.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.timetable.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.timetable.data.AppCacheManager
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatMinutes
import com.example.timetable.notify.CourseReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_WEEK_SLOT_START_MINUTES = 8 * 60
private const val DEFAULT_WEEK_SLOT_DURATION_MINUTES = 40
private const val DEFAULT_WEEK_SLOT_GAP_MINUTES = 5

/**
 * 课程表应用主组件。
 *
 * 应用的主入口点。内部委托 [rememberScheduleAppState] 管理所有状态，
 * 再由 [ScheduleAppContent] 渲染 UI，保持此函数简洁。
 *
 * @param launchTarget 启动目标，包含初始日期和目标页面
 * @param viewModel 课程表视图模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    launchTarget: AppLaunchTarget = AppLaunchTarget(),
    viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state = rememberScheduleAppState(viewModel = viewModel, launchTarget = launchTarget)
    ScheduleAppContent(state = state)
}

/**
 * 课程表应用 UI 内容。
 *
 * 从 [ScheduleApp] 提取的纯 UI 组合函数，所有状态均通过 [state] 参数读写。
 * 仅在 composable 作用域内预读 stringResource，然后传递给子组件。
 *
 * @param state 应用状态持有者
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleAppContent(state: ScheduleAppState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── 缓存的派生值 ──
    val dateRangeEntriesCache = remember(state.entries) { DateRangeEntriesCache(state.entries) }
    val dayEntriesByDate = remember(dateRangeEntriesCache, state.selectedLocalDate) {
        dateRangeEntriesCache.resolve(state.selectedLocalDate, state.selectedLocalDate)
    }
    val selectedDayEntries = remember(dayEntriesByDate, state.selectedLocalDate) {
        dayEntriesByDate[state.selectedLocalDate].orEmpty()
    }

    // ── 预读 stringResource（用于回调）──
    val msgEntryDuplicated = stringResource(R.string.msg_entry_duplicated)
    val msgCacheClearedTemplate = stringResource(R.string.msg_cache_cleared, "%s")
    val msgCacheEmpty = stringResource(R.string.msg_cache_empty)
    val msgCacheClearFailedTemplate = stringResource(R.string.msg_cache_clear_failed, "%s")
    val msgUnknownError = stringResource(R.string.msg_unknown_error)
    val msgNotificationsEnabled = stringResource(R.string.msg_notifications_enabled)
    val exportFilename = stringResource(R.string.export_filename)
    val notificationPermissionRequired = CourseReminderScheduler.notificationPermissionRequired()
    val notificationGranted = remember(state.notificationPermissionRefreshToken) {
        CourseReminderScheduler.notificationsEnabled(context)
    }
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.notificationPermissionRefreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── UI 树 ──
    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundLayer(backgroundAppearance = state.backgroundAppearance)

        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) },
            topBar = {
                when (state.currentDestination) {
                    AppDestination.DAY,
                    AppDestination.SETTINGS -> {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = if (state.currentDestination == AppDestination.DAY) stringResource(R.string.title_my_schedule) else stringResource(R.string.title_settings),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.scrolledContainer(),
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                    AppDestination.WEEK -> Unit
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !state.isSettingsPage,
                    enter = slideInVertically(tween(300)) { it / 2 } + scaleIn(tween(300)) + fadeIn(tween(300)),
                    exit = scaleOut(tween(200)) + fadeOut(tween(200)),
                ) {
                    val haptic = LocalHapticFeedback.current
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.surface.stickyHeader(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            state.editingEntry = createQuickEntryTemplate(
                                date = state.selectedLocalDate,
                                existingEntries = selectedDayEntries,
                            )
                        },
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.action_add_course))
                    }
                }
            },
            bottomBar = {
                ViewModeSwitcher(
                    currentDestination = state.currentDestination,
                    onDestinationChange = { destination -> state.currentDestinationName = destination.name },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = state.currentDestination,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(300)) { width -> direction * width / 4 } + fadeIn(tween(300)))
                        .togetherWith(
                            slideOutHorizontally(tween(300)) { width -> -direction * width / 4 } + fadeOut(tween(300))
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "pageTransition",
            ) { destination ->
                when (destination) {
                    AppDestination.WEEK -> {
                        WeekViewContent(
                            config = WeekViewConfig(
                                selectedDate = state.selectedDate,
                                selectedLocalDate = state.selectedLocalDate,
                                selectedWeekStart = state.selectedWeekStart,
                                selectedWeekEnd = state.selectedWeekEnd,
                                minDate = state.minDate,
                                maxDate = state.maxDate,
                            ),
                            data = WeekViewData(
                                entries = state.entries,
                                dateRangeEntriesCache = dateRangeEntriesCache,
                                weekTimeSlots = state.weekTimeSlots,
                                weekCardAlpha = state.weekCardAlpha,
                                weekCardHue = state.weekCardHue,
                            ),
                            snackbarHostState = state.snackbarHostState,
                            callbacks = WeekViewCallbacks(
                                onDateChanged = { state.selectedDate = it },
                                onEditEntry = { state.editingEntry = it },
                                onEditWeekSlot = { state.editingWeekSlotIndex = it },
                                onAddWeekSlot = { state.addingWeekSlotInitial = it },
                                onEditFixedWeekSchedule = { state.editingFixedWeekSchedule = true },
                            ),
                            contentPadding = padding,
                        )
                    }
                    AppDestination.DAY -> {
                        DayViewContent(
                            padding = padding,
                            selectedDate = state.selectedDate,
                            selectedLocalDate = state.selectedLocalDate,
                            minDate = state.minDate,
                            maxDate = state.maxDate,
                            entries = state.entries,
                            selectedDayEntries = selectedDayEntries,
                            dateRangeEntriesCache = dateRangeEntriesCache,
                            nextCourseSnapshot = state.nextCourseSnapshot,
                            snackbarHostState = state.snackbarHostState,
                            importLauncher = state.launchers.import,
                            exportLauncher = state.launchers.export,
                            reminderConfig = ReminderConfig(
                                minutes = state.reminderMinutes,
                                options = state.reminderOptions,
                                exactAlarmEnabled = state.exactAlarmEnabled,
                                notificationPermissionRequired = notificationPermissionRequired,
                                notificationGranted = notificationGranted,
                                onMinutesChange = { minutes ->
                                    state.reminderMinutes = minutes
                                    state.viewModel.updateReminderMinutes(minutes)
                                },
                                onEnableNotifications = {
                                    requestNotificationPermissionIfNeeded(
                                        state = state,
                                        notificationsAlreadyEnabledMessage = msgNotificationsEnabled,
                                    )
                                },
                                onOpenExactAlarmSettings = {
                                    val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                                    if (intent != null) state.exactAlarmSettingsLauncher.launch(intent)
                                },
                            ),
                            appearanceConfig = AppearanceConfig(
                                backgroundAppearance = state.backgroundAppearance,
                                onSelectBackgroundImage = { state.launchers.backgroundImage.launch("image/*") },
                                onAdjustCustomBackground = { state.showBackgroundAdjustDialog = true },
                                onBackgroundAppearanceChange = { state.backgroundAppearance = it },
                                weekCardAlpha = state.weekCardAlpha,
                                onWeekCardAlphaChange = { alpha ->
                                    state.weekCardAlpha = alpha
                                    AppearanceStore.setWeekCardAlpha(context, alpha)
                                },
                                weekCardHue = state.weekCardHue,
                                onWeekCardHueChange = { hue ->
                                    state.weekCardHue = hue
                                    AppearanceStore.setWeekCardHue(context, hue)
                                },
                            ),
                            callbacks = DayListCallbacks(
                                onDateChanged = { state.selectedDate = it },
                                onEditEntry = { state.editingEntry = it },
                                onDuplicateEntry = { entry ->
                                    state.editingEntry = duplicateEntryTemplate(entry)
                                    state.scope.launch {
                                        state.snackbarHostState.showSnackbar(msgEntryDuplicated)
                                    }
                                },
                                onDeleteEntry = { state.deletingEntry = it },
                                onCreateEntry = { date, existing ->
                                    state.editingEntry = createQuickEntryTemplate(date, existing)
                                },
                            ),
                        )
                    }
                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            clearingCache = state.clearingCache,
                            onClearCache = {
                                if (state.clearingCache) return@SettingsScreen
                                state.scope.launch {
                                    state.clearingCache = true
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            AppCacheManager.clearAppCaches(context)
                                        }
                                        val message = if (result.bytesCleared > 0L) {
                                            msgCacheClearedTemplate.format(AppCacheManager.formatBytes(result.bytesCleared))
                                        } else {
                                            msgCacheEmpty
                                        }
                                        state.snackbarHostState.showSnackbar(message)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        state.snackbarHostState.showSnackbar(
                                            msgCacheClearFailedTemplate.format(error.message ?: msgUnknownError),
                                        )
                                    } finally {
                                        state.clearingCache = false
                                    }
                                }
                            },
                            backgroundAppearance = state.backgroundAppearance,
                            onBackgroundModeChange = { mode ->
                                AppearanceStore.setBackgroundMode(context, mode)
                                state.backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                            },
                            onSelectBackgroundImage = { state.launchers.backgroundImage.launch("image/*") },
                            weekCardAlpha = state.weekCardAlpha,
                            onWeekCardAlphaChange = { alpha ->
                                state.weekCardAlpha = alpha
                                AppearanceStore.setWeekCardAlpha(context, alpha)
                            },
                            weekCardHue = state.weekCardHue,
                            onWeekCardHueChange = { hue ->
                                state.weekCardHue = hue
                                AppearanceStore.setWeekCardHue(context, hue)
                            },
                            notificationGranted = notificationGranted,
                            exactAlarmEnabled = state.exactAlarmEnabled,
                            onEnableNotifications = {
                                requestNotificationPermissionIfNeeded(
                                    state = state,
                                    notificationsAlreadyEnabledMessage = msgNotificationsEnabled,
                                )
                            },
                            onOpenExactAlarmSettings = {
                                val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                                if (intent != null) state.exactAlarmSettingsLauncher.launch(intent)
                            },
                            onImportIcs = {
                                state.launchers.import.launch(
                                    arrayOf(
                                        "text/calendar",
                                        "text/plain",
                                        "application/ics",
                                        "application/x-ical",
                                        "application/octet-stream",
                                        "*/*",
                                    ),
                                )
                            },
                            onExportIcs = {
                                state.launchers.export.launch(exportFilename)
                            },
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }

    ScheduleDialogOverlays(
        viewModel = state.viewModel,
        entries = state.entries,
        weekTimeSlots = state.weekTimeSlots,
        snackbarHostState = state.snackbarHostState,
        editingEntry = state.editingEntry,
        pendingConflict = state.pendingConflict,
        editingWeekSlotIndex = state.editingWeekSlotIndex,
        addingWeekSlotInitial = state.addingWeekSlotInitial,
        editingWeekSlotCount = state.editingWeekSlotCount,
        editingFixedWeekSchedule = state.editingFixedWeekSchedule,
        showBackgroundAdjustDialog = state.showBackgroundAdjustDialog,
        deletingEntry = state.deletingEntry,
        importPreviewState = state.importPreviewState,
        backgroundAppearance = state.backgroundAppearance,
        onEditingEntryChange = { state.editingEntry = it },
        onPendingConflictChange = { state.pendingConflict = it },
        onEditingWeekSlotIndexChange = { state.editingWeekSlotIndex = it },
        onAddingWeekSlotInitialChange = { state.addingWeekSlotInitial = it },
        onEditingWeekSlotCountChange = { state.editingWeekSlotCount = it },
        onEditingFixedWeekScheduleChange = { state.editingFixedWeekSchedule = it },
        onShowBackgroundAdjustDialogChange = { state.showBackgroundAdjustDialog = it },
        onDeletingEntryChange = { state.deletingEntry = it },
        onImportPreviewStateChange = { state.importPreviewState = it },
        onBackgroundAppearanceChange = { state.backgroundAppearance = it },
        onWeekTimeSlotsChange = { state.weekTimeSlots = it },
    )
}

/**
 * 日视图内容。
 *
 * 显示单日课程表，包含课程列表、下一节课信息、导入/导出功能等。
 *
 * @param padding 内边距
 * @param selectedDate 选中的日期
 * @param selectedLocalDate 选中的本地日期
 * @param minDate 最小日期
 * @param maxDate 最大日期
 * @param entries 所有课程条目
 * @param selectedDayEntries 选中日期的课程条目
 * @param dateRangeEntriesCache 日期范围课程缓存
 * @param nextCourseSnapshot 下一节课快照
 * @param snackbarHostState  Snackbar 主机状态
 * @param importLauncher 导入启动器
 * @param exportLauncher 导出启动器
 * @param reminderConfig 提醒设置与权限入口
 * @param appearanceConfig 背景和周卡片外观设置
 * @param callbacks 日视图交互回调
 */
@Composable
private fun DayViewContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    selectedDate: String,
    selectedLocalDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    entries: List<TimetableEntry>,
    selectedDayEntries: List<TimetableEntry>,
    dateRangeEntriesCache: DateRangeEntriesCache,
    nextCourseSnapshot: NextCourseSnapshot?,
    snackbarHostState: SnackbarHostState,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    reminderConfig: ReminderConfig,
    appearanceConfig: AppearanceConfig,
    callbacks: DayListCallbacks,
) {
    val isWeekMode = false
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedDate, isWeekMode) {
                var totalHorizontalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> totalHorizontalDrag += dragAmount },
                    onDragEnd = {
                        val swipeThresholdPx = density * 48f
                        when {
                            totalHorizontalDrag > swipeThresholdPx -> {
                                val previousDate = selectedLocalDate.minusDays(1)
                                if (previousDate >= minDate) callbacks.onDateChanged(previousDate.toString())
                            }
                            totalHorizontalDrag < -swipeThresholdPx -> {
                                val nextDate = selectedLocalDate.plusDays(1)
                                if (nextDate <= maxDate) callbacks.onDateChanged(nextDate.toString())
                            }
                        }
                        totalHorizontalDrag = 0f
                    },
                )
            }
            .padding(padding),
    ) {
        val listState = remember { androidx.compose.foundation.lazy.LazyListState() }

        DayScheduleList(
            listState = listState,
            snackbarHostState = snackbarHostState,
            entries = entries,
            selectedDate = selectedDate,
            selectedLocalDate = selectedLocalDate,
            selectedDayEntries = selectedDayEntries,
            dateRangeEntriesCache = dateRangeEntriesCache,
            nextCourseSnapshot = nextCourseSnapshot,
            importLauncher = importLauncher,
            exportLauncher = exportLauncher,
            reminderConfig = reminderConfig,
            appearanceConfig = appearanceConfig,
            callbacks = callbacks,
        )
    }
}

/**
 * 创建默认的新周时段。
 *
 * 根据现有时段列表创建一个默认的新周时段。
 *
 * @param slots 现有周时段列表
 * @return 新的周时段，或 null 如果无法创建
 */
internal fun defaultNewWeekSlot(slots: List<WeekTimeSlot>): WeekTimeSlot? {
    val lastSlot = slots.maxByOrNull { it.endMinutes }
    if (lastSlot == null) {
        return WeekTimeSlot(
            startMinutes = DEFAULT_WEEK_SLOT_START_MINUTES,
            endMinutes = DEFAULT_WEEK_SLOT_START_MINUTES + DEFAULT_WEEK_SLOT_DURATION_MINUTES,
        )
    }
    return nextWeekTimeSlot(lastSlot)
}

/**
 * 调整周时段列表大小。
 *
 * 根据目标数量调整周时段列表，添加或删除时段。
 *
 * @param slots 现有周时段列表
 * @param targetCount 目标数量
 * @return 调整后的周时段列表
 */
internal fun resizeWeekTimeSlots(slots: List<WeekTimeSlot>, targetCount: Int): List<WeekTimeSlot> {
    if (targetCount <= 0) return slots
    if (slots.size == targetCount) return slots.sortedBy { it.startMinutes }
    if (slots.size > targetCount) return slots.sortedBy { it.startMinutes }.take(targetCount)

    val expanded = slots.sortedBy { it.startMinutes }.toMutableList()
    while (expanded.size < targetCount) {
        val seed = expanded.lastOrNull()
        val nextSlot = if (seed == null) {
            WeekTimeSlot(
                startMinutes = DEFAULT_WEEK_SLOT_START_MINUTES,
                endMinutes = DEFAULT_WEEK_SLOT_START_MINUTES + DEFAULT_WEEK_SLOT_DURATION_MINUTES,
            )
        } else {
            nextWeekTimeSlot(seed)
        }
        if (nextSlot == null) break
        expanded += nextSlot
    }
    return expanded
}

/**
 * 创建下一个周时段。
 *
 * 根据前一个周时段创建下一个周时段。
 *
 * @param previous 前一个周时段
 * @return 下一个周时段，或 null 如果无法创建
 */
internal fun nextWeekTimeSlot(previous: WeekTimeSlot): WeekTimeSlot? {
    val start = previous.endMinutes + DEFAULT_WEEK_SLOT_GAP_MINUTES
    val end = start + DEFAULT_WEEK_SLOT_DURATION_MINUTES
    if (end > 24 * 60) return null
    return WeekTimeSlot(startMinutes = start, endMinutes = end)
}

/**
 * 待处理的课程冲突。
 *
 * 表示课程条目更新时遇到的冲突。
 *
 * @param updatedEntry 更新后的课程条目
 * @param conflictEntry 冲突的课程条目
 */
data class PendingEntryConflict(
    val updatedEntry: TimetableEntry,
    val conflictEntry: TimetableEntry,
)

private fun requestNotificationPermissionIfNeeded(
    state: ScheduleAppState,
    notificationsAlreadyEnabledMessage: String,
) {
    if (CourseReminderScheduler.notificationPermissionRequired()) {
        state.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        state.notificationPermissionRefreshToken++
        state.scope.launch {
            state.snackbarHostState.showSnackbar(notificationsAlreadyEnabledMessage)
        }
    }
}

data class ReminderConfig(
    val minutes: List<Int>,
    val options: List<Int>,
    val exactAlarmEnabled: Boolean,
    val notificationPermissionRequired: Boolean,
    val notificationGranted: Boolean,
    val onMinutesChange: (List<Int>) -> Unit,
    val onEnableNotifications: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
)

data class AppearanceConfig(
    val backgroundAppearance: com.example.timetable.data.BackgroundAppearance,
    val onSelectBackgroundImage: () -> Unit,
    val onAdjustCustomBackground: () -> Unit,
    val onBackgroundAppearanceChange: (com.example.timetable.data.BackgroundAppearance) -> Unit,
    val weekCardAlpha: Float,
    val onWeekCardAlphaChange: (Float) -> Unit,
    val weekCardHue: Float,
    val onWeekCardHueChange: (Float) -> Unit,
)

data class NextCourseCardState(
    val title: String,
    val timeRange: String,
    val location: String,
    val statusText: String,
)

internal fun NextCourseSnapshot.toCardState(unnamedLabel: String = ""): NextCourseCardState {
    return NextCourseCardState(
        title = entry.title.ifBlank { unnamedLabel },
        timeRange = "${formatMinutes(entry.startMinutes)} - ${formatMinutes(entry.endMinutes)}",
        location = entry.location,
        statusText = statusText,
    )
}

/**
 * 创建快速课程条目模板。
 *
 * 根据现有课程条目创建一个快速课程条目模板。
 *
 * @param date 日期
 * @param existingEntries 现有课程条目
 * @return 课程条目模板
 */
internal fun createQuickEntryTemplate(
    date: LocalDate,
    existingEntries: List<TimetableEntry>,
): TimetableEntry {
    val fallbackStart = 8 * 60
    val fallbackEnd = 9 * 60
    val lastEntry = existingEntries.maxByOrNull { it.endMinutes }

    val suggestedStart: Int
    val suggestedEnd: Int
    if (lastEntry == null) {
        suggestedStart = fallbackStart
        suggestedEnd = fallbackEnd
    } else {
        val templateDuration = (lastEntry.endMinutes - lastEntry.startMinutes).coerceIn(30, 120)
        suggestedStart = (lastEntry.endMinutes + DEFAULT_WEEK_SLOT_GAP_MINUTES).coerceIn(0, (24 * 60) - 1)
        suggestedEnd = (suggestedStart + templateDuration).coerceAtMost(24 * 60)
    }

    val (startMinutes, endMinutes) = if (suggestedEnd > suggestedStart) {
        suggestedStart to suggestedEnd
    } else {
        fallbackStart to fallbackEnd
    }

    return TimetableEntry.create(
        title = "",
        date = date.toString(),
        dayOfWeek = date.dayOfWeek.value,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
    )
}

/**
 * 复制课程条目模板。
 *
 * 创建一个现有课程条目的副本。
 *
 * @param source 源课程条目
 * @return 复制的课程条目
 */
internal fun duplicateEntryTemplate(source: TimetableEntry): TimetableEntry {
    return TimetableEntry.create(
        title = source.title,
        date = source.date,
        dayOfWeek = source.dayOfWeek,
        startMinutes = source.startMinutes,
        endMinutes = source.endMinutes,
        location = source.location,
        note = source.note,
        recurrenceType = source.recurrenceType,
        semesterStartDate = source.semesterStartDate,
        weekRule = source.weekRule,
        customWeekList = source.customWeekList,
        skipWeekList = source.skipWeekList,
    )
}
