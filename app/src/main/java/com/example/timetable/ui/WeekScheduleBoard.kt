package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

private val boardAccentColors = listOf(
    Color(0xFFF2B6CF),
    Color(0xFFE98AA9),
    Color(0xFF82A7F4),
    Color(0xFFF3BE6E),
    Color(0xFFE88974),
    Color(0xFF78D8CB),
    Color(0xFFB69AF8),
    Color(0xFF6F9ED6),
)

@Composable
fun WeekScheduleBoard(
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    entries: List<TimetableEntry>,
    slots: List<WeekTimeSlot>,
    cardAlpha: Float,
    cardHue: Float,
    cardScale: Float,
    onAddSlot: () -> Unit,
    onCustomizeSlotCount: () -> Unit,
    onEntryClick: (TimetableEntry) -> Unit,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }
    val weekNumber = remember(selectedDate) {
        selectedDate.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
    }
    val entriesByDay = remember(entries) {
        entries
            .mapNotNull { entry -> entryDate(entry)?.let { date -> date to entry } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, dayEntries) -> dayEntries.sortedBy { it.startMinutes } }
    }
    val selectedDayEntries = remember(entriesByDay, selectedDate) {
        entriesByDay[selectedDate].orEmpty()
    }
    val horizontalScrollState = rememberScrollState()

    val timeColumnWidth = 58.dp
    val dayColumnWidth = (94f * cardScale).dp
    val headerHeight = (74f * cardScale.coerceAtLeast(1f)).dp
    val slotHeight = (104f * cardScale).dp
    val gutter = 4.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x72FFFFFF)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        WeekOverviewHeader(
            selectedDate = selectedDate,
            weekStart = weekStart,
            weekEnd = weekEnd,
            weekNumber = weekNumber,
            weekEntries = entries,
            selectedDayEntries = selectedDayEntries,
            slotCount = slots.size,
            cardScale = cardScale,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            onCustomizeSlotCount = onCustomizeSlotCount,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeColumnHeader(
                    width = timeColumnWidth,
                    height = headerHeight,
                    onAddSlot = onAddSlot,
                )
                days.forEach { day ->
                    DayHeaderCell(
                        day = day,
                        width = dayColumnWidth,
                        height = headerHeight,
                        selected = day == selectedDate,
                    )
                }
            }

            slots.forEachIndexed { index, slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight),
                    verticalAlignment = Alignment.Top,
                ) {
                    TimeSlotCell(
                        index = index,
                        slot = slot,
                        width = timeColumnWidth,
                        height = slotHeight,
                        onClick = { onSlotClick(index) },
                    )

                    days.forEach { day ->
                        val slotEntries = entriesByDay[day].orEmpty().filter { entry ->
                            entry.startMinutes < slot.endMinutes && slot.startMinutes < entry.endMinutes
                        }

                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .height(slotHeight)
                                .padding(horizontal = gutter / 2, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (day == selectedDate) Color(0x22FFFFFF) else Color(0x14FFFFFF)),
                        ) {
                            if (slotEntries.isEmpty()) {
                                Box(modifier = Modifier.matchParentSize())
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(3.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    slotEntries.take(2).forEach { entry ->
                                        val color = boardAccentColors[
                                            (entry.title.hashCode() and Int.MAX_VALUE) % boardAccentColors.size
                                        ]
                                        WeekEntryBlock(
                                            modifier = Modifier.fillMaxWidth(),
                                            entry = entry,
                                            color = colorWithHueShift(color, cardHue).copy(alpha = cardAlpha),
                                            compact = true,
                                            onClick = { onEntryClick(entry) },
                                        )
                                    }
                                    if (slotEntries.size > 2) {
                                        Text(
                                            text = "+${slotEntries.size - 2}",
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .padding(end = 4.dp, top = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4A5367),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekOverviewHeader(
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    weekNumber: Int,
    weekEntries: List<TimetableEntry>,
    selectedDayEntries: List<TimetableEntry>,
    slotCount: Int,
    cardScale: Float,
    onCustomizeSlotCount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${selectedDate.year}/${selectedDate.monthValue}/${selectedDate.dayOfMonth}",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal),
                    color = Color(0xFF111319),
                )
                Text(
                    text = "第 $weekNumber 周  ${formatWeekRange(weekStart, weekEnd)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF3A4050),
                )
                Text(
                    text = if (weekEntries.isEmpty()) {
                        "本周暂无课程"
                    } else {
                        "本周 ${weekEntries.size} 节，今天 ${selectedDayEntries.size} 节"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF677086),
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassActionChip(
                    label = "节数 $slotCount",
                    onClick = onCustomizeSlotCount,
                )
                WeekMiniStats(
                    selectedCount = selectedDayEntries.size,
                    weekCount = weekEntries.size,
                    scalePercent = (cardScale * 100).toInt(),
                )
            }
        }
    }
}

@Composable
private fun WeekMiniStats(
    selectedCount: Int,
    weekCount: Int,
    scalePercent: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryPill(label = "今日", value = selectedCount.toString())
        SummaryPill(label = "本周", value = weekCount.toString())
        SummaryPill(label = "大小", value = "$scalePercent%")
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
) {
    Surface(
        color = Color(0x48FFFFFF),
        contentColor = Color(0xFF111319),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF667085),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun TimeColumnHeader(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onAddSlot: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(end = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onAddSlot,
            shape = CircleShape,
            color = Color(0x52FFFFFF),
            contentColor = Color(0xFF111319),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新增节次",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: LocalDate,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    selected: Boolean,
) {
    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0x42FFFFFF) else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = chineseWeekday(day.dayOfWeek),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF495164),
        )
        Text(
            text = "${day.monthValue}/${day.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF778096),
        )
    }
}

@Composable
private fun TimeSlotCell(
    index: Int,
    slot: WeekTimeSlot,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x44FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF10131A),
            )
            Text(
                text = formatMinutes(slot.startMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E3442),
            )
            Text(
                text = formatMinutes(slot.endMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E3442),
            )
        }
    }
}

@Composable
private fun GlassActionChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color(0x52FFFFFF),
        contentColor = Color(0xFF111319),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun WeekEntryBlock(
    modifier: Modifier,
    entry: TimetableEntry,
    color: Color,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                },
                color = Color.White,
                maxLines = if (compact) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${formatMinutes(entry.startMinutes)}-${formatMinutes(entry.endMinutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Start,
            )
        }
    }
}

private fun entryDate(entry: TimetableEntry): LocalDate? = com.example.timetable.data.parseEntryDate(entry.date)

internal fun chineseWeekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    return if (start.month == end.month) {
        "${start.monthValue}/${start.dayOfMonth} - ${end.dayOfMonth}"
    } else {
        "${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
    }
}

internal fun colorWithHueShift(base: Color, hueShift: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = (hsv[0] + hueShift) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
