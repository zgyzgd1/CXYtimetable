package com.example.timetable.ui

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import androidx.compose.ui.res.stringResource
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

// Course accent colors are unified via accentColorFor() + LocalCourseAccentColors

@Composable
fun WeekScheduleBoard(
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    entriesByDay: Map<LocalDate, List<TimetableEntry>>,
    slots: List<WeekTimeSlot>,
    cardAlpha: Float,
    cardHue: Float,
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
    val weekEntries = remember(entriesByDay, days) {
        days.flatMap { day -> entriesByDay[day].orEmpty() }
    }
    val selectedDayEntries = remember(entriesByDay, selectedDate) {
        entriesByDay[selectedDate].orEmpty()
    }
    val horizontalScrollState = rememberScrollState()

    val timeColumnWidth = 60.dp
    val headerHeight = 80.dp
    val slotHeight = 120.dp
    val slotGap = 8.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth - 16.dp // subtract horizontal padding
        val minDayColumnWidth = 100.dp
        val dayColumnWidth = ((availableWidth - timeColumnWidth) / 7f).coerceAtLeast(minDayColumnWidth)
        val laneHeight = calculateLaneHeight(slots.size, slotHeight, slotGap)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShape.CardLarge)
                .background(MaterialTheme.colorScheme.surface.weekBoard())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.overlayActive(),
                    shape = AppShape.CardLarge,
                ),
        ) {
            WeekOverviewHeader(
                selectedDate = selectedDate,
                weekStart = weekStart,
                weekEnd = weekEnd,
                weekNumber = weekNumber,
                weekEntries = weekEntries,
                selectedDayEntries = selectedDayEntries,
                slotCount = slots.size,
                onCustomizeSlotCount = onCustomizeSlotCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    TimeSlotColumn(
                        width = timeColumnWidth,
                        slots = slots,
                        slotHeight = slotHeight,
                        slotGap = slotGap,
                        onSlotClick = onSlotClick,
                    )
                    days.forEach { day ->
                        WeekDayLane(
                            day = day,
                            selected = day == selectedDate,
                            entries = entriesByDay[day].orEmpty(),
                            slots = slots,
                            width = dayColumnWidth,
                            laneHeight = laneHeight,
                            slotHeight = slotHeight,
                            slotGap = slotGap,
                            cardAlpha = cardAlpha,
                            cardHue = cardHue,
                            onEntryClick = onEntryClick,
                        )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.overlayDisabled(),
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
private fun TimeColumnHeader(
    width: Dp,
    height: Dp,
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
            color = MaterialTheme.colorScheme.surfaceVariant.overlaySelected(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.title_add_slot),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeSlotColumn(
    width: Dp,
    slots: List<WeekTimeSlot>,
    slotHeight: Dp,
    slotGap: Dp,
    onSlotClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(slotGap),
    ) {
        slots.forEachIndexed { index, slot ->
            TimeSlotCell(
                index = index,
                slot = slot,
                width = width,
                height = slotHeight,
                onClick = { onSlotClick(index) },
            )
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: LocalDate,
    width: Dp,
    height: Dp,
    selected: Boolean,
) {
    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 2.dp)
            .clip(AppShape.CardSmall)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.overlaySelected() else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekdayLabel(day.dayOfWeek, LocalContext.current),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${day.monthValue}/${day.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeekDayLane(
    day: LocalDate,
    selected: Boolean,
    entries: List<TimetableEntry>,
    slots: List<WeekTimeSlot>,
    width: Dp,
    laneHeight: Dp,
    slotHeight: Dp,
    slotGap: Dp,
    cardAlpha: Float,
    cardHue: Float,
    onEntryClick: (TimetableEntry) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(width)
            .height(laneHeight)
            .padding(horizontal = 3.dp),
    ) {
        val density = LocalDensity.current
        val laneWidthPx = with(density) { maxWidth.toPx() }
        val slotHeightPx = with(density) { slotHeight.toPx() }
        val slotGapPx = with(density) { slotGap.toPx() }
        val layouts = remember(entries, slots, laneWidthPx, slotHeightPx, slotGapPx) {
            buildWeekEntryLayouts(
                entries = entries,
                slots = slots,
                laneWidthPx = laneWidthPx,
                slotHeightPx = slotHeightPx,
                slotGapPx = slotGapPx,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(slotGap),
        ) {
            slots.forEach {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight)
                        .clip(AppShape.CardSmall)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.accentMedium() else MaterialTheme.colorScheme.surfaceVariant.overlayLightest()),
                )
            }
        }

        val courseColors = LocalCourseAccentColors.current
        layouts.forEach { layout ->
            val color = accentColorFor(layout.entry.title, courseColors)
            WeekEntryBlock(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { layout.leftPx.toDp() },
                        y = with(density) { layout.topPx.toDp() },
                    )
                    .width(with(density) { layout.widthPx.toDp() })
                    .height(with(density) { layout.heightPx.toDp() }),
                entry = layout.entry,
                color = colorWithHueShift(color, cardHue).copy(alpha = cardAlpha.coerceIn(0f, 1f)),
                onClick = { onEntryClick(layout.entry) },
            )
        }

        if (entries.isEmpty() && selected && slots.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.card_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.overlayVeryFaint(),
                )
            }
        }
    }
}

