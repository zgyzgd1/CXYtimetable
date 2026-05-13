package com.example.timetable.ui

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.timetable.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.AppCacheManager
import com.example.timetable.data.AppConstants
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableGroup
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.jw.JwImportScreen
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

internal enum class BackgroundModeSelection {
    APPLY_MODE,
    REQUEST_CUSTOM_IMAGE,
}

internal fun resolveBackgroundModeSelection(
    mode: AppBackgroundMode,
    hasCustomBackground: Boolean,
): BackgroundModeSelection {
    return if (mode == AppBackgroundMode.CUSTOM_IMAGE && !hasCustomBackground) {
        BackgroundModeSelection.REQUEST_CUSTOM_IMAGE
    } else {
        BackgroundModeSelection.APPLY_MODE
    }
}

internal data class ReminderPermissionRefreshState(
    val notificationPermissionRefreshToken: Int,
    val exactAlarmEnabled: Boolean,
)

internal fun refreshReminderPermissionState(
    currentNotificationPermissionRefreshToken: Int,
    canScheduleExactAlarms: () -> Boolean,
): ReminderPermissionRefreshState {
    return ReminderPermissionRefreshState(
        notificationPermissionRefreshToken = currentNotificationPermissionRefreshToken + 1,
        exactAlarmEnabled = canScheduleExactAlarms(),
    )
}

private val appDestinationNameStateSaver = Saver<String, Any>(
    save = { it },
    restore = { savedValue ->
        AppDestination.fromSavedStateValue(savedValue).name
    },
)

