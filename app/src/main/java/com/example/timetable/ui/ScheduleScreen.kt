package com.example.timetable.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatDateLabel
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 课程表主应用界面
 * 整合所有 UI 组件，管理应用状态和用户交互
 *
 * @param viewModel 视图模型实例，提供数据和业务逻辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    // 收集课程列表状态
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    // Snackbar 主机状态，用于显示提示消息
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // 当前选中的日期，支持较广时间范围
    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    val initialDate = LocalDate.now().takeIf { it in minDate..maxDate } ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    // 正在编辑的课程条目，null 表示没有在编辑
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

    // 监听视图模型的消息并显示 Snackbar
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 创建导入文件的启动器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromIcs(context.contentResolver, uri)
        }
    }

    // 创建导出文件的启动器
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

    // 根据选中的日期过滤课程列表
    val filteredEntries = remember(entries, selectedDate) {
        entries.filter { it.date == selectedDate }.sortedBy { it.startMinutes }
    }

    // 构建应用的主框架
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "我的课程表",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            // 浮动操作按钮：新增课程
            FloatingActionButton(onClick = {
                editingEntry = TimetableEntry(
                    title = "",
                    date = selectedDate,
                    dayOfWeek = selectedLocalDate.dayOfWeek.value,
                    startMinutes = 8 * 60,
                    endMinutes = 9 * 60,
                )
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增课程")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        )
                    )
                )
                .pointerInput(selectedDate) {
                    var totalHorizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            totalHorizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                // 右滑：查看昨天
                                totalHorizontalDrag > 80f -> {
                                    val previousDate = selectedLocalDate.minusDays(1)
                                    if (previousDate >= minDate) {
                                        selectedDate = previousDate.toString()
                                    }
                                }
                                // 左滑：查看明天
                                totalHorizontalDrag < -80f -> {
                                    val nextDate = selectedLocalDate.plusDays(1)
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
            // 可滚动的课程列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                state = listState,
            ) {
                // 顶部英雄区域：显示统计信息和操作按钮
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
                                )
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
                    )
                }
                // 日期切换
                item {
                    PerpetualCalendar(
                        selectedDate = selectedDate,
                        entries = entries,
                        onDateChanged = { selectedDate = it },
                    )
                }
                // 当前选中日期的标题
                item {
                    SectionHeader(title = formatDateLabel(selectedDate))
                }
                // 如果没有课程，显示空状态卡片
                if (filteredEntries.isEmpty()) {
                    item {
                        EmptyStateCard(onAdd = {
                            editingEntry = TimetableEntry(
                                title = "",
                                date = selectedDate,
                                dayOfWeek = selectedLocalDate.dayOfWeek.value,
                                startMinutes = 8 * 60,
                                endMinutes = 9 * 60,
                            )
                        })
                    }
                } else {
                    // 显示课程列表
                    items(filteredEntries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onEdit = { editingEntry = entry },
                            onDelete = { viewModel.deleteEntry(entry.id) },
                        )
                    }
                }
                // 底部留白
                item { Spacer(modifier = Modifier.height(56.dp)) }
            }
        }
    }

    // 课程编辑器对话框
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

