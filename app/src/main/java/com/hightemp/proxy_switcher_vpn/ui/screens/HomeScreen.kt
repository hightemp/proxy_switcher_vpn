package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
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
    selectedProxyLabel: String?,
    diagnosticsSummary: String,
    lastError: String?,
    canStartVpn: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onManageProxies: () -> Unit,
    onViewLogs: () -> Unit,
    onViewDiagnostics: () -> Unit,
    onPrivacyDisclosureAccepted: (Boolean) -> Unit,
    onDomainDestinationLoggingEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy Switcher VPN") },
                actions = {
                    IconButton(onClick = onViewLogs) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "View logs")
                    }
                    IconButton(onClick = onViewDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = "View diagnostics")
                    }
                    IconButton(onClick = onManageProxies) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage proxies")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = runtimeState.status.name,
                style = MaterialTheme.typography.displaySmall,
                color = if (runtimeState.isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            HomeInfoCard(title = "Selected proxy") {
                Text(
                    text = selectedProxyLabel ?: "No proxy selected",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            HomeInfoCard(title = "VPN state") {
                Text("Permission: ${uiState.permissionStatus.name}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Service: ${runtimeState.status.name}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(runtimeState.statusMessage ?: uiState.message)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CounterCard(
                    title = "Bytes in",
                    value = formatBytes(stats.bytesIn),
                    modifier = Modifier.weight(1f)
                )
                CounterCard(
                    title = "Bytes out",
                    value = formatBytes(stats.bytesOut),
                    modifier = Modifier.weight(1f)
                )
            }

            HomeInfoCard(title = "Diagnostics") {
                Text(diagnosticsSummary)
            }

            if (!lastError.isNullOrBlank()) {
                HomeInfoCard(title = "Last error") {
                    Text(
                        text = lastError,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartVpn,
                    enabled = canStartVpn &&
                        uiState.permissionStatus != VpnPermissionStatus.REQUESTING &&
                        !runtimeState.isForegroundServiceActive,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("START VPN")
                }
                Button(
                    onClick = onStopVpn,
                    enabled = runtimeState.isForegroundServiceActive,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("STOP VPN")
                }
            }

            PrivacyCard(
                settings = settings,
                onPrivacyDisclosureAccepted = onPrivacyDisclosureAccepted,
                onDomainDestinationLoggingEnabled = onDomainDestinationLoggingEnabled
            )
        }
    }
}

@Composable
private fun HomeInfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CounterCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun PrivacyCard(
    settings: AppSettings,
    onPrivacyDisclosureAccepted: (Boolean) -> Unit,
    onDomainDestinationLoggingEnabled: (Boolean) -> Unit
) {
    HomeInfoCard(title = "Privacy") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return "%.1f KiB".format(kib)
    return "%.1f MiB".format(kib / 1024.0)
}

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
            selectedProxyLabel = "Test proxy (SOCKS5)",
            diagnosticsSummary = "IPv4 only; DNS proxy-safe; UDP/443 blocked.",
            lastError = null,
            canStartVpn = true,
            onStartVpn = {},
            onStopVpn = {},
            onManageProxies = {},
            onViewLogs = {},
            onViewDiagnostics = {},
            onPrivacyDisclosureAccepted = {},
            onDomainDestinationLoggingEnabled = {}
        )
    }
}