/**
 * 课程表应用主组件�? *
 * 应用的主入口点，包含日视图、周视图和设置页面的切换逻辑�? *
 * @param launchTarget 启动目标，包含初始日期和目标页面
 * @param viewModel 课程表视图模�? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    launchTarget: AppLaunchTarget = AppLaunchTarget(),
    viewModel: ScheduleViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val timetableGroups by viewModel.timetableGroups.collectAsStateWithLifecycle()
    val activeGroupId by viewModel.activeGroupId.collectAsStateWithLifecycle()
    val activeGroup = remember(timetableGroups, activeGroupId) {
        timetableGroups.firstOrNull { it.id == activeGroupId } ?: TimetableGroup.default()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resources = LocalContext.current.resources

    var backgroundAppearance by remember(context) { mutableStateOf(AppearanceStore.getBackgroundAppearance(context)) }
    var weekCardAlpha by remember(context) { mutableStateOf(AppearanceStore.getWeekCardAlpha(context)) }
    var weekCardHue by remember(context) { mutableStateOf(AppearanceStore.getWeekCardHue(context)) }
    var weekTimeSlots by remember(context) { mutableStateOf(AppearanceStore.getWeekTimeSlots(context)) }

    val minDate = AppConstants.MIN_DATE
    val maxDate = AppConstants.MAX_DATE
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
    val isJwImportPage = currentDestination == AppDestination.JW_IMPORT

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
    var notificationPermissionRefreshToken by remember { mutableStateOf(0) }
    val reminderOptions = remember { CourseReminderScheduler.reminderMinuteOptions() }
    var deletingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var importPreviewState by remember { mutableStateOf<ImportPreview?>(null) }
    val notificationGranted = remember(context, notificationPermissionRefreshToken) {
        CourseReminderScheduler.notificationsEnabled(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val refreshed = refreshReminderPermissionState(
                    currentNotificationPermissionRefreshToken = notificationPermissionRefreshToken,
                    canScheduleExactAlarms = { CourseReminderScheduler.canScheduleExactAlarms(context) },
                )
                notificationPermissionRefreshToken = refreshed.notificationPermissionRefreshToken
                exactAlarmEnabled = refreshed.exactAlarmEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launchers = rememberScheduleLaunchers(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onBackgroundAppearanceChange = { backgroundAppearance = it },
        onShowBackgroundAdjustDialogChange = { showBackgroundAdjustDialog = it },
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionRefreshToken++
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) resources.getString(R.string.msg_notifications_enabled) else resources.getString(R.string.msg_notifications_disabled_warning),
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
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_exact_alarm_enabled))
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_exact_alarm_disabled_warning))
            } else {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_exact_alarm_still_disabled))
            }
        }
    }

    fun requestNotificationPermissionOrReportNotRequired() {
        if (CourseReminderScheduler.notificationPermissionRequired()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_notifications_not_required))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
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

    LaunchedEffect(launchTarget.launchRequestId, launchTarget.openAddCourse, activeGroup.id) {
        if (!launchTarget.openAddCourse) return@LaunchedEffect
        val targetDate = parseEntryDate(launchTarget.selectedDate.orEmpty())
            ?.takeIf { it in minDate..maxDate }
            ?: selectedLocalDate
        selectedDate = targetDate.toString()
        currentDestinationName = AppDestination.DAY.name
        val targetEntries = dateRangeEntriesCache.resolve(targetDate, targetDate)[targetDate].orEmpty()
        editingEntry = createQuickEntryTemplate(
            date = targetDate,
            existingEntries = targetEntries,
            groupId = activeGroup.id,
        )
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
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.scrolledContainer(),
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                    AppDestination.WEEK,
                    AppDestination.JW_IMPORT -> Unit
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isSettingsPage && !isJwImportPage,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.surface.stickyHeader(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            editingEntry = createQuickEntryTemplate(
                                date = selectedLocalDate,
                                existingEntries = selectedDayEntries,
                                groupId = activeGroup.id,
                            )
                        },
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.action_add_course))
                    }
                }
            },
            bottomBar = {
                if (!isJwImportPage) {
                    ViewModeSwitcher(
                        currentDestination = currentDestination,
                        onDestinationChange = { destination -> currentDestinationName = destination.name },
                    )
                }
            },
        ) { padding ->
            when (currentDestination) {
                AppDestination.WEEK -> {
                    WeekViewContent(
                        selectedDate = selectedDate,
                        selectedLocalDate = selectedLocalDate,
                        selectedWeekStart = selectedWeekStart,
                        selectedWeekEnd = selectedWeekEnd,
                        minDate = minDate,
                        maxDate = maxDate,
                        entries = entries,
                        dateRangeEntriesCache = dateRangeEntriesCache,
                        weekTimeSlots = weekTimeSlots,
                        weekCardAlpha = weekCardAlpha,
                        weekCardHue = weekCardHue,
                        snackbarHostState = snackbarHostState,
                        onDateChanged = { selectedDate = it },
                        onEditEntry = { editingEntry = it },
                        onEditWeekSlot = { editingWeekSlotIndex = it },
                        onAddWeekSlot = { addingWeekSlotInitial = it },
                        onEditFixedWeekSchedule = { editingFixedWeekSchedule = true },
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
                        timetableGroups = timetableGroups,
                        activeGroup = activeGroup,
                        selectedDayEntries = selectedDayEntries,
                        dateRangeEntriesCache = dateRangeEntriesCache,
                        nextCourseSnapshot = nextCourseSnapshot,
                        snackbarHostState = snackbarHostState,
                        importLauncher = launchers.import,
                        exportLauncher = launchers.export,
                        onAcademicImport = { currentDestinationName = AppDestination.JW_IMPORT.name },
                        onSelectTimetableGroup = viewModel::selectTimetableGroup,
                        onCreateTimetableGroup = viewModel::createTimetableGroup,
                        reminderConfig = ReminderConfig(
                            minutes = reminderMinutes,
                            options = reminderOptions,
                            exactAlarmEnabled = exactAlarmEnabled,
                            onMinutesChange = { minutes ->
                                reminderMinutes = minutes
                                viewModel.updateReminderMinutes(minutes)
                            },
                            onEnableNotifications = ::requestNotificationPermissionOrReportNotRequired,
                            onOpenExactAlarmSettings = {
                                val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                                if (intent != null) exactAlarmSettingsLauncher.launch(intent)
                            },
                        ),
                        appearanceConfig = AppearanceConfig(
                            backgroundAppearance = backgroundAppearance,
                            onSelectBackgroundImage = { launchers.backgroundImage.launch("image/*") },
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
                        callbacks = DayListCallbacks(
                            onDateChanged = { selectedDate = it },
                            onEditEntry = { editingEntry = it },
                            onDuplicateEntry = { entry ->
                                editingEntry = duplicateEntryTemplate(entry)
                                scope.launch {
                                    snackbarHostState.showSnackbar(resources.getString(R.string.msg_entry_duplicated))
                                }
                            },
                            onDeleteEntry = { deletingEntry = it },
                            onCreateEntry = { date, existing ->
                                editingEntry = createQuickEntryTemplate(date, existing, activeGroup.id)
                            },
                        ),
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
                                        resources.getString(R.string.msg_cache_cleared, AppCacheManager.formatBytes(result.bytesCleared))
                                    } else {
                                        resources.getString(R.string.msg_cache_empty)
                                    }
                                    snackbarHostState.showSnackbar(message)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        resources.getString(R.string.msg_cache_clear_failed, error.message ?: resources.getString(R.string.msg_unknown_error)),
                                    )
                                } finally {
                                    clearingCache = false
                                }
                            }
                        },
                        backgroundAppearance = backgroundAppearance,
                        onBackgroundModeChange = { mode ->
                            when (
                                resolveBackgroundModeSelection(
                                    mode = mode,
                                    hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
                                )
                            ) {
                                BackgroundModeSelection.REQUEST_CUSTOM_IMAGE -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.msg_select_background_image))
                                    }
                                    launchers.backgroundImage.launch("image/*")
                                }
                                BackgroundModeSelection.APPLY_MODE -> {
                                    AppearanceStore.setBackgroundMode(context, mode)
                                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                                }
                            }
                        },
                        onSelectBackgroundImage = { launchers.backgroundImage.launch("image/*") },
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
                        notificationGranted = notificationGranted,
                        exactAlarmEnabled = exactAlarmEnabled,
                        onEnableNotifications = ::requestNotificationPermissionOrReportNotRequired,
                        onOpenExactAlarmSettings = {
                            val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                            if (intent != null) exactAlarmSettingsLauncher.launch(intent)
                        },
                        onImportIcs = {
                            launchers.import.launch(
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
                        onExportIcs = { launchers.export.launch(resources.getString(R.string.export_filename)) },
                        modifier = Modifier.padding(padding),
                    )
                }
                AppDestination.JW_IMPORT -> {
                    JwImportScreen(
                        viewModel = viewModel,
                        onBack = { currentDestinationName = AppDestination.DAY.name },
                        onImportSubmitted = { currentDestinationName = AppDestination.DAY.name },
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
        activeGroup = activeGroup,
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
 * 日视图内容�? *
 * 显示单日课程表，包含课程列表、下一节课信息、导�?导出功能等�? *
 * @param padding 内边�? * @param selectedDate 选中的日�? * @param selectedLocalDate 选中的本地日�? * @param minDate 最小日�? * @param maxDate 最大日�? * @param entries 所有课程条�? * @param selectedDayEntries 选中日期的课程条�? * @param dateRangeEntriesCache 日期范围课程缓存
 * @param nextCourseSnapshot 下一节课快照
 * @param snackbarHostState  Snackbar 主机状�? * @param importLauncher 导入启动�? * @param exportLauncher 导出启动�? * @param onAcademicImport 教务系统导入入口
 * @param reminderConfig 提醒设置与权限入�? * @param appearanceConfig 背景和周卡片外观设置
 * @param callbacks 日视图交互回�? */
