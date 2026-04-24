package com.example.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.R
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.notify.CourseReminderScheduler

@Composable
fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onEnableNotifications: () -> Unit,
    exactAlarmPermissionRequired: Boolean,
    exactAlarmEnabled: Boolean,
    onOpenExactAlarmSettings: () -> Unit,
    reminderMinutes: List<Int>,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (List<Int>) -> Unit,
    backgroundMode: AppBackgroundMode,
    hasCustomBackground: Boolean,
    onSelectBackgroundImage: () -> Unit,
    onUseBundledBackground: () -> Unit,
    onUseGradientBackground: () -> Unit,
    onAdjustCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
) {
    var showReminderSheet by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.hero_subtitle, courseCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroActionChip(
                    icon = Icons.Default.Download,
                    label = stringResource(R.string.hero_import),
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.Upload,
                    label = stringResource(R.string.hero_export),
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.NotificationsActive,
                    label = stringResource(R.string.hero_reminder, CourseReminderScheduler.formatReminderChipLabel(reminderMinutes)),
                    onClick = { showReminderSheet = true },
                    modifier = Modifier.weight(1f),
                )
                HeroActionChip(
                    icon = Icons.Default.ColorLens,
                    label = stringResource(R.string.hero_background),
                    onClick = { showAppearanceDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showReminderSheet) {
        ReminderPickerDialog(
            reminderMinutes = reminderMinutes,
            reminderOptions = reminderOptions,
            exactAlarmPermissionRequired = exactAlarmPermissionRequired,
            exactAlarmEnabled = exactAlarmEnabled,
            onDismiss = { showReminderSheet = false },
            onSelect = {
                onReminderMinutesChange(it)
                showReminderSheet = false
            },
            onEnableNotifications = {
                onEnableNotifications()
                showReminderSheet = false
            },
            onOpenExactAlarmSettings = {
                onOpenExactAlarmSettings()
                showReminderSheet = false
            },
        )
    }

    if (showAppearanceDialog) {
        AppearanceDialog(
            onDismiss = { showAppearanceDialog = false },
            backgroundMode = backgroundMode,
            hasCustomBackground = hasCustomBackground,
            onSelectBackgroundImage = onSelectBackgroundImage,
            onUseBundledBackground = onUseBundledBackground,
            onUseGradientBackground = onUseGradientBackground,
            onAdjustCustomBackground = onAdjustCustomBackground,
            onClearCustomBackground = onClearCustomBackground,
            weekCardAlpha = weekCardAlpha,
            onWeekCardAlphaChange = onWeekCardAlphaChange,
            weekCardHue = weekCardHue,
            onWeekCardHueChange = onWeekCardHueChange,
        )
    }
}

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
            .semantics {
                role = Role.Button
                contentDescription = buildHeroActionContentDescription(LocalContext.current, label)
            }
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

@Composable
private fun AppearanceDialog(
    onDismiss: () -> Unit,
    backgroundMode: AppBackgroundMode,
    hasCustomBackground: Boolean,
    onSelectBackgroundImage: () -> Unit,
    onUseBundledBackground: () -> Unit,
    onUseGradientBackground: () -> Unit,
    onAdjustCustomBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
) {
    val previewColor = remember(weekCardHue, weekCardAlpha) {
        colorWithHueShift(Color(0xFFE98AA9), weekCardHue).copy(alpha = weekCardAlpha)
    }
    val backgroundSummary = when (backgroundMode) {
        AppBackgroundMode.BUNDLED_IMAGE -> stringResource(R.string.bg_bundled)
        AppBackgroundMode.CUSTOM_IMAGE -> stringResource(R.string.bg_custom)
        AppBackgroundMode.GRADIENT -> stringResource(R.string.bg_gradient)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_appearance), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.label_background_image), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = backgroundSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.bg_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            onSelectBackgroundImage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_select_custom_image))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onUseBundledBackground,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_default_image))
                        }
                        OutlinedButton(
                            onClick = onUseGradientBackground,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_gradient_only))
                        }
                    }
                    if (hasCustomBackground) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onAdjustCustomBackground()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_adjust_range))
                            }
                            TextButton(
                                onClick = onClearCustomBackground,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_clear_custom_image))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(stringResource(R.string.label_week_hue), style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = weekCardHue,
                        onValueChange = onWeekCardHueChange,
                        valueRange = 0f..360f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = previewColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(width = 56.dp, height = 32.dp),
                        ) {}
                        Text(
                            text = stringResource(R.string.label_current_hue, weekCardHue.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(stringResource(R.string.label_week_alpha), style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = weekCardAlpha,
                        onValueChange = onWeekCardAlphaChange,
                        valueRange = 0.35f..1.0f,
                    )
                    Text(
                        text = stringResource(R.string.label_current_alpha, (weekCardAlpha * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun ReminderPickerDialog(
    reminderMinutes: List<Int>,
    reminderOptions: List<Int>,
    exactAlarmPermissionRequired: Boolean,
    exactAlarmEnabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (List<Int>) -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    var selectedReminderMinutes by remember(reminderMinutes) {
        mutableStateOf(
            CourseReminderScheduler.normalizeReminderMinutes(reminderMinutes)
                .ifEmpty { CourseReminderScheduler.defaultReminderMinutesSet() },
        )
    }
    var customReminderText by remember { mutableStateOf("") }
    var customReminderError by remember { mutableStateOf<String?>(null) }
    val maxReminderSelectionCount = CourseReminderScheduler.maxReminderSelectionCount()

    // Pre-load string resources for use in non-@Composable local functions
    val strMinReminder = stringResource(R.string.error_min_reminder_required)
    val strMaxReminder = stringResource(R.string.error_max_reminder_reached, maxReminderSelectionCount)
    val strCustomRange = stringResource(R.string.error_custom_reminder_range)
    val strReminderExists = stringResource(R.string.error_reminder_exists)

    fun updateSelectedReminderMinutes(updatedMinutes: List<Int>) {
        selectedReminderMinutes = CourseReminderScheduler.normalizeReminderMinutes(updatedMinutes)
            .ifEmpty { CourseReminderScheduler.defaultReminderMinutesSet() }
        customReminderError = null
    }

    fun toggleReminder(option: Int) {
        if (option in selectedReminderMinutes) {
            if (selectedReminderMinutes.size == 1) {
                customReminderError = strMinReminder
                return
            }
            updateSelectedReminderMinutes(selectedReminderMinutes - option)
            return
        }

        if (selectedReminderMinutes.size >= maxReminderSelectionCount) {
            customReminderError = strMaxReminder
            return
        }

        updateSelectedReminderMinutes(selectedReminderMinutes + option)
    }

    fun submitCustomReminder() {
        val parsedMinutes = customReminderText.toIntOrNull()
        if (parsedMinutes == null || !CourseReminderScheduler.isReminderMinutesValid(parsedMinutes)) {
            customReminderError = strCustomRange
            return
        }
        if (parsedMinutes in selectedReminderMinutes) {
            customReminderError = strReminderExists
            return
        }
        if (selectedReminderMinutes.size >= maxReminderSelectionCount) {
            customReminderError = strMaxReminder
            return
        }
        customReminderText = ""
        updateSelectedReminderMinutes(selectedReminderMinutes + parsedMinutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_reminder_picker), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.hint_reminder_multi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.label_selected_reminder, CourseReminderScheduler.formatReminderSelection(selectedReminderMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exactAlarmPermissionRequired) {
                    Text(
                        text = if (exactAlarmEnabled) {
                            stringResource(R.string.label_exact_alarm_on)
                        } else {
                            stringResource(R.string.label_exact_alarm_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exactAlarmEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (!exactAlarmEnabled) {
                        OutlinedButton(
                            onClick = onOpenExactAlarmSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_enable_exact_alarm))
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedReminderMinutes.forEach { minute ->
                        OutlinedButton(
                            onClick = { toggleReminder(minute) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("${minute}m")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminderOptions.forEach { option ->
                        val selected = option in selectedReminderMinutes
                        if (selected) {
                            Button(
                                onClick = { toggleReminder(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        } else {
                            OutlinedButton(
                                onClick = { toggleReminder(option) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("${option}m") }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.hint_custom_reminder, maxReminderSelectionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = customReminderText,
                    onValueChange = { value ->
                        customReminderText = value.filter { it.isDigit() }.take(3)
                        customReminderError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_custom_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = customReminderError != null,
                    supportingText = {
                        Text(customReminderError ?: stringResource(R.string.hint_add_custom_reminder))
                    },
                )
                OutlinedButton(
                    onClick = ::submitCustomReminder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_add_custom_reminder))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedReminderMinutes) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEnableNotifications) { Text(stringResource(R.string.action_enable_permission)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

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
            text = stringResource(R.string.label_sort_by_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ViewModeSwitcher(
    currentDestination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)) {
        NavigationBarItem(
            selected = currentDestination == AppDestination.DAY,
            onClick = { onDestinationChange(AppDestination.DAY) },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_day_view)) },
        )
        NavigationBarItem(
            selected = currentDestination == AppDestination.WEEK,
            onClick = { onDestinationChange(AppDestination.WEEK) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_week_view)) },
        )
        NavigationBarItem(
            selected = currentDestination == AppDestination.SETTINGS,
            onClick = { onDestinationChange(AppDestination.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
        )
    }
}
