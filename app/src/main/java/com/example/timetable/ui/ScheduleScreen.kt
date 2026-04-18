package com.example.timetable.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatDateLabel
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var backgroundImageUri by remember(context) { mutableStateOf(AppearanceStore.getBackgroundImageUri(context)) }
    var weekCardAlpha by remember(context) { mutableStateOf(AppearanceStore.getWeekCardAlpha(context)) }

    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    val initialDate = LocalDate.now().takeIf { it in minDate..maxDate } ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var isWeekMode by rememberSaveable { mutableStateOf(false) }
    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    val selectedWeekStart = remember(selectedLocalDate) {
        selectedLocalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val selectedWeekEnd = remember(selectedWeekStart) { selectedWeekStart.plusDays(6) }
    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var reminderMinutes by remember { mutableStateOf(CourseReminderScheduler.getReminderMinutes(context)) }
    val reminderOptions = remember { CourseReminderScheduler.reminderMinuteOptions() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (granted) {
                snackbarHostState.showSnackbar("已开启手机通知提醒")
            } else {
                snackbarHostState.showSnackbar("未授予通知权限，可能无法收到提醒")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
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
                runCatching {
                    val text = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    }
                    snackbarHostState.showSnackbar("已导出日历")
                }.onFailure {
                    snackbarHostState.showSnackbar("导出失败：${it.message ?: "未知错误"}")
                }
            }
        }
    }

    val backgroundImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                AppearanceStore.setBackgroundImageUri(context, uri)
                backgroundImageUri = uri.toString()
            }.onSuccess {
                scope.launch {
                    snackbarHostState.showSnackbar("已更新背景图")
                }
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar("设置背景图失败：${it.message ?: "未知错误"}")
                }
            }
        }
    }

    val filteredEntries = remember(entries, selectedDate, isWeekMode, selectedWeekStart, selectedWeekEnd) {
        entries.filter { entry ->
            val entryDate = parseEntryDate(entry.date) ?: return@filter false
            if (isWeekMode) entryDate in selectedWeekStart..selectedWeekEnd else entry.date == selectedDate
        }.sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundLayer(backgroundImageUri = backgroundImageUri)

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "我的课程表",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingEntry = TimetableEntry(
                            title = "",
                            date = selectedDate,
                            dayOfWeek = selectedLocalDate.dayOfWeek.value,
                            startMinutes = 8 * 60,
                            endMinutes = 9 * 60,
                        )
                    },
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新增课程")
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedDate, isWeekMode) {
                        var totalHorizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                totalHorizontalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalHorizontalDrag > 80f -> {
                                        val previousDate = selectedLocalDate.minusDays(if (isWeekMode) 7 else 1)
                                        if (previousDate >= minDate) {
                                            selectedDate = previousDate.toString()
                                        }
                                    }
                                    totalHorizontalDrag < -80f -> {
                                        val nextDate = selectedLocalDate.plusDays(if (isWeekMode) 7 else 1)
                                        if (nextDate <= maxDate) {
                                            selectedDate = nextDate.toString()
                                        }
                                    }
                                }
                                totalHorizontalDrag = 0f
                            },
                        )
                    }
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    state = listState,
                ) {
                    item {
                        HeroSection(
                            courseCount = entries.size,
                            onImport = {
                                importLauncher.launch(
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
                            onExport = { exportLauncher.launch("课程表导出.ics") },
                            onEnableNotifications = {
                                when {
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("当前系统版本无需额外通知授权")
                                        }
                                    }
                                    CourseReminderScheduler.notificationsEnabled(context) ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) == PackageManager.PERMISSION_GRANTED -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("通知权限已开启")
                                        }
                                    }
                                    else -> {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                            reminderMinutes = reminderMinutes,
                            reminderOptions = reminderOptions,
                            onReminderMinutesChange = { minutes ->
                                reminderMinutes = minutes
                                viewModel.updateReminderMinutes(minutes)
                            },
                            hasCustomBackground = backgroundImageUri != null,
                            onImportBackground = {
                                backgroundImportLauncher.launch(arrayOf("image/*"))
                            },
                            onClearBackground = {
                                AppearanceStore.clearBackgroundImage(context)
                                backgroundImageUri = null
                                scope.launch { snackbarHostState.showSnackbar("已恢复默认背景") }
                            },
                            weekCardAlpha = weekCardAlpha,
                            onWeekCardAlphaChange = { alpha ->
                                weekCardAlpha = alpha
                                AppearanceStore.setWeekCardAlpha(context, alpha)
                            },
                        )
                    }
                    item {
                        PerpetualCalendar(
                            selectedDate = selectedDate,
                            entries = entries,
                            onDateChanged = { selectedDate = it },
                        )
                    }
                    item {
                        ViewModeSwitcher(
                            isWeekMode = isWeekMode,
                            onModeChange = { isWeekMode = it },
                        )
                    }
                    if (isWeekMode) {
                        item {
                            WeekScheduleBoard(
                                selectedDate = selectedLocalDate,
                                weekStart = selectedWeekStart,
                                weekEnd = selectedWeekEnd,
                                entries = filteredEntries,
                                cardAlpha = weekCardAlpha,
                            )
                        }
                    } else {
                        item {
                            SectionHeader(title = formatDateLabel(selectedDate))
                        }
                        if (filteredEntries.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    onAdd = {
                                        editingEntry = TimetableEntry(
                                            title = "",
                                            date = selectedDate,
                                            dayOfWeek = selectedLocalDate.dayOfWeek.value,
                                            startMinutes = 8 * 60,
                                            endMinutes = 9 * 60,
                                        )
                                    },
                                )
                            }
                        } else {
                            items(filteredEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    onEdit = { editingEntry = entry },
                                    onDelete = { viewModel.deleteEntry(entry.id) },
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(56.dp)) }
                }
            }
        }
    }

    editingEntry?.let { entry ->
        EntryEditorDialog(
            initial = entry,
            onDismiss = { editingEntry = null },
            onSave = {
                viewModel.upsertEntry(it)
                editingEntry = null
            },
        )
    }
}
