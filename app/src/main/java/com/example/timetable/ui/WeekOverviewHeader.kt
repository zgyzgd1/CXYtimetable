package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.TimetableEntry
import java.time.LocalDate

/**
 * 周视图概览头部。
 *
 * 显示当前选中日期、周次、课程统计等信息。
 */
@Composable
fun WeekOverviewHeader(
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    weekNumber: Int,
    weekEntries: List<TimetableEntry>,
    selectedDayEntries: List<TimetableEntry>,
    slotCount: Int,
    onCustomizeSlotCount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${selectedDate.year}/${selectedDate.monthValue}/${selectedDate.dayOfMonth}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.label_week_number, weekNumber) + "  " + formatWeekRange(weekStart, weekEnd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (weekEntries.isEmpty()) {
                        stringResource(R.string.label_week_no_courses)
                    } else {
                        stringResource(R.string.label_week_course_count, weekEntries.size, selectedDayEntries.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.hintContent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                GlassActionChip(
                    label = stringResource(R.string.label_slot_count, slotCount),
                    onClick = onCustomizeSlotCount,
                )
                WeekMiniStats(
                    selectedCount = selectedDayEntries.size,
                    weekCount = weekEntries.size,
                )
            }
        }
    }
}

@Composable
private fun WeekMiniStats(
    selectedCount: Int,
    weekCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryPill(label = stringResource(R.string.label_today), value = selectedCount.toString())
        SummaryPill(label = stringResource(R.string.label_this_week), value = weekCount.toString())
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.weekBoard(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = AppShape.Chip,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
internal fun GlassActionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.overlayHeavy(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = AppShape.Chip,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
}

internal fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    return if (start.month == end.month) {
        "${start.monthValue}/${start.dayOfMonth} - ${end.dayOfMonth}"
    } else {
        "${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
    }
}
