package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.parseMinutes

@Composable
fun EntryEditorDialog(
    initial: TimetableEntry,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit,
) {
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var dateText by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var startTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var location by rememberSaveable(initial.id) { mutableStateOf(initial.location) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) "新增课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorText = null
                    },
                    label = "课程名称",
                    singleLine = true,
                )
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
                AppTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "地点",
                    singleLine = true,
                )
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注",
                    minLines = 2,
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
                    val parsedDate = parseEntryDate(dateText)
                    when {
                        title.trim().isBlank() -> errorText = "请输入课程名称"
                        title.trim().length > 64 -> errorText = "课程名称不能超过 64 个字符"
                        parsedDate == null -> errorText = "请输入合法日期，范围 1970-01-01 到 2100-12-31"
                        parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                        parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                        location.trim().length > 64 -> errorText = "地点不能超过 64 个字符"
                        note.trim().length > 256 -> errorText = "备注不能超过 256 个字符"
                        else -> onSave(
                            initial.copy(
                                title = title.trim(),
                                date = parsedDate.toString(),
                                dayOfWeek = parsedDate.dayOfWeek.value,
                                startMinutes = parsedStart,
                                endMinutes = parsedEnd,
                                location = location.trim(),
                                note = note.trim(),
                            ),
                        )
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
    title: String,
    initial: WeekTimeSlot,
    onDismiss: () -> Unit,
    onSave: (WeekTimeSlot) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var startTime by rememberSaveable(title) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(title) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                        parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                        parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                        else -> onSave(WeekTimeSlot(parsedStart, parsedEnd))
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDelete?.let {
                    OutlinedButton(onClick = it) {
                        Text("删除")
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
fun WeekSlotCountDialog(
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var countText by rememberSaveable(initialCount) { mutableStateOf(initialCount.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义节数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = countText,
                    onValueChange = {
                        countText = it
                        errorText = null
                    },
                    label = "总节数",
                    placeholder = "20",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )
                Text(
                    text = "会保留前面的节次时间，多出来的节次自动补在后面，减少时从末尾裁剪。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = countText.toIntOrNull()
                    when {
                        parsed == null -> errorText = "请输入数字"
                        parsed !in 1..20 -> errorText = "节数范围为 1 到 20"
                        else -> onSave(parsed)
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
