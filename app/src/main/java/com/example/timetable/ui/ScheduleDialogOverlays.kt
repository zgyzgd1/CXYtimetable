package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.BackgroundImageTransform
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.areWeekTimeSlotsNonOverlapping
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.inferFixedWeekScheduleConfig
import com.example.timetable.data.syncWeekTimeSlotsWithEntryChange
import kotlinx.coroutines.launch

@Composable
fun ScheduleDialogOverlays(
    viewModel: ScheduleViewModel,
    entries: List<TimetableEntry>,
    weekTimeSlots: List<WeekTimeSlot>,
    snackbarHostState: SnackbarHostState,
    editingEntry: TimetableEntry?,
    pendingConflict: PendingEntryConflict?,
    editingWeekSlotIndex: Int?,
    addingWeekSlotInitial: WeekTimeSlot?,
    editingWeekSlotCount: Boolean,
    editingFixedWeekSchedule: Boolean,
    showBackgroundAdjustDialog: Boolean,
    deletingEntry: TimetableEntry?,
    importPreviewState: ImportPreview?,
    backgroundAppearance: BackgroundAppearance,
    onEditingEntryChange: (TimetableEntry?) -> Unit,
    onPendingConflictChange: (PendingEntryConflict?) -> Unit,
    onEditingWeekSlotIndexChange: (Int?) -> Unit,
    onAddingWeekSlotInitialChange: (WeekTimeSlot?) -> Unit,
    onEditingWeekSlotCountChange: (Boolean) -> Unit,
    onEditingFixedWeekScheduleChange: (Boolean) -> Unit,
    onShowBackgroundAdjustDialogChange: (Boolean) -> Unit,
    onDeletingEntryChange: (TimetableEntry?) -> Unit,
    onImportPreviewStateChange: (ImportPreview?) -> Unit,
    onBackgroundAppearanceChange: (BackgroundAppearance) -> Unit,
    onWeekTimeSlotsChange: (List<WeekTimeSlot>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    deletingEntry?.let { toDelete ->
        DeleteEntryDialog(
            entry = toDelete,
            onConfirm = {
                viewModel.deleteEntry(toDelete.id)
                onDeletingEntryChange(null)
            },
            onDismiss = { onDeletingEntryChange(null) },
        )
    }

    importPreviewState?.let { preview ->
        ImportConflictDialog(
            preview = preview,
            onConfirm = {
                viewModel.confirmImport(preview)
                onImportPreviewStateChange(null)
            },
            onDismiss = {
                viewModel.cancelImport()
                onImportPreviewStateChange(null)
            },
        )
    }

    if (showBackgroundAdjustDialog) {
        BackgroundImageAdjustDialog(
            backgroundAppearance = backgroundAppearance,
            onDismiss = { onShowBackgroundAdjustDialogChange(false) },
            onSave = { transform ->
                AppearanceStore.setBackgroundImageTransform(context, transform)
                onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                onShowBackgroundAdjustDialogChange(false)
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_background_range_updated)) }
            },
        )
    }

    if (editingFixedWeekSchedule) {
        FixedWeekScheduleDialog(
            initialConfig = inferFixedWeekScheduleConfig(weekTimeSlots),
            initialSlots = weekTimeSlots,
            onDismiss = { onEditingFixedWeekScheduleChange(false) },
            onSave = { updatedSlots ->
                onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, updatedSlots))
                onEditingFixedWeekScheduleChange(false)
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_week_slots_updated)) }
            },
        )
    }

    editingEntry?.let { entry ->
        val existingEntry = entries.any { it.id == entry.id }
        val applyEntrySave: (TimetableEntry, Boolean) -> Unit = { updatedEntry, allowConflict ->
            viewModel.upsertEntry(updatedEntry, allowConflict = allowConflict)
            val syncedWeekTimeSlots = syncWeekTimeSlotsWithEntryChange(
                currentSlots = weekTimeSlots,
                originalEntry = entry.takeIf { existingEntry },
                updatedEntry = updatedEntry,
            )
            if (syncedWeekTimeSlots != weekTimeSlots) {
                onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, syncedWeekTimeSlots))
            }
            onPendingConflictChange(null)
            onEditingEntryChange(null)
        }
        EntryEditorDialog(
            initial = entry,
            onDismiss = {
                onPendingConflictChange(null)
                onEditingEntryChange(null)
            },
            onSave = { updatedEntry ->
                val conflict = viewModel.previewConflict(updatedEntry)
                if (conflict != null) {
                    onPendingConflictChange(
                        PendingEntryConflict(
                            updatedEntry = updatedEntry,
                            conflictEntry = conflict,
                        ),
                    )
                } else {
                    applyEntrySave(updatedEntry, false)
                }
            },
        )

        pendingConflict?.let { state ->
            EntryConflictDialog(
                conflictTitle = state.conflictEntry.title,
                conflictStartMinutes = state.conflictEntry.startMinutes,
                conflictEndMinutes = state.conflictEntry.endMinutes,
                onSaveAnyway = { applyEntrySave(state.updatedEntry, true) },
                onDeferAndSave = {
                    val adjusted = viewModel.suggestResolvedEntry(state.updatedEntry)
                    if (adjusted == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_no_deferrable_slot))
                        }
                    } else {
                        applyEntrySave(adjusted, false)
                    }
                },
                onGoBack = { onPendingConflictChange(null) },
            )
        }
    }

    editingWeekSlotIndex?.let { index ->
        val currentSlot = weekTimeSlots.getOrNull(index) ?: return@let
        WeekSlotEditorDialog(
            title = context.getString(R.string.title_edit_slot, index + 1),
            initial = currentSlot,
            onDismiss = { onEditingWeekSlotIndexChange(null) },
            onSave = { updatedSlot ->
                val updatedSlots = weekTimeSlots.toMutableList().apply {
                    this[index] = updatedSlot
                }.sortedBy { it.startMinutes }
                if (!areWeekTimeSlotsNonOverlapping(updatedSlots)) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_slot_time_overlap)) }
                    return@WeekSlotEditorDialog
                }
                onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, updatedSlots))
                onEditingWeekSlotIndexChange(null)
            },
            onDelete = if (weekTimeSlots.size > 1) {
                {
                    val updatedSlots = weekTimeSlots.toMutableList().apply { removeAt(index) }
                    onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, updatedSlots))
                    onEditingWeekSlotIndexChange(null)
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_slot_deleted, index + 1)) }
                }
            } else {
                null
            },
        )
    }

    addingWeekSlotInitial?.let { initialSlot ->
        WeekSlotEditorDialog(
            title = context.getString(R.string.title_add_slot),
            initial = initialSlot,
            onDismiss = { onAddingWeekSlotInitialChange(null) },
            onSave = { newSlot ->
                val updatedSlots = (weekTimeSlots + newSlot).sortedBy { it.startMinutes }
                if (!areWeekTimeSlotsNonOverlapping(updatedSlots)) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_slot_time_overlap)) }
                    return@WeekSlotEditorDialog
                }
                onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, updatedSlots))
                onAddingWeekSlotInitialChange(null)
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_slot_added, updatedSlots.indexOf(newSlot) + 1)) }
            },
        )
    }

    if (editingWeekSlotCount) {
        WeekSlotCountDialog(
            initialCount = weekTimeSlots.size,
            onDismiss = { onEditingWeekSlotCountChange(false) },
            onSave = { count ->
                val updatedSlots = resizeWeekTimeSlots(weekTimeSlots, count)
                onWeekTimeSlotsChange(AppearanceStore.setWeekTimeSlots(context, updatedSlots))
                onEditingWeekSlotCountChange(false)
                scope.launch {
                    val message = if (updatedSlots.size == count) {
                        context.getString(R.string.msg_slots_resized, count)
                    } else {
                        context.getString(R.string.msg_slots_resize_limited, updatedSlots.size)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            },
        )
    }
}

@Composable
fun DeleteEntryDialog(
    entry: TimetableEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_confirm_delete)) },
        text = {
            Text(
                stringResource(
                    R.string.msg_confirm_delete_entry,
                    entry.title.ifBlank { stringResource(R.string.label_unnamed) },
                ),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun ImportConflictDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_import_conflict)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.msg_import_parsed, preview.totalParsed, preview.validEntries.size))
                if (preview.invalidCount > 0) {
                    Text(stringResource(R.string.msg_import_skipped, preview.invalidCount))
                }
                Text(
                    stringResource(R.string.msg_import_conflicts, preview.conflictCount),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_confirm_import)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun EntryConflictDialog(
    conflictTitle: String,
    conflictStartMinutes: Int,
    conflictEndMinutes: Int,
    onSaveAnyway: () -> Unit,
    onDeferAndSave: () -> Unit,
    onGoBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onGoBack,
        title = { Text(stringResource(R.string.title_time_conflict)) },
        text = {
            Text(
                stringResource(
                    R.string.msg_conflict_overlap,
                    conflictTitle,
                    formatMinutes(conflictStartMinutes),
                    formatMinutes(conflictEndMinutes),
                ),
            )
        },
        confirmButton = {
            Button(onClick = onSaveAnyway) {
                Text(stringResource(R.string.action_save_anyway))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeferAndSave) {
                    Text(stringResource(R.string.action_defer_and_save))
                }
                OutlinedButton(onClick = onGoBack) {
                    Text(stringResource(R.string.action_go_back_edit))
                }
            }
        },
    )
}
