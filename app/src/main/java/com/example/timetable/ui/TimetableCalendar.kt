package com.example.timetable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun PerpetualCalendar(
    selectedDate: String,
    entries: List<TimetableEntry>,
    onDateChanged: (String) -> Unit,
) {
    val today = LocalDate.now()
    val selected = parseEntryDate(selectedDate) ?: today

    var visibleMonth by remember { mutableStateOf(YearMonth.from(selected)) }

    LaunchedEffect(selected) {
        val selectedMonth = YearMonth.from(selected)
        if (selectedMonth != visibleMonth) {
            visibleMonth = selectedMonth
        }
    }

    val daysInMonth = remember(visibleMonth) {
        val start = visibleMonth.atDay(1)
        (0 until visibleMonth.lengthOfMonth()).map { start.plusDays(it.toLong()) }
    }
    val entriesByDate = remember(entries) { entries.groupingBy { it.date }.eachCount() }
    val listState = rememberLazyListState()

    LaunchedEffect(selected, daysInMonth) {
        val idx = daysInMonth.indexOfFirst { it == selected }
        if (idx >= 0) {
            listState.animateScrollToItem(idx, scrollOffset = -20)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "上月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "下月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(daysInMonth, key = { it.toEpochDay() }) { date ->
                    val isSelected = date == selected
                    val isToday = date == today
                    val hasCourse = (entriesByDate[date.toString()] ?: 0) > 0

                    val containerColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "calendarContainer",
                    )
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "calendarText",
                    )

                    Card(
                        modifier = Modifier
                            .width(54.dp)
                            .clickable { onDateChanged(date.toString()) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = textColor,
                                textAlign = TextAlign.Center,
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasCourse) textColor else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
