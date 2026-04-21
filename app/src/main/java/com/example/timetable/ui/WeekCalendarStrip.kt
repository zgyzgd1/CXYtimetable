package com.example.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun WeekCalendarStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDate = remember { LocalDate.of(2020, 1, 1) }
    val endDate = remember { LocalDate.of(2035, 12, 31) }
    val selectedDayIndex = remember(selectedDate) { selectedDate.dayOfWeek.value - 1 }
    val latestSelectedDate by rememberUpdatedState(selectedDate)
    val state = rememberWeekCalendarState(
        startDate = startDate,
        endDate = endDate,
        firstVisibleWeekDate = selectedDate,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    LaunchedEffect(selectedDate) {
        val visibleDates = state.firstVisibleWeek.days.map { it.date }
        if (selectedDate !in visibleDates) {
            state.animateScrollToDate(selectedDate)
        }
    }

    LaunchedEffect(state, selectedDayIndex) {
        snapshotFlow { state.firstVisibleWeek.days.map { it.date } }
            .map { visibleWeek ->
                visibleWeek.getOrNull(selectedDayIndex) ?: visibleWeek.first()
            }
            .distinctUntilChanged()
            .collect { visibleAnchorDate ->
                if (visibleAnchorDate != latestSelectedDate) {
                    onDateSelected(visibleAnchorDate)
                }
            }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "周选择",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            WeekCalendar(
                modifier = Modifier.fillMaxWidth(),
                state = state,
                dayContent = { day ->
                    WeekCalendarDayCell(
                        date = day.date,
                        selected = day.date == selectedDate,
                        today = day.date == LocalDate.now(),
                        onClick = { onDateSelected(day.date) },
                    )
                },
            )
        }
    }
}

@Composable
private fun WeekCalendarDayCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        today -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        today -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = chineseWeekday(date.dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentColor,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (today) contentColor else Color.Transparent)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (today) "今" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = containerColor,
                )
            }
        }
    }
}
