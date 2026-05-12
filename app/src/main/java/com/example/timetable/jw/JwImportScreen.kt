package com.example.timetable.jw

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.parseEntryDate
import com.example.timetable.jw.hebau.HebauAcademicIcsBridge
import com.example.timetable.jw.hebau.HebauCourseMapper
import com.example.timetable.jw.hebau.HebauCourseParser
import com.example.timetable.jw.hebau.HebauParseResult
import com.example.timetable.ui.ScheduleViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwImportScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onImportSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var entryUrl by rememberSaveable { mutableStateOf(JwImportContract.PRIMARY_ENTRY_URL) }
    var currentUrl by rememberSaveable { mutableStateOf(entryUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var webModeName by rememberSaveable { mutableStateOf(JwWebMode.DESKTOP.name) }
    var progress by remember { mutableStateOf(0) }
    var pendingImport by remember { mutableStateOf<PendingHebauImport?>(null) }
    val webMode = JwWebMode.fromSavedName(webModeName)
    val importNotRequestedMessage = stringResource(R.string.msg_jw_import_not_requested)
    val payloadTooLargeMessage = stringResource(R.string.msg_jw_import_payload_too_large)

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun submitParsedCourses(parseResult: HebauParseResult, semesterStartDate: LocalDate) {
        scope.launch {
            val mapping = withContext(Dispatchers.Default) {
                HebauCourseMapper.map(parseResult.payload, semesterStartDate)
            }
            if (parseResult.errors.isNotEmpty()) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_jw_import_parse_skipped, parseResult.errors.size))
            }
            if (mapping.errors.isNotEmpty()) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_jw_import_mapping_skipped, mapping.errors.size))
            }
            if (mapping.entries.isEmpty()) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_jw_import_no_mapped))
                return@launch
            }
            val normalizedEntries = withContext(Dispatchers.Default) {
                HebauAcademicIcsBridge.normalize(
                    entries = mapping.entries,
                    calendarName = resources.getString(R.string.jw_source_name_hebau),
                )
            }
            viewModel.importAcademicEntries(
                sourceName = resources.getString(R.string.jw_source_name_hebau),
                entries = normalizedEntries,
            )
            onImportSubmitted()
        }
    }

    val bridge = remember(importNotRequestedMessage, payloadTooLargeMessage) {
        JwBridge(
            onCoursesJson = { json ->
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.Default) {
                            HebauCourseParser.parse(json)
                        }
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar(
                            resources.getString(
                                R.string.msg_jw_import_parse_error,
                                error.message ?: resources.getString(R.string.msg_unknown_error),
                            ),
                        )
                    }.getOrNull() ?: return@launch

                    if (result.payload.courses.isEmpty()) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.msg_jw_import_received_empty))
                        return@launch
                    }

                    val parsedSemesterDate = result.payload.semesterStartDate?.let(::parseEntryDate)
                    if (parsedSemesterDate != null) {
                        submitParsedCourses(result, parsedSemesterDate)
                    } else {
                        pendingImport = PendingHebauImport(
                            parseResult = result,
                            semesterStartDateText = defaultSemesterStartDateText(),
                        )
                    }
                }
            },
            onError = ::showMessage,
            onLog = ::showMessage,
            messages = JwBridgeMessages(
                importNotRequested = importNotRequestedMessage,
                payloadTooLarge = payloadTooLargeMessage,
            ),
        )
    }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_jw_import),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pageTitle.ifBlank { JwImportContract.displayHost(currentUrl).ifBlank { currentUrl } },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(
                        onClick = {
                            entryUrl = if (entryUrl == JwImportContract.PRIMARY_ENTRY_URL) {
                                JwImportContract.ALTERNATE_ENTRY_URL
                            } else {
                                JwImportContract.PRIMARY_ENTRY_URL
                            }
                        },
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.action_switch_entry))
                    }
                    IconButton(
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                            webView?.clearCache(true)
                            webView?.reload()
                            showMessage(resources.getString(R.string.msg_jw_login_cleared))
                        },
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.action_clear_login))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                JwWebModeSelector(
                    selected = webMode,
                    onSelected = { selectedMode ->
                        if (selectedMode != webMode) {
                            webModeName = selectedMode.name
                            showMessage(
                                resources.getString(
                                    R.string.msg_jw_web_mode_changed,
                                    resources.getString(selectedMode.labelResId()),
                                ),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            entryUrl = JwImportContract.PRIMARY_ENTRY_URL
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.jw_primary_entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            entryUrl = JwImportContract.ALTERNATE_ENTRY_URL
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.jw_alternate_entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = {
                        if (!JwImportContract.isAllowedUrl(currentUrl)) {
                            showMessage(resources.getString(R.string.msg_jw_host_not_allowed))
                            return@Button
                        }
                        scope.launch {
                            val script = runCatching {
                                withContext(Dispatchers.IO) {
                                    context.assets.open("jw/hebau_urp.js").bufferedReader().use { it.readText() }
                                }
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(
                                    resources.getString(
                                        R.string.msg_jw_import_parse_error,
                                        error.message ?: resources.getString(R.string.msg_unknown_error),
                                    ),
                                )
                            }.getOrNull() ?: return@launch

                            bridge.markImportRequested()
                            webView?.evaluateJavascript(script, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_start_jw_import))
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            JwWebView(
                bridge = bridge,
                url = entryUrl,
                webMode = webMode,
                onWebViewCreated = {
                    webView = it
                    bridge.currentUrl = it.url ?: entryUrl
                },
                onUrlChange = {
                    currentUrl = it
                    bridge.currentUrl = it
                },
                onTitleChange = { pageTitle = it },
                onProgressChange = { progress = it },
                onError = ::showMessage,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingImport?.let { pending ->
        SemesterStartConfirmDialog(
            pending = pending,
            onChange = { pendingImport = pending.copy(semesterStartDateText = it) },
            onConfirm = {
                val parsed = parseEntryDate(pending.semesterStartDateText)
                if (parsed == null) {
                    showMessage(resources.getString(R.string.error_semester_start_date_invalid))
                    return@SemesterStartConfirmDialog
                }
                pendingImport = null
                submitParsedCourses(pending.parseResult, parsed)
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun JwWebModeSelector(
    selected: JwWebMode,
    onSelected: (JwWebMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WebModeButton(
            mode = JwWebMode.DESKTOP,
            selected = selected == JwWebMode.DESKTOP,
            onClick = { onSelected(JwWebMode.DESKTOP) },
            modifier = Modifier.weight(1f),
        )
        WebModeButton(
            mode = JwWebMode.MOBILE,
            selected = selected == JwWebMode.MOBILE,
            onClick = { onSelected(JwWebMode.MOBILE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WebModeButton(
    mode: JwWebMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(stringResource(mode.labelResId()), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(stringResource(mode.labelResId()), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SemesterStartConfirmDialog(
    pending: PendingHebauImport,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_semester_start_confirm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.hint_semester_start_confirm),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = pending.semesterStartDateText,
                    onValueChange = onChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_semester_start_date_input)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private data class PendingHebauImport(
    val parseResult: HebauParseResult,
    val semesterStartDateText: String,
)

private fun defaultSemesterStartDateText(): String {
    return LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toString()
}

private fun JwWebMode.labelResId(): Int {
    return when (this) {
        JwWebMode.DESKTOP -> R.string.jw_web_mode_desktop
        JwWebMode.MOBILE -> R.string.jw_web_mode_mobile
    }
}
