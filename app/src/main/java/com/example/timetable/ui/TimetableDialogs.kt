package com.example.timetable.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timetable.data.*


/**
 * 课程编辑器对话框
 * 提供表单用于添加或编辑课程信息
 *
 * @param initial 初始课程数据
 * @param onDismiss 取消编辑回调
 * @param onSave 保存课程回调
 */
@Composable
fun EntryEditorDialog(
    initial: TimetableEntry,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit,
) {
    // 表单字段状态
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var dateText by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var startTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var location by rememberSaveable(initial.id) { mutableStateOf(initial.location) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var errorText by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) "新增课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 课程名称输入框
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorText = null
                    },
                    label = "课程名称",
                    singleLine = true,
                )
                // 日期输入
                AppTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        errorText = null
                    },
                    label = "日期",
                    placeholder = "2026-09-01",
                    singleLine = true,
                )
                // 开始和结束时间输入
                TimeRangeFields(
                    startTime = startTime,
                    endTime = endTime,
                    onStartChanged = {
                        startTime = it
                        errorText = null
                    },
                    onEndChanged = {
                        endTime = it
                        errorText = null
                    },
                )
                // 地点输入框
                AppTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "地点",
                    singleLine = true,
                )
                // 备注输入框
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注",
                    minLines = 2,
                )
                // 错误提示文本
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedStart = parseMinutes(startTime)
                val parsedEnd = parseMinutes(endTime)
                val parsedDate = parseEntryDate(dateText)
                when {
                    title.trim().isBlank() -> errorText = "请输入课程名称"
                    title.trim().length > 64 -> errorText = "课程名称不能超过 64 字"
                    parsedDate == null -> errorText = "请输入合法日期，范围 1970-01-01 到 2100-12-31"
                    parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                    parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                    location.trim().length > 64 -> errorText = "地点不能超过 64 字"
                    note.trim().length > 256 -> errorText = "备注不能超过 256 字"
                    else -> onSave(
                        initial.copy(
                            title = title.trim(),
                            date = parsedDate.toString(),
                            dayOfWeek = parsedDate.dayOfWeek.value,
                            startMinutes = parsedStart,
                            endMinutes = parsedEnd,
                            location = location.trim(),
                            note = note.trim(),
                        )
                    )
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun TimeRangeFields(
    startTime: String,
    endTime: String,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
            value = startTime,
            onValueChange = onStartChanged,
            label = "开始时间",
            placeholder = "08:00",
            keyboardType = KeyboardType.Text,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = endTime,
            onValueChange = onEndChanged,
            label = "结束时间",
            placeholder = "09:30",
            keyboardType = KeyboardType.Text,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun WeekSlotEditorDialog(
    slotNumber: Int,
    initial: WeekTimeSlot,
    onDismiss: () -> Unit,
    onSave: (WeekTimeSlot) -> Unit,
) {
    var startTime by rememberSaveable(slotNumber) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(slotNumber) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 $slotNumber 节时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeRangeFields(
                    startTime = startTime,
                    endTime = endTime,
                    onStartChanged = {
                        startTime = it
                        errorText = null
                    },
                    onEndChanged = {
                        endTime = it
                        errorText = null
                    },
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedStart = parseMinutes(startTime)
                    val parsedEnd = parseMinutes(endTime)
                    when {
                        parsedStart == null || parsedEnd == null -> {
                            errorText = "请输入合法时间，例如 08:00"
                        }
                        parsedStart >= parsedEnd -> {
                            errorText = "结束时间需要晚于开始时间"
                        }
                        else -> onSave(WeekTimeSlot(parsedStart, parsedEnd))
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