@Composable
private fun DayViewContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    selectedDate: String,
    selectedLocalDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    entries: List<TimetableEntry>,
    timetableGroups: List<TimetableGroup>,
    activeGroup: TimetableGroup,
    selectedDayEntries: List<TimetableEntry>,
    dateRangeEntriesCache: DateRangeEntriesCache,
    nextCourseSnapshot: NextCourseSnapshot?,
    snackbarHostState: SnackbarHostState,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onAcademicImport: () -> Unit,
    onSelectTimetableGroup: (String) -> Unit,
    onCreateTimetableGroup: (String) -> Unit,
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
            timetableGroups = timetableGroups,
            activeGroup = activeGroup,
            selectedDate = selectedDate,
            selectedLocalDate = selectedLocalDate,
            selectedDayEntries = selectedDayEntries,
            dateRangeEntriesCache = dateRangeEntriesCache,
            nextCourseSnapshot = nextCourseSnapshot,
            importLauncher = importLauncher,
            exportLauncher = exportLauncher,
            onAcademicImport = onAcademicImport,
            onSelectTimetableGroup = onSelectTimetableGroup,
            onCreateTimetableGroup = onCreateTimetableGroup,
            reminderConfig = reminderConfig,
            appearanceConfig = appearanceConfig,
            callbacks = callbacks,
        )
    }
}

/**
 * 创建默认的新周时段�? *
 * 根据现有时段列表创建一个默认的新周时段�? *
 * @param slots 现有周时段列�? * @return 新的周时段，�?null 如果无法创建
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
 * 调整周时段列表大小�? *
 * 根据目标数量调整周时段列表，添加或删除时段�? *
 * @param slots 现有周时段列�? * @param targetCount 目标数量
 * @return 调整后的周时段列�? */
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
 * 创建下一个周时段�? *
 * 根据前一个周时段创建下一个周时段�? *
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
 * 待处理的课程冲突�? *
 * 表示课程条目更新时遇到的冲突�? *
 * @param updatedEntry 更新后的课程条目
 * @param conflictEntry 冲突的课程条�? */
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
 * 创建快速课程条目模板�? *
 * 根据现有课程条目创建一个快速课程条目模板�? *
 * @param date 日期
 * @param existingEntries 现有课程条目
 * @return 课程条目模板
 */
internal fun createQuickEntryTemplate(
    date: LocalDate,
    existingEntries: List<TimetableEntry>,
    groupId: String = TimetableGroup.DEFAULT_ID,
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
        groupId = groupId,
        title = "",
        date = date.toString(),
        dayOfWeek = date.dayOfWeek.value,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
    )
}

/**
 * 复制课程条目模板�? *
 * 创建一个现有课程条目的副本�? *
 * @param source 源课程条�? * @return 复制的课程条�? */
internal fun duplicateEntryTemplate(source: TimetableEntry): TimetableEntry {
    return TimetableEntry.create(
        groupId = source.groupId,
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
