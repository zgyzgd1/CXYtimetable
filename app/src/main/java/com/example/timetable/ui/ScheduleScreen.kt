package com.example.timetable.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.timetable.R
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.AppCacheManager
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_WEEK_SLOT_START_MINUTES = 8 * 60
private const val DEFAULT_WEEK_SLOT_DURATION_MINUTES = 40
private const val DEFAULT_WEEK_SLOT_GAP_MINUTES = 5

private val appDestinationNameStateSaver = Saver<String, Any>(
    save = { it },
    restore = { savedValue ->
        AppDestination.fromSavedStateValue(savedValue).name
    },
)

/**
 * 课程表应用主组件。
 *
 * 应用的主入口点，包含日视图、周视图和设置页面的切换逻辑。
 *
 * @param launchTarget 启动目标，包含初始日期和目标页面
 * @param viewModel 课程表视图模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    launchTarget: AppLaunchTarget = AppLaunchTarget(),
    viewModel: ScheduleViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var backgroundAppearance by remember(context) { mutableStateOf(AppearanceStore.getBackgroundAppearance(context)) }
    var weekCardAlpha by remember(context) { mutableStateOf(AppearanceStore.getWeekCardAlpha(context)) }
    var weekCardHue by remember(context) { mutableStateOf(AppearanceStore.getWeekCardHue(context)) }
    var weekTimeSlots by remember(context) { mutableStateOf(AppearanceStore.getWeekTimeSlots(context)) }

    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    @Suppress("MagicNumber") // Fallback date for initial state
    val initialDate = parseEntryDate(launchTarget.selectedDate.orEmpty())
        ?.takeIf { it in minDate..maxDate }
        ?: LocalDate.now().takeIf { it in minDate..maxDate }
        ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var currentDestinationName by rememberSaveable(stateSaver = appDestinationNameStateSaver) {
        mutableStateOf(launchTarget.destination.name)
    }
    val currentDestination = remember(currentDestinationName) {
        AppDestination.fromSavedName(currentDestinationName)
    }
    LaunchedEffect(currentDestinationName) {
        val normalizedDestinationName = currentDestination.name
        if (normalizedDestinationName != currentDestinationName) {
            currentDestinationName = normalizedDestinationName
        }
    }
    LaunchedEffect(launchTarget.selectedDate, launchTarget.destination) {
        val targetDate = parseEntryDate(launchTarget.selectedDate.orEmpty())?.takeIf { it in minDate..maxDate }
        if (targetDate != null && targetDate.toString() != selectedDate) {
            selectedDate = targetDate.toString()
        }
        if (currentDestinationName != launchTarget.destination.name) {
            currentDestinationName = launchTarget.destination.name
        }
    }
    val isWeekMode = currentDestination == AppDestination.WEEK
    val isSettingsPage = currentDestination == AppDestination.SETTINGS

    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    val selectedWeekStart = remember(selectedLocalDate) {
        selectedLocalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val selectedWeekEnd = remember(selectedWeekStart) { selectedWeekStart.plusDays(6) }
    val nextCourseSnapshot by produceState<NextCourseSnapshot?>(
        initialValue = null,
        key1 = entries,
    ) {
        while (true) {
            val nowDate = LocalDate.now()
            val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            value = findNextCourseSnapshot(
                entries = entries,
                nowDate = nowDate,
                nowMinutes = nowMinutes,
                context = context,
            )
            delay(30_000L) // Refresh next-course snapshot every 30 seconds
        }
    }

    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var pendingConflict by remember { mutableStateOf<PendingEntryConflict?>(null) }
    var editingWeekSlotIndex by remember { mutableStateOf<Int?>(null) }
    var addingWeekSlotInitial by remember { mutableStateOf<WeekTimeSlot?>(null) }
    var editingWeekSlotCount by remember { mutableStateOf(false) }
    var editingFixedWeekSchedule by remember { mutableStateOf(false) }
    var showBackgroundAdjustDialog by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(CourseReminderScheduler.getReminderMinutesSet(context)) }
    var exactAlarmEnabled by remember { mutableStateOf(CourseReminderScheduler.canScheduleExactAlarms(context)) }
    val reminderOptions = remember { CourseReminderScheduler.reminderMinuteOptions() }
    var deletingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var importPreviewState by remember { mutableStateOf<ImportPreview?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) context.getString(R.string.msg_notifications_enabled) else context.getString(R.string.msg_notifications_disabled_warning),
            )
        }
    }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val enabled = CourseReminderScheduler.canScheduleExactAlarms(context)
        exactAlarmEnabled = enabled
        scope.launch {
            if (enabled) {
                viewModel.resyncReminderSchedule()
                snackbarHostState.showSnackbar(context.getString(R.string.msg_exact_alarm_enabled))
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_exact_alarm_disabled_warning))
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_exact_alarm_still_disabled))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }

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
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_export_success))
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_export_failed, error.message ?: context.getString(R.string.msg_unknown_error)))
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
                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                    showBackgroundAdjustDialog = true
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_background_updated))
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.msg_background_failed, error.message ?: context.getString(R.string.msg_unknown_error)),
                    )
                }
            }
        }
    }

    val dateRangeEntriesCache = remember(entries) {
        DateRangeEntriesCache(entries)
    }
    val dayEntriesByDate = remember(dateRangeEntriesCache, selectedLocalDate) {
        dateRangeEntriesCache.resolve(selectedLocalDate, selectedLocalDate)
    }
    val selectedDayEntries = remember(dayEntriesByDate, selectedLocalDate) {
        dayEntriesByDate[selectedLocalDate].orEmpty()
    }

    LaunchedEffect(Unit) {
        viewModel.importPreview.collect { preview ->
            importPreviewState = preview
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundLayer(backgroundAppearance = backgroundAppearance)

        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                when (currentDestination) {
                    AppDestination.DAY,
                    AppDestination.SETTINGS -> {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = if (currentDestination == AppDestination.DAY) stringResource(R.string.title_my_schedule) else stringResource(R.string.title_settings),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                    AppDestination.WEEK -> Unit
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isSettingsPage,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            editingEntry = createQuickEntryTemplate(
                                date = selectedLocalDate,
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
                    currentDestination = currentDestination,
                    onDestinationChange = { destination -> currentDestinationName = destination.name },
                )
            },
        ) { padding ->
            when (currentDestination) {
                AppDestination.WEEK -> {
                    WeekViewContent(
                        config = WeekViewConfig(
                            selectedDate = selectedDate,
                            selectedLocalDate = selectedLocalDate,
                            selectedWeekStart = selectedWeekStart,
                            selectedWeekEnd = selectedWeekEnd,
                            minDate = minDate,
                            maxDate = maxDate,
                        ),
                        data = WeekViewData(
                            entries = entries,
                            dateRangeEntriesCache = dateRangeEntriesCache,
                            weekTimeSlots = weekTimeSlots,
                            weekCardAlpha = weekCardAlpha,
                            weekCardHue = weekCardHue,
                        ),
                        snackbarHostState = snackbarHostState,
                        callbacks = WeekViewCallbacks(
                            onDateChanged = { selectedDate = it },
                            onEditEntry = { editingEntry = it },
                            onEditWeekSlot = { editingWeekSlotIndex = it },
                            onAddWeekSlot = { addingWeekSlotInitial = it },
                            onEditFixedWeekSchedule = { editingFixedWeekSchedule = true },
                        ),
                        contentPadding = padding,
                    )
                }
                AppDestination.DAY -> {
                    DayViewContent(
                        padding = padding,
                        selectedDate = selectedDate,
                        selectedLocalDate = selectedLocalDate,
                        minDate = minDate,
                        maxDate = maxDate,
                        entries = entries,
                        selectedDayEntries = selectedDayEntries,
                        dateRangeEntriesCache = dateRangeEntriesCache,
                        nextCourseSnapshot = nextCourseSnapshot,
                        snackbarHostState = snackbarHostState,
                        importLauncher = importLauncher,
                        exportLauncher = exportLauncher,
                        reminderConfig = ReminderConfig(
                            minutes = reminderMinutes,
                            options = reminderOptions,
                            exactAlarmEnabled = exactAlarmEnabled,
                            onMinutesChange = { minutes ->
                                reminderMinutes = minutes
                                viewModel.updateReminderMinutes(minutes)
                            },
                            onEnableNotifications = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onOpenExactAlarmSettings = {
                                val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                                if (intent != null) exactAlarmSettingsLauncher.launch(intent)
                            },
                        ),
                        appearanceConfig = AppearanceConfig(
                            backgroundAppearance = backgroundAppearance,
                            onSelectBackgroundImage = { backgroundImageLauncher.launch("image/*") },
                            onAdjustCustomBackground = { showBackgroundAdjustDialog = true },
                            onBackgroundAppearanceChange = { backgroundAppearance = it },
                            weekCardAlpha = weekCardAlpha,
                            onWeekCardAlphaChange = { alpha ->
                                weekCardAlpha = alpha
                                AppearanceStore.setWeekCardAlpha(context, alpha)
                            },
                            weekCardHue = weekCardHue,
                            onWeekCardHueChange = { hue ->
                                weekCardHue = hue
                                AppearanceStore.setWeekCardHue(context, hue)
                            },
                        ),
                        onDateChanged = { selectedDate = it },
                        onEditEntry = { editingEntry = it },
                        onDuplicateEntry = { entry ->
                            editingEntry = duplicateEntryTemplate(entry)
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.msg_entry_duplicated))
                            }
                        },
                        onDeleteEntry = { deletingEntry = it },
                        onCreateEntry = { date, existing ->
                            editingEntry = createQuickEntryTemplate(date, existing)
                        },
                    )
                }
                AppDestination.SETTINGS -> {
                    SettingsScreen(
                        clearingCache = clearingCache,
                        onClearCache = {
                            if (clearingCache) return@SettingsScreen
                            scope.launch {
                                clearingCache = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        AppCacheManager.clearAppCaches(context)
                                    }
                                    val message = if (result.bytesCleared > 0L) {
                                        context.getString(R.string.msg_cache_cleared, AppCacheManager.formatBytes(result.bytesCleared))
                                    } else {
                                        context.getString(R.string.msg_cache_empty)
                                    }
                                    snackbarHostState.showSnackbar(message)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.msg_cache_clear_failed, error.message ?: context.getString(R.string.msg_unknown_error)),
                                    )
                                } finally {
                                    clearingCache = false
                                }
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    ScheduleDialogOverlays(
        viewModel = viewModel,
        entries = entries,
        weekTimeSlots = weekTimeSlots,
        snackbarHostState = snackbarHostState,
        editingEntry = editingEntry,
        pendingConflict = pendingConflict,
        editingWeekSlotIndex = editingWeekSlotIndex,
        addingWeekSlotInitial = addingWeekSlotInitial,
        editingWeekSlotCount = editingWeekSlotCount,
        editingFixedWeekSchedule = editingFixedWeekSchedule,
        showBackgroundAdjustDialog = showBackgroundAdjustDialog,
        deletingEntry = deletingEntry,
        importPreviewState = importPreviewState,
        backgroundAppearance = backgroundAppearance,
        onEditingEntryChange = { editingEntry = it },
        onPendingConflictChange = { pendingConflict = it },
        onEditingWeekSlotIndexChange = { editingWeekSlotIndex = it },
        onAddingWeekSlotInitialChange = { addingWeekSlotInitial = it },
        onEditingWeekSlotCountChange = { editingWeekSlotCount = it },
        onEditingFixedWeekScheduleChange = { editingFixedWeekSchedule = it },
        onShowBackgroundAdjustDialogChange = { showBackgroundAdjustDialog = it },
        onDeletingEntryChange = { deletingEntry = it },
        onImportPreviewStateChange = { importPreviewState = it },
        onBackgroundAppearanceChange = { backgroundAppearance = it },
        onWeekTimeSlotsChange = { weekTimeSlots = it },
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
 * @param notificationPermissionLauncher 通知权限启动器
 * @param exactAlarmSettingsLauncher 精确闹钟设置启动器
 * @param exactAlarmEnabled 精确闹钟是否启用
 * @param reminderMinutes 提醒分钟数
 * @param reminderOptions 提醒选项
 * @param onReminderMinutesChange 提醒分钟数变更回调
 * @param backgroundAppearance 背景外观
 * @param onBackgroundAppearanceChange 背景外观变更回调
 * @param onSelectBackgroundImage 选择背景图片回调
 * @param onAdjustCustomBackground 调整自定义背景回调
 * @param weekCardAlpha 周卡片透明度
 * @param onWeekCardAlphaChange 周卡片透明度变更回调
 * @param weekCardHue 周卡片色调
 * @param onWeekCardHueChange 周卡片色调变更回调
 * @param onDateChanged 日期变更回调
 * @param onEditEntry 编辑课程条目回调
 * @param onDuplicateEntry 复制课程条目回调
 * @param onDeleteEntry 删除课程条目回调
 * @param onCreateEntry 创建课程条目回调
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
    onDateChanged: (String) -> Unit,
    onEditEntry: (TimetableEntry) -> Unit,
    onDuplicateEntry: (TimetableEntry) -> Unit,
    onDeleteEntry: (TimetableEntry) -> Unit,
    onCreateEntry: (LocalDate, List<TimetableEntry>) -> Unit,
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
                                if (previousDate >= minDate) onDateChanged(previousDate.toString())
                            }
                            totalHorizontalDrag < -swipeThresholdPx -> {
                                val nextDate = selectedLocalDate.plusDays(1)
                                if (nextDate <= maxDate) onDateChanged(nextDate.toString())
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
            filteredEntries = selectedDayEntries,
            selectedDayEntries = selectedDayEntries,
            dateRangeEntriesCache = dateRangeEntriesCache,
            nextCourseSnapshot = nextCourseSnapshot,
            importLauncher = importLauncher,
            exportLauncher = exportLauncher,
            reminderConfig = reminderConfig,
            appearanceConfig = appearanceConfig,
            onDateChanged = onDateChanged,
            onEditEntry = onEditEntry,
            onDuplicateEntry = onDuplicateEntry,
            onDeleteEntry = onDeleteEntry,
            onCreateEntry = onCreateEntry,
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

data class ReminderConfig(
    val minutes: List<Int>,
    val options: List<Int>,
    val exactAlarmEnabled: Boolean,
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
