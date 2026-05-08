package com.example.timetable.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.timetable.R
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.BackgroundImageManager
import kotlinx.coroutines.launch

internal data class ScheduleLaunchers(
    val import: ActivityResultLauncher<Array<String>>,
    val export: ActivityResultLauncher<String>,
    val backgroundImage: ActivityResultLauncher<String>,
)

@Composable
internal fun rememberScheduleLaunchers(
    viewModel: ScheduleViewModel,
    snackbarHostState: SnackbarHostState,
    onBackgroundAppearanceChange: (BackgroundAppearance) -> Unit,
    onShowBackgroundAdjustDialogChange: (Boolean) -> Unit,
): ScheduleLaunchers {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val msgExportSuccess = stringResource(R.string.msg_export_success)
    val msgExportFailedTemplate = stringResource(R.string.msg_export_failed, "%s")
    val msgUnknownError = stringResource(R.string.msg_unknown_error)
    val msgBackgroundUpdated = stringResource(R.string.msg_background_updated)
    val msgBackgroundFailedTemplate = stringResource(R.string.msg_background_failed, "%s")

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromIcs(context.contentResolver, uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    }
                    snackbarHostState.showSnackbar(msgExportSuccess)
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(msgExportFailedTemplate.format(error.message ?: msgUnknownError))
                }
            }
        }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    BackgroundImageManager.saveCustomBackground(context, context.contentResolver, uri)
                    onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                    onShowBackgroundAdjustDialogChange(true)
                    snackbarHostState.showSnackbar(msgBackgroundUpdated)
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        msgBackgroundFailedTemplate.format(error.message ?: msgUnknownError),
                    )
                }
            }
        }
    }

    return ScheduleLaunchers(
        import = importLauncher,
        export = exportLauncher,
        backgroundImage = backgroundImageLauncher,
    )
}
