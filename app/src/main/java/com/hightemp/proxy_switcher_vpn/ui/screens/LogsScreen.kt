package com.hightemp.proxy_switcher_vpn.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.utils.LogEntry
import com.hightemp.proxy_switcher_vpn.utils.LogExport
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
    onBuildReport: (hideAddresses: Boolean, (String) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingReport by remember { mutableStateOf<String?>(null) }
    val saveReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val report = pendingReport
        pendingReport = null
        if (uri != null && report != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(report.toByteArray(Charsets.UTF_8))
                } ?: error("No output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) R.string.logs_export_saved else R.string.logs_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    var selectedLevel by remember { mutableStateOf<VpnEngineLogLevel?>(null) }
    var selectedType by remember { mutableStateOf<LogType?>(null) }
    val filteredLogs = remember(logs, selectedLevel, selectedType) {
        logs.filter { entry ->
            (selectedLevel == null || entry.level == selectedLevel) &&
                (selectedType == null || entry.type == selectedType)
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = { Text(stringResource(R.string.logs_title)) },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.logs_export))
                    }
                    IconButton(
                        onClick = onClearLogs,
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.logs_clear))
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterMenu<VpnEngineLogLevel>(
                    selectedLabel = selectedLevel?.name ?: stringResource(R.string.logs_all_levels),
                    options = listOf(
                        FilterOption<VpnEngineLogLevel>(null, stringResource(R.string.logs_all_levels))
                    ) +
                        VpnEngineLogLevel.entries.map {
                            FilterOption<VpnEngineLogLevel>(it, it.name)
                        },
                    onSelected = { selectedLevel = it },
                    modifier = Modifier.weight(1f)
                )
                FilterMenu<LogType>(
                    selectedLabel = selectedType?.name ?: stringResource(R.string.logs_all_types),
                    options = listOf(FilterOption<LogType>(null, stringResource(R.string.logs_all_types))) +
                        LogType.entries.map {
                            FilterOption<LogType>(it, it.name)
                        },
                    onSelected = { selectedType = it },
                    modifier = Modifier.weight(1f)
                )
            }
            if (filteredLogs.isEmpty()) {
                EmptyLogs(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { entry ->
                        LogRow(entry = entry)
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportLogsDialog(
            onDismiss = { showExportDialog = false },
            onShare = { hideAddresses ->
                showExportDialog = false
                onBuildReport(hideAddresses) { report ->
                    if (!shareReport(context, report)) {
                        Toast.makeText(context, R.string.logs_export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSave = { hideAddresses ->
                showExportDialog = false
                onBuildReport(hideAddresses) { report ->
                    pendingReport = report
                    saveReportLauncher.launch(LogExport.fileName(System.currentTimeMillis()))
                }
            }
        )
    }
}

@Composable
private fun ExportLogsDialog(
    onDismiss: () -> Unit,
    onShare: (hideAddresses: Boolean) -> Unit,
    onSave: (hideAddresses: Boolean) -> Unit
) {
    var hideAddresses by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logs_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.logs_export_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.logs_export_hide_addresses),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.logs_export_hide_addresses_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = hideAddresses, onCheckedChange = { hideAddresses = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(hideAddresses) }) {
                Text(stringResource(R.string.logs_export_share))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(hideAddresses) }) {
                    Text(stringResource(R.string.logs_export_save))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

/** Writes the report to cacheDir/exports (replacing older ones) and opens the share sheet. */
private fun shareReport(context: Context, report: String): Boolean {
    return runCatching {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, LogExport.fileName(System.currentTimeMillis()))
        file.writeText(report, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.logs_export_share_title)))
    }.isSuccess
}

private const val EXPORT_DIRECTORY = "exports"

@Composable
private fun <T> FilterMenu(
    selectedLabel: String,
    options: List<FilterOption<T>>,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyLogs(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimestamp(entry.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.level.name,
                style = MaterialTheme.typography.labelSmall,
                color = entry.level.color()
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

private data class FilterOption<T>(
    val value: T?,
    val label: String
)

@Composable
private fun VpnEngineLogLevel.color(): Color {
    return when (this) {
        VpnEngineLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        VpnEngineLogLevel.INFO -> MaterialTheme.colorScheme.primary
        VpnEngineLogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        VpnEngineLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMillis))
}

@Preview(showBackground = true)
@Composable
private fun LogsScreenPreview() {
    Proxy_switcher_vpnTheme {
        LogsScreen(
            logs = listOf(
                LogEntry(
                    timestampMillis = 1_700_000_000_000,
                    level = VpnEngineLogLevel.INFO,
                    type = LogType.VPN,
                    message = "VPN start requested."
                ),
                LogEntry(
                    timestampMillis = 1_700_000_001_000,
                    level = VpnEngineLogLevel.ERROR,
                    type = LogType.PROXY,
                    message = "Selected upstream proxy failed."
                )
            ),
            onBack = {},
            onClearLogs = {}
        )
    }
}
