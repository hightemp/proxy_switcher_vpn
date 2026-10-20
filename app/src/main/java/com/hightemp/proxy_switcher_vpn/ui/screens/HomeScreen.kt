package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.i18n.AppLanguages
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.asString
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.VpnPermissionStatus
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.VpnPermissionUiState
import com.hightemp.proxy_switcher_vpn.vpn.background.AlwaysOnVpnStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionStatus
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
    lastError: UserText?,
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
    modifier: Modifier = Modifier,
    backgroundRestrictions: BackgroundRestrictionStatus = BackgroundRestrictionStatus(),
    alwaysOnStatus: AlwaysOnVpnStatus = AlwaysOnVpnStatus(),
    onFixBackgroundRestrictions: () -> Unit = {},
    onOpenVpnSettings: () -> Unit = {},
    languageTag: String = AppLanguages.SYSTEM,
    onLanguageSelected: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsDialogVisible by remember { mutableStateOf(false) }

    val statusMessage = (runtimeState.statusMessage ?: uiState.message).asString()
    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
        }
    }
    val lastErrorText = lastError?.asString()
    LaunchedEffect(lastErrorText) {
        if (!lastErrorText.isNullOrBlank()) {
            snackbarHostState.showSnackbar(lastErrorText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onManageProxies) {
                        Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.home_manage_proxies))
                    }
                    IconButton(onClick = onViewLogs) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = stringResource(R.string.home_view_logs)
                        )
                    }
                    IconButton(onClick = onViewDiagnostics) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = stringResource(R.string.home_view_diagnostics)
                        )
                    }
                    IconButton(onClick = { settingsDialogVisible = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
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
            if (backgroundRestrictions.needsAttention) {
                BackgroundRestrictionWarning(
                    status = backgroundRestrictions,
                    onFix = onFixBackgroundRestrictions
                )
            }

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
                Text(stringResource(if (isVpnActive) R.string.home_stop_vpn else R.string.home_start_vpn))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_route),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManageProxies) {
                    Text(stringResource(R.string.home_manage))
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
                        title = stringResource(R.string.route_direct),
                        subtitle = stringResource(R.string.route_direct_subtitle),
                        selected = selectedProxyId == null,
                        enabled = true,
                        onClick = onDirectSelected
                    )
                }
                items(proxies, key = { proxy -> proxy.id }) { proxy ->
                    RouteRow(
                        title = proxy.label?.takeIf { it.isNotBlank() } ?: proxy.host,
                        subtitle = if (proxy.isEnabled) {
                            "${proxy.type} ${proxy.host}:${proxy.port}"
                        } else {
                            stringResource(
                                R.string.route_proxy_disabled_subtitle,
                                "${proxy.type} ${proxy.host}:${proxy.port}"
                            )
                        },
                        selected = selectedProxyId == proxy.id,
                        enabled = proxy.isEnabled,
                        onClick = { onProxySelected(proxy) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.home_diagnostics_summary),
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
            onInfiniteReconnectEnabled = onInfiniteReconnectEnabled,
            backgroundRestrictions = backgroundRestrictions,
            alwaysOnStatus = alwaysOnStatus,
            onFixBackgroundRestrictions = onFixBackgroundRestrictions,
            onOpenVpnSettings = onOpenVpnSettings,
            languageTag = languageTag,
            onLanguageSelected = onLanguageSelected
        )
    }
}

@Composable
private fun BackgroundRestrictionWarning(
    status: BackgroundRestrictionStatus,
    onFix: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (status.backgroundRestricted == true) {
                            R.string.home_warning_background_restricted
                        } else {
                            R.string.home_warning_battery_optimized
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.home_warning_samsung),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.action_allow))
            }
        }
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
            text = stringResource(runtimeState.status.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.home_permission, stringResource(permissionStatus.labelRes())),
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
                stringResource(R.string.home_connections, stats.activeConnections)
            } else {
                stringResource(R.string.home_connections_unavailable)
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
    onInfiniteReconnectEnabled: (Boolean) -> Unit,
    backgroundRestrictions: BackgroundRestrictionStatus,
    alwaysOnStatus: AlwaysOnVpnStatus,
    onFixBackgroundRestrictions: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    languageTag: String,
    onLanguageSelected: (String) -> Unit
) {
    var languageMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val languageName = AppLanguages.supported
                    .firstOrNull { it.tag == languageTag }
                    ?.nativeName
                    ?: stringResource(R.string.settings_language_system)
                Box {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_language),
                        description = languageName,
                        actionLabel = stringResource(R.string.action_change),
                        onAction = { languageMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_language_system)) },
                            onClick = {
                                languageMenuExpanded = false
                                onLanguageSelected(AppLanguages.SYSTEM)
                            }
                        )
                        AppLanguages.supported.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.nativeName) },
                                onClick = {
                                    languageMenuExpanded = false
                                    onLanguageSelected(language.tag)
                                }
                            )
                        }
                    }
                }
                SettingsActionRow(
                    title = stringResource(R.string.settings_background),
                    description = stringResource(
                        when {
                            backgroundRestrictions.backgroundRestricted == true ->
                                R.string.settings_background_restricted
                            backgroundRestrictions.batteryOptimizationIgnored == false ->
                                R.string.settings_background_optimized
                            backgroundRestrictions.batteryOptimizationIgnored == true ->
                                R.string.settings_background_unrestricted
                            else -> R.string.settings_background_unknown
                        }
                    ),
                    actionLabel = stringResource(
                        if (backgroundRestrictions.needsAttention) R.string.action_allow else R.string.action_open
                    ),
                    onAction = onFixBackgroundRestrictions
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_always_on),
                    description = stringResource(
                        when (alwaysOnStatus.alwaysOn) {
                            true -> if (alwaysOnStatus.lockdown == true) {
                                R.string.settings_always_on_lockdown
                            } else {
                                R.string.settings_always_on_on
                            }
                            false -> R.string.settings_always_on_off
                            null -> R.string.settings_always_on_unknown
                        }
                    ),
                    actionLabel = stringResource(R.string.settings_open_system),
                    onAction = onOpenVpnSettings
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.settings_reconnect))
                        Text(
                            text = stringResource(R.string.settings_reconnect_body),
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
                        text = stringResource(R.string.settings_disclosure),
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
                        text = stringResource(R.string.settings_domain_logging),
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

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAction) {
            Text(actionLabel)
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
                message = UserText.of(R.string.permission_already_granted)
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

private fun VpnServiceStatus.labelRes(): Int = when (this) {
    VpnServiceStatus.STOPPED -> R.string.status_stopped
    VpnServiceStatus.STARTING -> R.string.status_starting
    VpnServiceStatus.RUNNING -> R.string.status_running
    VpnServiceStatus.STOPPING -> R.string.status_stopping
    VpnServiceStatus.ERROR -> R.string.status_error
}

private fun VpnPermissionStatus.labelRes(): Int = when (this) {
    VpnPermissionStatus.UNKNOWN -> R.string.permission_unknown
    VpnPermissionStatus.REQUIRED -> R.string.permission_required
    VpnPermissionStatus.REQUESTING -> R.string.permission_requesting
    VpnPermissionStatus.GRANTED -> R.string.permission_granted
    VpnPermissionStatus.DENIED -> R.string.permission_denied
}
