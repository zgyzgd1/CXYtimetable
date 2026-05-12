package com.example.timetable.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timetable.BuildConfig
import com.example.timetable.R
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.notify.CourseReminderScheduler

@Composable
fun SettingsScreen(
    clearingCache: Boolean,
    onClearCache: () -> Unit,
    backgroundAppearance: BackgroundAppearance,
    onBackgroundModeChange: (AppBackgroundMode) -> Unit,
    onSelectBackgroundImage: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
    notificationGranted: Boolean,
    exactAlarmEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onImportIcs: () -> Unit,
    onExportIcs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 外观设置 ──────────────────────────────────
        SettingsSectionHeader(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_section_appearance),
        )

        // 背景模式选择
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsItemLabel(
                    icon = Icons.Default.ColorLens,
                    label = stringResource(R.string.settings_background_mode),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppBackgroundMode.entries.forEach { mode ->
                        val selected = mode == backgroundAppearance.mode
                        val label = when (mode) {
                            AppBackgroundMode.BUNDLED_IMAGE -> stringResource(R.string.settings_background_bundled)
                            AppBackgroundMode.CUSTOM_IMAGE -> stringResource(R.string.settings_background_custom)
                            AppBackgroundMode.GRADIENT -> stringResource(R.string.settings_background_gradient)
                        }
                        if (selected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(label, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onBackgroundModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
                if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
                    FilledTonalButton(
                        onClick = onSelectBackgroundImage,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_background_custom))
                    }
                }
            }
        }

        // 周视图卡片透明度
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsItemLabel(
                    icon = Icons.Default.Opacity,
                    label = stringResource(R.string.settings_card_opacity),
                )
                Text(
                    text = "${(weekCardAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = weekCardAlpha,
                    onValueChange = onWeekCardAlphaChange,
                    valueRange = 0.3f..1f,
                    steps = 7,
                )
            }
        }

        // 周视图卡片色调
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsItemLabel(
                    icon = Icons.Default.Tune,
                    label = stringResource(R.string.settings_card_hue),
                )
                Text(
                    text = "${weekCardHue.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = weekCardHue,
                    onValueChange = onWeekCardHueChange,
                    valueRange = -180f..180f,
                    steps = 36,
                )
            }
        }

        // ── 提醒设置 ──────────────────────────────────
        SettingsSectionHeader(
            icon = Icons.Default.NotificationsActive,
            title = stringResource(R.string.settings_section_reminders),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 通知权限
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_notification_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (notificationGranted) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = AppShape.Pill,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_notification_granted),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onEnableNotifications,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_notification_denied),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // 精确提醒
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_exact_alarm_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (exactAlarmEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = AppShape.Pill,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_exact_alarm_enabled),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpenExactAlarmSettings,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_exact_alarm_disabled),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        // ── 数据管理 ──────────────────────────────────
        SettingsSectionHeader(
            icon = Icons.Default.Cached,
            title = stringResource(R.string.settings_section_data),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.hint_cache_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = onImportIcs,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_import_ics))
                    }
                    OutlinedButton(
                        onClick = onExportIcs,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_export_ics))
                    }
                }
                Button(
                    onClick = onClearCache,
                    enabled = !clearingCache,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(if (clearingCache) stringResource(R.string.action_clearing) else stringResource(R.string.action_clear_cache))
                }
            }
        }

        // ── 关于 ──────────────────────────────────────
        SettingsSectionHeader(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = stringResource(R.string.settings_section_about),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.secondaryContent(),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_app_name_version),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SettingsItemLabel(
    icon: ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
