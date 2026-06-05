package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyTestStatus
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyTestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyListScreen(
    proxies: List<ProxyEntity>,
    selectedProxyId: Long?,
    proxyTestResults: Map<Long, ProxyTestUiState>,
    onBack: () -> Unit,
    onAddProxy: () -> Unit,
    onEditProxy: (ProxyEntity) -> Unit,
    onSelectProxy: (ProxyEntity) -> Unit,
    onDeleteProxy: (ProxyEntity) -> Unit,
    onTestProxy: (ProxyEntity) -> Unit,
    modifier: Modifier = Modifier
) {
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
                title = { Text("Proxies") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProxy) {
                Icon(Icons.Default.Add, contentDescription = "Add proxy")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (proxies.isEmpty()) {
            EmptyProxyList(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                items(
                    items = proxies,
                    key = { proxy -> proxy.id }
                ) { proxy ->
                    ProxyListItem(
                        proxy = proxy,
                        selected = proxy.id == selectedProxyId,
                        testState = proxyTestResults[proxy.id],
                        onSelect = { onSelectProxy(proxy) },
                        onEdit = { onEditProxy(proxy) },
                        onDelete = { onDeleteProxy(proxy) },
                        onTest = { onTestProxy(proxy) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProxyList(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No saved proxies",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Add a SOCKS5, HTTP, or HTTPS proxy before starting VPN.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProxyListItem(
    proxy: ProxyEntity,
    selected: Boolean,
    testState: ProxyTestUiState?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = proxy.label?.takeIf { it.isNotBlank() }
                            ?: "${proxy.host}:${proxy.port}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${proxy.type}  ${proxy.host}:${proxy.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (proxy.isEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall
                )
                testState?.let { state ->
                    Text(
                        text = state.message,
                        color = when (state.status) {
                            ProxyTestStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                            ProxyTestStatus.FAILURE -> MaterialTheme.colorScheme.error
                            ProxyTestStatus.TESTING -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!selected) {
                    TextButton(onClick = onSelect) {
                        Text("Select")
                    }
                }
                TextButton(
                    onClick = onTest,
                    enabled = testState?.status != ProxyTestStatus.TESTING
                ) {
                    Text(if (testState?.status == ProxyTestStatus.TESTING) "Testing" else "Test")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit proxy")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete proxy")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProxyListScreenPreview() {
    Proxy_switcher_vpnTheme {
        ProxyListScreen(
            proxies = listOf(
                ProxyEntity(
                    id = 1L,
                    host = "proxy.example",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    label = "Primary"
                ),
                ProxyEntity(
                    id = 2L,
                    host = "https-proxy.example",
                    port = 8443,
                    type = ProxyType.HTTPS
                )
            ),
            selectedProxyId = 1L,
            proxyTestResults = mapOf(
                2L to ProxyTestUiState(
                    status = ProxyTestStatus.FAILURE,
                    message = "Could not connect to proxy."
                )
            ),
            onBack = {},
            onAddProxy = {},
            onEditProxy = {},
            onSelectProxy = {},
            onDeleteProxy = {},
            onTestProxy = {}
        )
    }
}
