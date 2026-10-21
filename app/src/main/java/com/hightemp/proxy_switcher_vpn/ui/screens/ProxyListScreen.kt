package com.hightemp.proxy_switcher_vpn.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.asString
import com.hightemp.proxy_switcher_vpn.i18n.resolve
import com.hightemp.proxy_switcher_vpn.proxy.ProxyCheckParsers
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.BulkTestProgress
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.DeletedProxy
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyDeleteCheck
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyTestStatus
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyTestUiState
import kotlinx.coroutines.launch

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
    onDeleteProxy: (ProxyEntity, (DeletedProxy) -> Unit) -> Unit,
    onTestProxy: (ProxyEntity) -> Unit,
    onExportProxies: () -> String,
    onImportProxies: (String, (Int, UserText?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    proxyDeleteCheck: (ProxyEntity) -> ProxyDeleteCheck = { ProxyDeleteCheck.ALLOWED },
    onUndoDeleteProxy: (DeletedProxy) -> Unit = {},
    proxyChecks: Map<Long, ProxyCheckEntity> = emptyMap(),
    speedTestStates: Map<Long, ProxyTestUiState> = emptyMap(),
    bulkTestProgress: BulkTestProgress? = null,
    onTestAllProxies: (List<ProxyEntity>) -> Unit = {},
    onSpeedTestProxy: (ProxyEntity) -> Unit = {},
    onImportLines: () -> Unit = {},
    sort: ProxySort = ProxySort.NEWEST,
    groupFilter: ProxyGroupFilter = ProxyGroupFilter.All,
    onListViewChanged: (ProxySort, ProxyGroupFilter) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var proxyPendingDelete by remember { mutableStateOf<ProxyEntity?>(null) }
    val deleteProxy = { proxy: ProxyEntity ->
        onDeleteProxy(proxy) { deleted ->
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.proxy_deleted),
                    actionLabel = context.getString(R.string.action_undo),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoDeleteProxy(deleted)
                }
            }
        }
    }
    val requestDelete = { proxy: ProxyEntity ->
        when (proxyDeleteCheck(proxy)) {
            ProxyDeleteCheck.ALLOWED -> deleteProxy(proxy)
            ProxyDeleteCheck.NEEDS_CONFIRMATION -> proxyPendingDelete = proxy
            ProxyDeleteCheck.BLOCKED_ACTIVE_VPN -> {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(context.getString(R.string.proxy_delete_blocked))
                }
            }
        }
    }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(proxies) { ProxyListQuery.groups(proxies) }
    val visibleProxies = remember(proxies, proxyChecks, query, sort, groupFilter) {
        ProxyListQuery.apply(proxies, proxyChecks, query, sort, groupFilter)
    }
    var exportText by remember { mutableStateOf("") }
    val showImportResult = { count: Int, error: UserText? ->
        if (error != null) {
            Toast.makeText(context, error.resolve(context), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.proxy_list_imported, count, count),
                Toast.LENGTH_SHORT
            ).show()
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
                Toast.makeText(context, R.string.proxy_list_saved_to_file, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.proxy_list_save_failed, error.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
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
                Toast.makeText(context, R.string.proxy_list_read_failed, Toast.LENGTH_LONG).show()
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
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = { Text(stringResource(R.string.proxy_list_title)) },
                actions = {
                    IconButton(
                        onClick = { onTestAllProxies(visibleProxies) },
                        enabled = visibleProxies.isNotEmpty() && bulkTestProgress?.running != true
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.proxy_list_test_all))
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.proxy_list_import))
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.proxy_import_menu_lines)) },
                                onClick = {
                                    showImportMenu = false
                                    onImportLines()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.proxy_import_menu_json)) },
                                onClick = {
                                    showImportMenu = false
                                    showImportDialog = true
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            exportText = onExportProxies()
                            showExportDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.proxy_list_export))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProxy) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.proxy_list_add))
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
                    .fillMaxSize(),
                // Bottom space so the add button never covers the last card's actions.
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                item(key = "list-controls") {
                    ProxyListControls(
                        query = query,
                        onQueryChange = { query = it },
                        sort = sort,
                        onSortSelected = { onListViewChanged(it, groupFilter) },
                        groups = groups,
                        groupFilter = groupFilter,
                        onGroupSelected = { onListViewChanged(sort, it) }
                    )
                }
                bulkTestProgress?.takeIf { it.running }?.let { progress ->
                    item(key = "bulk-progress") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.proxy_list_bulk_progress, progress.done, progress.total),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { progress.done.toFloat() / progress.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (visibleProxies.isEmpty()) {
                    item(key = "nothing-found") {
                        Text(
                            text = stringResource(R.string.proxy_list_nothing_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
                items(
                    items = visibleProxies,
                    key = { proxy -> proxy.id }
                ) { proxy ->
                    ProxyListItem(
                        proxy = proxy,
                        selected = proxy.id == selectedProxyId,
                        testState = proxyTestResults[proxy.id],
                        check = proxyChecks[proxy.id],
                        speedState = speedTestStates[proxy.id],
                        onSelect = { onSelectProxy(proxy) },
                        onEdit = { onEditProxy(proxy) },
                        onDelete = { requestDelete(proxy) },
                        onTest = { onTestProxy(proxy) },
                        onSpeedTest = { onSpeedTestProxy(proxy) }
                    )
                }
            }
        }
    }

    proxyPendingDelete?.let { proxy ->
        AlertDialog(
            onDismissRequest = { proxyPendingDelete = null },
            title = { Text(stringResource(R.string.proxy_delete_confirm_title)) },
            text = { Text(stringResource(R.string.proxy_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        proxyPendingDelete = null
                        deleteProxy(proxy)
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { proxyPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
        title = { Text(stringResource(R.string.proxy_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.proxy_export_warning),
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
                    Toast.makeText(context, R.string.proxy_export_copied, Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(stringResource(R.string.action_copy))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveToFile) {
                    Text(stringResource(R.string.proxy_export_save_to_file))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
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
        title = { Text(stringResource(R.string.proxy_import_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.proxy_import_hint),
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
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLoadFromFile) {
                    Text(stringResource(R.string.proxy_import_load_from_file))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

/** Search field, sort menu and group filter chips above the proxy list. */
@Composable
private fun ProxyListControls(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: ProxySort,
    onSortSelected: (ProxySort) -> Unit,
    groups: List<String>,
    groupFilter: ProxyGroupFilter,
    onGroupSelected: (ProxyGroupFilter) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.proxy_list_search),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.proxy_list_search_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.proxy_list_sort))
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    ProxySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label())) },
                            leadingIcon = {
                                if (option == sort) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = {
                                showSortMenu = false
                                onSortSelected(option)
                            }
                        )
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            val selected = groupFilter.takeUnless { it is ProxyGroupFilter.Named && it.name !in groups }
                ?: ProxyGroupFilter.All
            val filters = listOf(ProxyGroupFilter.All) + groups.map(ProxyGroupFilter::Named) + ProxyGroupFilter.Ungrouped
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { filter ->
                    FilterChip(
                        selected = filter == selected,
                        onClick = { onGroupSelected(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    ProxyGroupFilter.All -> stringResource(R.string.proxy_group_all)
                                    ProxyGroupFilter.Ungrouped -> stringResource(R.string.proxy_group_none)
                                    is ProxyGroupFilter.Named -> filter.name
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun ProxySort.label(): Int = when (this) {
    ProxySort.NEWEST -> R.string.proxy_sort_newest
    ProxySort.NAME -> R.string.proxy_sort_name
    ProxySort.LATENCY -> R.string.proxy_sort_latency
    ProxySort.TYPE_COUNTRY -> R.string.proxy_sort_type_country
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
            text = stringResource(R.string.proxy_list_empty_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.proxy_list_empty_body),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProxyListItem(
    proxy: ProxyEntity,
    selected: Boolean,
    testState: ProxyTestUiState?,
    check: ProxyCheckEntity?,
    speedState: ProxyTestUiState?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onSpeedTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onSelect),
        // Selection is a border, not a fill: a dynamic primaryContainer can clash with error text.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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
                            contentDescription = stringResource(R.string.proxy_list_selected),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = listOfNotNull(
                        "${proxy.type}  ${proxy.host}:${proxy.port}",
                        proxy.groupName?.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        if (proxy.isEnabled) R.string.proxy_enabled else R.string.proxy_disabled
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                check?.let { ProxyCheckSummary(it) }
                speedState?.let { state ->
                    Text(
                        text = state.message.asString(),
                        color = if (state.status == ProxyTestStatus.FAILURE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                testState?.takeIf { it.status != ProxyTestStatus.SUCCESS }?.let { state ->
                    Text(
                        text = state.message.asString(),
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
                        Text(stringResource(R.string.action_select))
                    }
                }
                TextButton(
                    onClick = onTest,
                    enabled = testState?.status != ProxyTestStatus.TESTING
                ) {
                    Text(
                        stringResource(
                            if (testState?.status == ProxyTestStatus.TESTING) {
                                R.string.proxy_testing
                            } else {
                                R.string.action_test
                            }
                        )
                    )
                }
                IconButton(
                    onClick = onSpeedTest,
                    enabled = speedState?.status != ProxyTestStatus.TESTING
                ) {
                    Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.proxy_speed_test))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.proxy_list_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.proxy_list_delete))
                }
            }
        }
    }
}

/** "🇩🇪 DE · 203.0.113.5 · 120 ms · 54.3 Mbit/s" and when it was checked. */
@Composable
private fun ProxyCheckSummary(check: ProxyCheckEntity) {
    val locale = LocalConfiguration.current.locales[0]
    val parts = buildList {
        if (check.success == false) {
            add(stringResource(R.string.proxy_check_unavailable))
        }
        check.countryCode?.let { code ->
            add(listOfNotNull(ProxyCheckParsers.countryFlag(code), code).joinToString(" "))
        }
        check.exitIp?.let(::add)
        check.latencyMillis?.takeIf { check.success == true }?.let {
            add(stringResource(R.string.proxy_check_latency, it))
        }
        check.downloadBitsPerSecond?.let {
            add(stringResource(R.string.proxy_check_speed, ProxyCheckParsers.formatMegabits(it, locale)))
        }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            color = if (check.success == false) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
    val checkedAt = listOfNotNull(check.checkedAtMillis, check.speedCheckedAtMillis).maxOrNull()
    if (checkedAt != null) {
        val now = System.currentTimeMillis()
        Text(
            text = if (now - checkedAt < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.proxy_check_checked_just_now)
            } else {
                stringResource(
                    R.string.proxy_check_checked_at,
                    DateUtils.getRelativeTimeSpanString(checkedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
                )
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
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
                    message = UserText.of(R.string.proxy_test_connect_failed)
                )
            ),
            onBack = {},
            onAddProxy = {},
            onEditProxy = {},
            onSelectProxy = {},
            onDeleteProxy = { _, _ -> },
            onTestProxy = {},
            onExportProxies = { "[]" },
            onImportProxies = { _, onResult -> onResult(0, null) }
        )
    }
}
