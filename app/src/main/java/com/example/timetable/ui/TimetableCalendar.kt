package com.example.timetable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.parseEntryDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 轻量级水平周日历选择器
 * 使用 LazyRow 替代全月网格，大幅降低渲染开销
 *
 * @param selectedDate 当前选中的日期（yyyy-MM-dd）
 * @param entries 所有课程，用于渲染小圆点提示
 * @param onDateChanged 日期切换回调
 */
@Composable
fun PerpetualCalendar(
    selectedDate: String,
    entries: List<TimetableEntry>,
    onDateChanged: (String) -> Unit,
) {
    val today = LocalDate.now()
    val selected = parseEntryDate(selectedDate) ?: today

    // 以选中日期所在月份为基准显示月份文字
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selected)) }

    // 跨月切换时自动同步（不覆盖用户手动翻页）
    LaunchedEffect(selected) {
        val selectedMonth = YearMonth.from(selected)
        if (selectedMonth != visibleMonth) {
            visibleMonth = selectedMonth
        }
    }

    // 预生成当前月所有日期
    val daysInMonth = remember(visibleMonth) {
        val start = visibleMonth.atDay(1)
        val len = visibleMonth.lengthOfMonth()
        (0 until len).map { start.plusDays(it.toLong()) }
    }

    val entriesByDate = remember(entries) { entries.groupingBy { it.date }.eachCount() }

    val listState = rememberLazyListState()

    // 当选中日期改变时自动滚动到对应位置
    LaunchedEffect(selected, daysInMonth) {
        val idx = daysInMonth.indexOfFirst { it == selected }
        if (idx >= 0) {
            listState.animateScrollToItem(idx, scrollOffset = -20)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 月份切换行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "上月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "下月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 水平滚动日期行
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(daysInMonth.size, key = { daysInMonth[it].toEpochDay() }) { idx ->
                    val date = daysInMonth[idx]
                    val isSelected = date == selected
                    val isToday = date == today
                    val hasCourse = (entriesByDate[date.toString()] ?: 0) > 0

                    val bgColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dateBg",
                    )
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dateText",
                    )
                    val subTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "subText",
                    )

                    Column(
                        modifier = Modifier
                            .width(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable { onDateChanged(date.toString()) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 星期缩写
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor,
                            textAlign = TextAlign.Center,
                        )
                        // 日期数字
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = textColor,
                            textAlign = TextAlign.Center,
                        )
                        // 有课指示小点
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasCourse) {
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.primary
                                    } else Color.Transparent
                                ),
                        )
                    }
                }
            }
        }
    }
}
