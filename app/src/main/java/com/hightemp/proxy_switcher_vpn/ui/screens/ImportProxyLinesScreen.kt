package com.hightemp.proxy_switcher_vpn.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.asString
import com.hightemp.proxy_switcher_vpn.i18n.resolve
import com.hightemp.proxy_switcher_vpn.proxy.ProxyFieldOrder
import com.hightemp.proxy_switcher_vpn.proxy.ProxyImportLine
import com.hightemp.proxy_switcher_vpn.proxy.ProxyImportPreview
import com.hightemp.proxy_switcher_vpn.proxy.ProxyImportStatus
import com.hightemp.proxy_switcher_vpn.proxy.ProxyLineImport
import com.hightemp.proxy_switcher_vpn.proxy.ProxyLineImportOptions
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.BulkTestProgress
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.ProxyLineImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportProxyLinesScreen(
    proxies: List<ProxyEntity>,
    progress: BulkTestProgress?,
    onBack: () -> Unit,
    onImport: (List<ProxyImportLine>, (ProxyLineImportResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Plain remember: the text holds credentials and must not go into saved instance state.
    var text by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf<ProxyType?>(null) }
    var fieldOrder by remember { mutableStateOf(ProxyFieldOrder.HOST_FIRST) }
    var resultMessage by remember { mutableStateOf<UserText?>(null) }
    val importing = progress?.running == true
    val preview by produceState(ProxyImportPreview(emptyList()), text, protocol, fieldOrder, proxies) {
        delay(PREVIEW_DEBOUNCE_MILLIS)
        value = withContext(Dispatchers.Default) {
            ProxyLineImport.preview(text, ProxyLineImportOptions(protocol, fieldOrder), proxies)
        }
    }
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readNBytesCompat(ProxyLineImport.MAX_TEXT_LENGTH + 1)
            }
        }.getOrNull()
        when {
            bytes == null -> Toast.makeText(context, R.string.proxy_list_read_failed, Toast.LENGTH_LONG).show()
            bytes.size > ProxyLineImport.MAX_TEXT_LENGTH ->
                Toast.makeText(context, R.string.import_lines_file_too_large, Toast.LENGTH_LONG).show()
            else -> {
                text = String(bytes, Charsets.UTF_8).removePrefix(UTF8_BOM)
                resultMessage = null
            }
        }
    }
    val importLines = {
        resultMessage = null
        onImport(preview.lines) { result ->
            val summary = UserText.of(R.string.import_lines_result, result.added, result.updated)
            if (result.failedLines.isEmpty()) {
                Toast.makeText(context, summary.resolve(context), Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                text = result.failedLines.joinToString("\n") { it.raw }
                resultMessage = UserText.of(
                    R.string.diag_time_and_text,
                    summary,
                    UserText.Plural(R.plurals.import_lines_failed, result.failedLines.size)
                )
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
                title = { Text(stringResource(R.string.import_lines_title)) }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.import_lines_hint, ProxyLineImport.MAX_RECORDS),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it.take(ProxyLineImport.MAX_TEXT_LENGTH)
                            resultMessage = null
                        },
                        label = { Text(stringResource(R.string.import_lines_text_label)) },
                        enabled = !importing,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            openFileLauncher.launch(arrayOf("text/plain", "text/csv", "text/comma-separated-values", "*/*"))
                        },
                        enabled = !importing
                    ) {
                        Text(stringResource(R.string.proxy_import_load_from_file))
                    }
                    ImportOptions(
                        protocol = protocol,
                        onProtocolSelected = { protocol = it },
                        showFieldOrder = preview.hasAmbiguous,
                        fieldOrder = fieldOrder,
                        onFieldOrderSelected = { fieldOrder = it },
                        enabled = !importing
                    )
                    resultMessage?.let { message ->
                        Text(
                            text = message.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (importing && progress != null && progress.total > 0) {
                        Text(
                            text = stringResource(R.string.import_lines_detecting, progress.done, progress.total),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val importable = preview.importable.size
                    Button(
                        onClick = importLines,
                        enabled = importable > 0 && !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pluralStringResource(R.plurals.import_lines_button, importable, importable))
                    }
                    PreviewSummary(preview)
                }
            }
            items(preview.lines, key = { it.lineNumber }) { line ->
                ImportLineRow(line)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportOptions(
    protocol: ProxyType?,
    onProtocolSelected: (ProxyType?) -> Unit,
    showFieldOrder: Boolean,
    fieldOrder: ProxyFieldOrder,
    onFieldOrderSelected: (ProxyFieldOrder) -> Unit,
    enabled: Boolean
) {
    Text(
        text = stringResource(R.string.import_lines_protocol),
        style = MaterialTheme.typography.titleSmall
    )
    val protocols = listOf<ProxyType?>(null) + ProxyType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        protocols.forEachIndexed { index, type ->
            SegmentedButton(
                selected = protocol == type,
                onClick = { onProtocolSelected(type) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = protocols.size)
            ) {
                Text(
                    text = type?.name ?: stringResource(R.string.import_lines_protocol_auto),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
    if (protocol == null) {
        Text(
            text = stringResource(R.string.import_lines_auto_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showFieldOrder) {
        Text(
            text = stringResource(R.string.import_lines_field_order),
            style = MaterialTheme.typography.bodySmall
        )
        val orders = listOf(
            ProxyFieldOrder.HOST_FIRST to "host:port:user:pass",
            ProxyFieldOrder.CREDENTIALS_FIRST to "user:pass:host:port"
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            orders.forEachIndexed { index, (order, title) ->
                SegmentedButton(
                    selected = fieldOrder == order,
                    onClick = { onFieldOrderSelected(order) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size)
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PreviewSummary(preview: ProxyImportPreview) {
    if (preview.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.import_lines_preview_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val counts = listOf(
        ProxyImportStatus.NEW to R.string.import_lines_count_new,
        ProxyImportStatus.UPDATE to R.string.import_lines_count_update,
        ProxyImportStatus.UNCHANGED to R.string.import_lines_count_unchanged,
        ProxyImportStatus.DUPLICATE to R.string.import_lines_count_duplicate,
        ProxyImportStatus.ERROR to R.string.import_lines_count_error
    ).mapNotNull { (status, label) ->
        preview.count(status).takeIf { it > 0 }?.let { stringResource(label, it) }
    }
    HorizontalDivider()
    Text(
        text = counts.joinToString(" · "),
        style = MaterialTheme.typography.titleSmall
    )
    if (preview.droppedCount > 0) {
        Text(
            text = stringResource(R.string.import_lines_dropped, ProxyLineImport.MAX_RECORDS, preview.droppedCount),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** One preview line: never shows the username or password, only whether there is a login. */
@Composable
private fun ImportLineRow(line: ProxyImportLine) {
    val isError = line.status == ProxyImportStatus.ERROR
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.import_line_number, line.lineNumber),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = line.message?.asString() ?: stringResource(line.status.label()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                )
            }
            line.proxy?.let { proxy ->
                val details = listOfNotNull(
                    ProxyLineImport.displayText(proxy),
                    stringResource(R.string.import_line_protocol_auto).takeIf { line.needsDetection },
                    stringResource(R.string.import_line_with_login).takeIf { proxy.username != null }
                )
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun ProxyImportStatus.label(): Int = when (this) {
    ProxyImportStatus.NEW -> R.string.import_line_status_new
    ProxyImportStatus.UPDATE -> R.string.import_line_status_update
    ProxyImportStatus.UNCHANGED -> R.string.import_line_status_unchanged
    ProxyImportStatus.DUPLICATE -> R.string.import_line_status_duplicate
    ProxyImportStatus.ERROR -> R.string.proxy_string_error_format
}

/** Reads at most [limit] bytes; `InputStream.readNBytes` needs API 33. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (output.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (read == -1) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val PREVIEW_DEBOUNCE_MILLIS = 200L
private const val UTF8_BOM = "\uFEFF"
