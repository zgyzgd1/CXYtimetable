package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 精简版英雄区域组件
 * 渐变背景 + 横向三栏胶囊按钮，减少竖向空间占用
 *
 * @param courseCount 课程总数
 * @param onImport 导入回调
 * @param onExport 导出回调
 * @param onEnableNotifications 通知权限回调
 * @param reminderMinutes 当前提醒分钟数
 * @param reminderOptions 可选提醒分钟列表
 * @param onReminderMinutesChange 提醒分钟改变回调
 */
@Composable
fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onEnableNotifications: () -> Unit,
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (Int) -> Unit,
) {
    var showReminderSheet by remember { mutableStateOf(false) }

    // 主 Hero 卡片——渐变背景
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // 标题行
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "课程表助手",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "当前共 $courseCount 门课程 · 支持 .ics 导入 / 导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }

            // 三个操作按钮横向排布
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroActionChip(
                    icon = Icons.Default.Download,
                    label = "导入",
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.Upload,
                    label = "导出",
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.NotificationsActive,
                    label = "提醒 ${reminderMinutes}m",
                    onClick = { showReminderSheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 提醒时间选择对话框
    if (showReminderSheet) {
        ReminderPickerDialog(
            reminderMinutes = reminderMinutes,
            reminderOptions = reminderOptions,
            onDismiss = { showReminderSheet = false },
            onSelect = {
                onReminderMinutesChange(it)
                showReminderSheet = false
            },
            onEnableNotifications = {
                onEnableNotifications()
                showReminderSheet = false
            },
        )
    }
}

/**
 * Hero 区域内的操作小胶囊按钮
 */
@Composable
private fun HeroActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 提醒时间选择对话框（紧凑）
 */
@Composable
private fun ReminderPickerDialog(
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("课前提醒时间", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "选择课程开始前多久接到提醒通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminderOptions.forEach { option ->
                        val selected = option == reminderMinutes
                        if (selected) {
                            Button(
                                onClick = { onSelect(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        } else {
                            OutlinedButton(
                                onClick = { onSelect(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEnableNotifications) { Text("开启通知权限") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

/**
 * 分区标题组件
 */
@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "按时间顺序",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