@Composable
private fun TimeSlotCell(
    index: Int,
    slot: WeekTimeSlot,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val slotContext = LocalContext.current
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(AppShape.Chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.overlayHover())
            .semantics {
                role = Role.Button
                contentDescription = buildTimeSlotContentDescription(slotContext, index, slot)
            }
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMinutes(slot.startMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMinutes(slot.endMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        color = MaterialTheme.colorScheme.surfaceVariant.overlaySelected(),
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

@Composable
private fun WeekEntryBlock(
    modifier: Modifier,
    entry: TimetableEntry,
    color: Color,
    onClick: () -> Unit,
) {
    val entryContext = LocalContext.current
    BoxWithConstraints(
        modifier = modifier
            .clip(AppShape.CardSmall)
            .border(
                width = 1.dp,
                color = Color.White.overlayActiveLight(),
                shape = AppShape.CardSmall,
            )
            .background(color)
            .semantics {
                role = Role.Button
                contentDescription = buildWeekEntryContentDescription(entryContext, entry)
            }
            .clickable(onClick = onClick),
    ) {
        val showLocation = entry.location.isNotBlank() && maxHeight >= 88.dp
        val showNote = entry.note.isNotBlank() && maxHeight >= 170.dp
        val largeCard = maxHeight >= 120.dp
        val titleLines = when {
            maxHeight >= 220.dp -> 8
            maxHeight >= 140.dp -> 6
            else -> 4
        }
        val innerPadding = if (maxHeight >= 120.dp) 10.dp else 6.dp
        val innerSpacing = if (maxHeight >= 120.dp) 4.dp else 2.dp
        // Ensure readable text: use dark text when card color is too light
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        val textColor = if (luminance > 0.5f) MaterialTheme.colorScheme.onSurface.overlayPrimaryMedium() else Color.White
        val subTextColor = if (luminance > 0.5f) MaterialTheme.colorScheme.onSurfaceVariant.overlayFaint() else Color.White.overlayPrimaryHigh()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = innerPadding, vertical = innerPadding),
            verticalArrangement = Arrangement.spacedBy(innerSpacing),
        ) {
            Text(
                text = entry.title,
                style = if (largeCard) {
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                },
                color = textColor,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (showLocation) {
                Text(
                    text = entry.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor,
                    maxLines = if (showNote) 4 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showNote) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${formatMinutes(entry.startMinutes)}-${formatMinutes(entry.endMinutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = subTextColor,
            )
        }
    }
}

internal data class WeekEntryLayout(
    val entry: TimetableEntry,
    val topPx: Float,
    val heightPx: Float,
    val leftPx: Float,
    val widthPx: Float,
)

private data class EntryColumnAssignment(
    val entry: TimetableEntry,
    val columnIndex: Int,
    val columnCount: Int,
)

private data class MutableEntryColumnAssignment(
    val entry: TimetableEntry,
    val columnIndex: Int,
)

private data class TimeAnchor(
    val minute: Int,
    val positionPx: Float,
)

internal fun weekdayLabel(dayOfWeek: DayOfWeek, context: Context): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> context.getString(R.string.weekday_mon)
    DayOfWeek.TUESDAY -> context.getString(R.string.weekday_tue)
    DayOfWeek.WEDNESDAY -> context.getString(R.string.weekday_wed)
    DayOfWeek.THURSDAY -> context.getString(R.string.weekday_thu)
    DayOfWeek.FRIDAY -> context.getString(R.string.weekday_fri)
    DayOfWeek.SATURDAY -> context.getString(R.string.weekday_sat)
    DayOfWeek.SUNDAY -> context.getString(R.string.weekday_sun)
}

private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    return if (start.month == end.month) {
        "${start.monthValue}/${start.dayOfMonth} - ${end.dayOfMonth}"
    } else {
        "${start.monthValue}/${start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
    }
}

private fun calculateLaneHeight(slotCount: Int, slotHeight: Dp, slotGap: Dp): Dp {
    if (slotCount <= 0) return 0.dp
    return slotHeight * slotCount.toFloat() + slotGap * (slotCount - 1).toFloat()
}

internal fun buildWeekEntryLayouts(
    entries: List<TimetableEntry>,
    slots: List<WeekTimeSlot>,
    laneWidthPx: Float,
    slotHeightPx: Float,
    slotGapPx: Float,
): List<WeekEntryLayout> {
    if (entries.isEmpty() || slots.isEmpty() || laneWidthPx <= 0f) return emptyList()

    val sortedSlots = slots.sortedBy { it.startMinutes }
    val firstMinute = sortedSlots.first().startMinutes
    val lastMinute = sortedSlots.last().endMinutes
    val anchors = buildLaneTimeAnchors(sortedSlots, slotHeightPx, slotGapPx)
    val columnGapPx = 6f

    return assignEntryColumns(
        entries = entries.filter { it.endMinutes > firstMinute && it.startMinutes < lastMinute },
    ).map { assignment ->
        val top = mapMinuteToLanePosition(
            minute = assignment.entry.startMinutes.coerceAtLeast(firstMinute),
            anchors = anchors,
        )
        val bottom = mapMinuteToLanePosition(
            minute = assignment.entry.endMinutes.coerceAtMost(lastMinute),
            anchors = anchors,
        )
        val columnCount = assignment.columnCount.coerceAtLeast(1)
        val totalGapPx = columnGapPx * (columnCount - 1)
        val widthPx = ((laneWidthPx - totalGapPx) / columnCount).coerceAtLeast(1f)

        WeekEntryLayout(
            entry = assignment.entry,
            topPx = top,
            heightPx = (bottom - top).coerceAtLeast(1f),
            leftPx = assignment.columnIndex * (widthPx + columnGapPx),
            widthPx = widthPx,
        )
    }.sortedWith(compareBy<WeekEntryLayout> { it.topPx }.thenBy { it.leftPx })
}

private fun buildLaneTimeAnchors(
    slots: List<WeekTimeSlot>,
    slotHeightPx: Float,
    slotGapPx: Float,
): List<TimeAnchor> {
    if (slots.isEmpty()) return emptyList()

    val anchors = mutableListOf<TimeAnchor>()
    slots.forEachIndexed { index, slot ->
        anchors += TimeAnchor(
            minute = slot.startMinutes,
            positionPx = index * (slotHeightPx + slotGapPx),
        )
    }

    val lastTopPx = (slots.lastIndex) * (slotHeightPx + slotGapPx)
    anchors += TimeAnchor(
        minute = slots.last().endMinutes,
        positionPx = lastTopPx + slotHeightPx,
    )
    return anchors
}

private fun mapMinuteToLanePosition(
    minute: Int,
    anchors: List<TimeAnchor>,
): Float {
    if (anchors.isEmpty()) return 0f

    val clampedMinute = minute.coerceIn(anchors.first().minute, anchors.last().minute)
    for (index in 0 until anchors.lastIndex) {
        val start = anchors[index]
        val end = anchors[index + 1]
        if (clampedMinute <= end.minute) {
            if (end.minute <= start.minute) return end.positionPx
            val fraction = (clampedMinute - start.minute).toFloat() / (end.minute - start.minute).toFloat()
            return start.positionPx + (end.positionPx - start.positionPx) * fraction
        }
    }
    return anchors.last().positionPx
}

private fun assignEntryColumns(entries: List<TimetableEntry>): List<EntryColumnAssignment> {
    if (entries.isEmpty()) return emptyList()

    val sortedEntries = entries.sortedWith(
        compareBy<TimetableEntry> { it.startMinutes }
            .thenBy { it.endMinutes }
            .thenBy { it.id },
    )
    val assignments = mutableListOf<EntryColumnAssignment>()
    val currentGroup = mutableListOf<MutableEntryColumnAssignment>()
    val columnEnds = mutableListOf<Int>()
    var groupMaxEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (currentGroup.isEmpty()) return
        val columnCount = columnEnds.size.coerceAtLeast(1)
        currentGroup.forEach { assignment ->
            assignments += EntryColumnAssignment(
                entry = assignment.entry,
                columnIndex = assignment.columnIndex,
                columnCount = columnCount,
            )
        }
        currentGroup.clear()
        columnEnds.clear()
        groupMaxEnd = Int.MIN_VALUE
    }

    sortedEntries.forEach { entry ->
        if (currentGroup.isNotEmpty() && entry.startMinutes >= groupMaxEnd) {
            flushGroup()
        }

        val reusableColumn = columnEnds.indexOfFirst { columnEnd -> columnEnd <= entry.startMinutes }
        val columnIndex = if (reusableColumn >= 0) {
            columnEnds[reusableColumn] = entry.endMinutes
            reusableColumn
        } else {
            columnEnds += entry.endMinutes
            columnEnds.lastIndex
        }

        currentGroup += MutableEntryColumnAssignment(entry = entry, columnIndex = columnIndex)
        groupMaxEnd = maxOf(groupMaxEnd, entry.endMinutes)
    }

    flushGroup()
    return assignments
}

internal fun colorWithHueShift(base: Color, hueShift: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = (hsv[0] + hueShift) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
