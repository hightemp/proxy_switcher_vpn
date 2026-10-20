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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.asString
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.DiagnosticField
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.DiagnosticSeverity
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnostics
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnDiagnosticsScreen(
    diagnostics: VpnDiagnostics,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                title = { Text(stringResource(R.string.diag_title)) }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DiagnosticsSection(title = stringResource(R.string.diag_section_runtime)) {
                DiagnosticRow(diagnostics.vpnPermission)
                DiagnosticRow(diagnostics.foregroundService)
                DiagnosticRow(diagnostics.singBoxCore)
                DiagnosticRow(diagnostics.tunInterface)
                DiagnosticRow(diagnostics.processMemory)
            }

            DiagnosticsSection(title = stringResource(R.string.diag_section_background)) {
                DiagnosticRow(diagnostics.batteryOptimization)
                DiagnosticRow(diagnostics.backgroundRestriction)
                DiagnosticRow(diagnostics.alwaysOnVpn)
                DiagnosticRow(diagnostics.lastVpnStop)
                DiagnosticRow(diagnostics.lastProcessExit)
            }

            DiagnosticsSection(title = stringResource(R.string.diag_section_traffic)) {
                DiagnosticRow(diagnostics.dns)
                DiagnosticRow(diagnostics.ipv4Route)
                DiagnosticRow(diagnostics.ipv6)
                DiagnosticRow(diagnostics.udp)
                DiagnosticRow(diagnostics.selectedProxy)
                DiagnosticValueRow(stringResource(R.string.diag_udp443), diagnostics.udp443Status)
                DiagnosticValueRow(stringResource(R.string.diag_non_dns_udp), diagnostics.nonDnsUdpStatus)
            }

            CountersSection(
                counters = diagnostics.counters,
                trafficStatsStatus = diagnostics.trafficStatsStatus,
                activeConnectionStatsStatus = diagnostics.activeConnectionStatsStatus
            )

            DiagnosticsSection(title = stringResource(R.string.diag_section_last_error)) {
                Text(
                    text = diagnostics.lastError?.asString() ?: stringResource(R.string.diag_none),
                    color = if (diagnostics.lastError == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            DiagnosticsSection(title = stringResource(R.string.diag_section_config)) {
                Text(
                    text = diagnostics.maskedConfigPreview ?: stringResource(R.string.diag_no_config),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(
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
private fun DiagnosticRow(field: DiagnosticField) {
    DiagnosticValueRow(
        label = field.label.asString(),
        value = field.value.asString(),
        valueColor = field.severity.color()
    )
}

@Composable
private fun DiagnosticValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun CountersSection(
    counters: VpnEngineCounters,
    trafficStatsStatus: String,
    activeConnectionStatsStatus: String
) {
    DiagnosticsSection(title = stringResource(R.string.diag_section_counters)) {
        DiagnosticValueRow(stringResource(R.string.diag_bytes_in), formatBytes(counters.bytesIn))
        DiagnosticValueRow(stringResource(R.string.diag_bytes_out), formatBytes(counters.bytesOut))
        DiagnosticValueRow(stringResource(R.string.diag_total_connections), counters.totalConnections.toString())
        DiagnosticValueRow(stringResource(R.string.diag_failed_connections), counters.failedConnections.toString())
        DiagnosticValueRow(stringResource(R.string.diag_dns_queries), counters.dnsQueries.toString())
        DiagnosticValueRow(stringResource(R.string.diag_blocked_udp), counters.blockedUdp.toString())
        DiagnosticValueRow(stringResource(R.string.diag_bypassed_udp), counters.bypassedUdp.toString())
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DiagnosticValueRow(stringResource(R.string.diag_traffic_stats), trafficStatsStatus)
        DiagnosticValueRow(stringResource(R.string.diag_active_connections), activeConnectionStatsStatus)
    }
}

@Composable
private fun DiagnosticSeverity.color(): Color {
    return when (this) {
        DiagnosticSeverity.OK -> MaterialTheme.colorScheme.primary
        DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
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
private fun VpnDiagnosticsScreenPreview() {
    Proxy_switcher_vpnTheme {
        VpnDiagnosticsScreen(
            diagnostics = VpnDiagnostics(
                counters = VpnEngineCounters(
                    bytesIn = 2048L,
                    bytesOut = 4096L,
                    blockedUdp = 3L
                ),
                maskedConfigPreview = "{\n  \"outbounds\": [\"***\"]\n}"
            ),
            onBack = {}
        )
    }
}
