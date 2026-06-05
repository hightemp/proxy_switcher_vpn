package com.hightemp.proxy_switcher_vpn.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
    onExportProxies: () -> String,
    onImportProxies: (String, (Int, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val showImportResult = { count: Int, error: String? ->
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Imported $count proxies", Toast.LENGTH_SHORT).show()
        }
    }
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(exportText.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open output file")
            }.onSuccess {
                Toast.makeText(context, "Saved to file", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Save failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    String(input.readBytes(), Charsets.UTF_8)
                }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(context, "Could not read file", Toast.LENGTH_LONG).show()
            } else {
                onImportProxies(text, showImportResult)
            }
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
                title = { Text("Proxies") },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Import proxies")
                    }
                    IconButton(
                        onClick = {
                            exportText = onExportProxies()
                            showExportDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Export proxies")
                    }
                }
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

    if (showExportDialog) {
        ExportProxyDialog(
            text = exportText,
            onDismiss = { showExportDialog = false },
            onSaveToFile = { saveFileLauncher.launch("proxies.json") }
        )
    }

    if (showImportDialog) {
        ImportProxyDialog(
            onDismiss = { showImportDialog = false },
            onImportText = { text ->
                onImportProxies(text) { count, error ->
                    showImportResult(count, error)
                    if (error == null) {
                        showImportDialog = false
                    }
                }
            },
            onLoadFromFile = {
                showImportDialog = false
                openFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }
}

@Composable
private fun ExportProxyDialog(
    text: String,
    onDismiss: () -> Unit,
    onSaveToFile: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Proxies") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Export includes saved proxy credentials.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveToFile) {
                    Text("Save to file")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun ImportProxyDialog(
    onDismiss: () -> Unit,
    onImportText: (String) -> Unit,
    onLoadFromFile: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Proxies") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Paste exported JSON below, or load it from a file.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("JSON") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImportText(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLoadFromFile) {
                    Text("Load from file")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
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
            onTestProxy = {},
            onExportProxies = { "[]" },
            onImportProxies = { _, onResult -> onResult(0, null) }
        )
    }
}
