package com.example.timetable.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.dayLabel
import com.example.timetable.data.formatMinutes

private val courseAccentColors = listOf(
    Color(0xFF7986CB),
    Color(0xFF4DB6AC),
    Color(0xFFFF8A65),
    Color(0xFFA1887F),
    Color(0xFF4FC3F7),
    Color(0xFFAED581),
    Color(0xFFBA68C8),
    Color(0xFFFFB74D),
    Color(0xFFF06292),
    Color(0xFF4DD0E1),
)

private fun accentColorFor(title: String): Color =
    courseAccentColors[(title.hashCode() and Int.MAX_VALUE) % courseAccentColors.size]

@Composable
fun EntryCard(
    entry: TimetableEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = remember(entry.title) { accentColorFor(entry.title) }

    Card(
        modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.98f),
                                accent.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp, end = 10.dp, top = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.title.ifBlank { "未命名课程" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = accent,
                                )
                                Text(
                                    text = "${formatMinutes(entry.startMinutes)} - ${formatMinutes(entry.endMinutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                text = dayLabel(entry.dayOfWeek),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = accent,
                            )
                        }
                    }

                    if (entry.location.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = entry.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (entry.note.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "📮",
                style = MaterialTheme.typography.displaySmall,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "今天暂无课程",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "切换日期查看，或点击下方按钮添加课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加课程")
            }
        }
    }
}
