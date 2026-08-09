package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.VpnPermissionStatus
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.VpnPermissionUiState
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: VpnPermissionUiState,
    settings: AppSettings,
    runtimeState: VpnRuntimeSnapshot,
    stats: VpnStats,
    proxies: List<ProxyEntity>,
    selectedProxyId: Long?,
    diagnosticsSummary: String,
    lastError: String?,
    canStartVpn: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onDirectSelected: () -> Unit,
    onProxySelected: (ProxyEntity) -> Unit,
    onManageProxies: () -> Unit,
    onViewLogs: () -> Unit,
    onViewDiagnostics: () -> Unit,
    onPrivacyDisclosureAccepted: (Boolean) -> Unit,
    onDomainDestinationLoggingEnabled: (Boolean) -> Unit,
    onInfiniteReconnectEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsDialogVisible by remember { mutableStateOf(false) }

    val statusMessage = runtimeState.statusMessage ?: uiState.message
    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
        }
    }
    LaunchedEffect(lastError) {
        if (!lastError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(lastError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy Switcher VPN") },
                actions = {
                    IconButton(onClick = onManageProxies) {
                        Icon(Icons.Default.Dns, contentDescription = "Manage proxies")
                    }
                    IconButton(onClick = onViewLogs) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = "View logs"
                        )
                    }
                    IconButton(onClick = onViewDiagnostics) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = "View diagnostics"
                        )
                    }
                    IconButton(onClick = { settingsDialogVisible = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusLine(
                runtimeState = runtimeState,
                permissionStatus = uiState.permissionStatus
            )
            TrafficLine(stats = stats)

            val isVpnActive = runtimeState.isForegroundServiceActive
            Button(
                onClick = { if (isVpnActive) onStopVpn() else onStartVpn() },
                enabled = if (isVpnActive) {
                    true
                } else {
                    canStartVpn && uiState.permissionStatus != VpnPermissionStatus.REQUESTING
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = if (isVpnActive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isVpnActive) "STOP VPN" else "START VPN")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Route",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManageProxies) {
                    Text("Manage")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    RouteRow(
                        title = DIRECT_ROUTE_LABEL,
                        subtitle = "No upstream proxy",
                        selected = selectedProxyId == null,
                        enabled = true,
                        onClick = onDirectSelected
                    )
                }
                items(proxies, key = { proxy -> proxy.id }) { proxy ->
                    RouteRow(
                        title = proxy.label?.takeIf { it.isNotBlank() } ?: proxy.host,
                        subtitle = "${proxy.type} ${proxy.host}:${proxy.port}" +
                            if (proxy.isEnabled) "" else " - disabled",
                        selected = selectedProxyId == proxy.id,
                        enabled = proxy.isEnabled,
                        onClick = { onProxySelected(proxy) }
                    )
                }
            }

            Text(
                text = diagnosticsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewDiagnostics)
                    .padding(vertical = 8.dp)
            )
        }
    }

    if (settingsDialogVisible) {
        SettingsDialog(
            settings = settings,
            onDismiss = { settingsDialogVisible = false },
            onPrivacyDisclosureAccepted = onPrivacyDisclosureAccepted,
            onDomainDestinationLoggingEnabled = onDomainDestinationLoggingEnabled,
            onInfiniteReconnectEnabled = onInfiniteReconnectEnabled
        )
    }
}

@Composable
private fun StatusLine(
    runtimeState: VpnRuntimeSnapshot,
    permissionStatus: VpnPermissionStatus
) {
    val statusColor = when (runtimeState.status) {
        VpnServiceStatus.RUNNING -> MaterialTheme.colorScheme.primary
        VpnServiceStatus.STARTING,
        VpnServiceStatus.STOPPING -> MaterialTheme.colorScheme.tertiary
        VpnServiceStatus.ERROR -> MaterialTheme.colorScheme.error
        VpnServiceStatus.STOPPED -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = statusColor,
            shape = CircleShape,
            modifier = Modifier.size(10.dp)
        ) {}
        Text(
            text = runtimeState.status.name,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "permission: ${permissionStatus.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrafficLine(stats: VpnStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2193 ${formatBytes(stats.bytesIn)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "\u2191 ${formatBytes(stats.bytesOut)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (stats.activeConnectionsAvailable) {
                "conn ${stats.activeConnections}"
            } else {
                "conn n/a"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RouteRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onPrivacyDisclosureAccepted: (Boolean) -> Unit,
    onDomainDestinationLoggingEnabled: (Boolean) -> Unit,
    onInfiniteReconnectEnabled: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Reconnect indefinitely")
                        Text(
                            text = "Keep retrying the selected proxy instead of " +
                                "stopping the VPN after a few failures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.infiniteReconnectEnabled,
                        onCheckedChange = onInfiniteReconnectEnabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Disclosure accepted",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.privacyDisclosureAccepted,
                        onCheckedChange = onPrivacyDisclosureAccepted
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detailed domain logging",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.domainDestinationLoggingEnabled,
                        onCheckedChange = onDomainDestinationLoggingEnabled,
                        enabled = settings.privacyDisclosureAccepted
                    )
                }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return "%.1f KiB".format(kib)
    return "%.1f MiB".format(kib / 1024.0)
}

private const val DIRECT_ROUTE_LABEL = "Direct Connection"

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    Proxy_switcher_vpnTheme {
        HomeScreen(
            uiState = VpnPermissionUiState(
                permissionStatus = VpnPermissionStatus.GRANTED,
                message = "VPN permission is granted."
            ),
            settings = AppSettings(),
            runtimeState = VpnRuntimeSnapshot(),
            stats = VpnStats(bytesIn = 2048, bytesOut = 4096),
            proxies = listOf(
                ProxyEntity(
                    id = 1L,
                    host = "proxy.example",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    label = "Test proxy"
                )
            ),
            selectedProxyId = 1L,
            diagnosticsSummary = "IPv4 only; DNS proxy-safe; UDP/443 blocked.",
            lastError = null,
            canStartVpn = true,
            onStartVpn = {},
            onStopVpn = {},
            onDirectSelected = {},
            onProxySelected = {},
            onManageProxies = {},
            onViewLogs = {},
            onViewDiagnostics = {},
            onPrivacyDisclosureAccepted = {},
            onDomainDestinationLoggingEnabled = {},
            onInfiniteReconnectEnabled = {}
        )
    }
}
