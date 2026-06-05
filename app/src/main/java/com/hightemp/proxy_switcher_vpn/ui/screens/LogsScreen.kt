package com.hightemp.proxy_switcher_vpn.ui.screens

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.utils.LogEntry
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                            contentDescription = "Back"
                        )
                    }
                },
                title = { Text("Logs") },
                actions = {
                    IconButton(
                        onClick = onClearLogs,
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
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
                    selectedLabel = selectedLevel?.name ?: "All levels",
                    options = listOf(FilterOption<VpnEngineLogLevel>(null, "All levels")) +
                        VpnEngineLogLevel.entries.map {
                            FilterOption<VpnEngineLogLevel>(it, it.name)
                        },
                    onSelected = { selectedLevel = it },
                    modifier = Modifier.weight(1f)
                )
                FilterMenu<LogType>(
                    selectedLabel = selectedType?.name ?: "All types",
                    options = listOf(FilterOption<LogType>(null, "All types")) +
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
}

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
            text = "No logs",
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
