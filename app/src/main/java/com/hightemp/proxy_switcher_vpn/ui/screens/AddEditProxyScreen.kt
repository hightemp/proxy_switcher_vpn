package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.asString
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.proxy.ParsedProxyString
import com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParseResult
import com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParser
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProxyScreen(
    proxy: ProxyEntity?,
    isEdit: Boolean,
    onBack: () -> Unit,
    onSave: (ProxyEntity) -> Unit,
    onDetectProtocol: (ParsedProxyString, (ProxyType?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<String> = emptyList()
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
                title = {
                    Text(stringResource(if (isEdit) R.string.edit_proxy_title else R.string.add_proxy_title))
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isEdit && proxy == null) {
            MissingProxyState(
                onBack = onBack,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp)
            )
        } else {
            ProxyForm(
                proxy = proxy,
                onSave = onSave,
                onDetectProtocol = onDetectProtocol,
                onCancel = onBack,
                groups = groups,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun MissingProxyState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.edit_proxy_not_found),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyForm(
    proxy: ProxyEntity?,
    onSave: (ProxyEntity) -> Unit,
    onDetectProtocol: (ParsedProxyString, (ProxyType?) -> Unit) -> Unit,
    onCancel: () -> Unit,
    groups: List<String>,
    modifier: Modifier = Modifier
) {
    var label by remember(proxy?.id) { mutableStateOf(proxy?.label.orEmpty()) }
    var groupName by remember(proxy?.id) { mutableStateOf(proxy?.groupName.orEmpty()) }
    var host by remember(proxy?.id) { mutableStateOf(proxy?.host.orEmpty()) }
    var port by remember(proxy?.id) { mutableStateOf(proxy?.port?.toString().orEmpty()) }
    var type by remember(proxy?.id) { mutableStateOf<ProxyType?>(proxy?.type ?: ProxyType.SOCKS5) }
    var username by remember(proxy?.id) { mutableStateOf(proxy?.username.orEmpty()) }
    var password by remember(proxy?.id) { mutableStateOf(proxy?.password.orEmpty()) }
    var isEnabled by remember(proxy?.id) { mutableStateOf(proxy?.isEnabled ?: true) }
    var errorMessage by remember(proxy?.id) { mutableStateOf<UserText?>(null) }
    var proxyString by remember(proxy?.id) { mutableStateOf("") }
    var proxyStringError by remember(proxy?.id) { mutableStateOf<UserText?>(null) }
    var proxyStringInfo by remember(proxy?.id) { mutableStateOf<UserText?>(null) }
    var ambiguousParse by remember(proxy?.id) {
        mutableStateOf<ProxyStringParseResult.Ambiguous?>(null)
    }
    var isDetecting by remember(proxy?.id) { mutableStateOf(false) }
    var showFormatHelp by remember { mutableStateOf(false) }
    var detectGeneration by remember(proxy?.id) { mutableIntStateOf(0) }

    val applyParsed = { parsed: ParsedProxyString ->
        host = parsed.host
        port = parsed.port.toString()
        username = parsed.username.orEmpty()
        password = parsed.password.orEmpty()
        proxyString = ""
        errorMessage = null
        val generation = ++detectGeneration
        if (parsed.type != null) {
            type = parsed.type
            isDetecting = false
            proxyStringInfo = UserText.of(R.string.proxy_string_parsed, parsed.type.name)
        } else {
            type = null
            isDetecting = true
            proxyStringInfo = UserText.of(R.string.proxy_string_detecting)
            onDetectProtocol(parsed) { detected ->
                if (generation == detectGeneration) {
                    isDetecting = false
                    proxyStringInfo = null
                    when {
                        detected == null -> proxyStringError =
                            UserText.of(R.string.proxy_string_detect_failed)
                        type == null -> {
                            type = detected
                            proxyStringInfo = UserText.of(R.string.proxy_string_detected, detected.name)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProxyStringInput(
            value = proxyString,
            onValueChange = {
                proxyString = it
                proxyStringError = null
            },
            errorMessage = proxyStringError,
            infoMessage = proxyStringInfo,
            isDetecting = isDetecting,
            onShowHelp = { showFormatHelp = true },
            onParse = {
                proxyStringError = null
                proxyStringInfo = null
                ambiguousParse = null
                when (val result = ProxyStringParser.parse(proxyString)) {
                    is ProxyStringParseResult.Success -> applyParsed(result.proxy)
                    is ProxyStringParseResult.Ambiguous -> ambiguousParse = result
                    is ProxyStringParseResult.Failure -> proxyStringError = result.message
                }
            }
        )
        if (showFormatHelp) {
            ProxyStringFormatHelpDialog(onDismiss = { showFormatHelp = false })
        }
        ambiguousParse?.let { ambiguous ->
            FieldOrderSelector(
                onOrderSelected = { hostFirst ->
                    ambiguousParse = null
                    applyParsed(if (hostFirst) ambiguous.hostFirst else ambiguous.credentialsFirst)
                }
            )
        }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.proxy_field_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        GroupField(
            value = groupName,
            onValueChange = { groupName = it },
            groups = groups
        )
        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                errorMessage = null
            },
            label = { Text(stringResource(R.string.proxy_field_host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it
                errorMessage = null
            },
            label = { Text(stringResource(R.string.proxy_field_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ProtocolSelector(
            selectedType = type,
            onTypeSelected = {
                type = it
                errorMessage = null
            }
        )
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text(stringResource(R.string.proxy_field_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text(stringResource(R.string.proxy_field_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.proxy_enabled),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it }
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                enabled = !isDetecting,
                onClick = {
                    val selectedType = type
                    val validation = ProxyFormValidator.validate(
                        hostInput = host,
                        portInput = port,
                        usernameInput = username,
                        passwordInput = password
                    )
                    if (ambiguousParse != null) {
                        errorMessage = UserText.of(R.string.proxy_form_choose_order)
                    } else if (selectedType == null) {
                        errorMessage = UserText.of(R.string.proxy_form_select_protocol)
                    } else if (validation.isValid) {
                        onSave(
                            ProxyEntity(
                                id = proxy?.id ?: 0L,
                                host = validation.host,
                                port = validation.port,
                                type = selectedType,
                                username = validation.username,
                                password = validation.password,
                                label = label.trim().ifBlank { null },
                                isEnabled = isEnabled,
                                groupName = groupName.trim().ifBlank { null }
                            )
                        )
                    } else {
                        errorMessage = validation.errorMessage
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ProxyStringInput(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: UserText?,
    infoMessage: UserText?,
    isDetecting: Boolean,
    onShowHelp: () -> Unit,
    onParse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.proxy_string_field)) },
                placeholder = { Text("user:pass@host:port") },
                isError = errorMessage != null,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowHelp) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.proxy_string_formats))
            }
            OutlinedButton(
                onClick = onParse,
                enabled = value.isNotBlank() && !isDetecting
            ) {
                Text(stringResource(R.string.proxy_string_parse))
            }
        }
        if (isDetecting) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                infoMessage?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall) }
            }
        } else if (errorMessage == null) {
            Text(
                text = infoMessage?.asString()
                    ?: stringResource(R.string.proxy_string_supporting_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        errorMessage?.let {
            Text(
                text = it.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldOrderSelector(
    onOrderSelected: (hostFirst: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.proxy_string_ambiguous),
            style = MaterialTheme.typography.bodySmall
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(true to "host:port:user:pass", false to "user:pass:host:port")
                .forEachIndexed { index, (hostFirst, title) ->
                    SegmentedButton(
                        selected = false,
                        onClick = { onOrderSelected(hostFirst) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) {
                        Text(title, style = MaterialTheme.typography.labelSmall)
                    }
                }
        }
    }
}

/** Free-text group with suggestions from the groups already in use. */
@Composable
private fun GroupField(
    value: String,
    onValueChange: (String) -> Unit,
    groups: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.proxy_field_group)) },
            supportingText = { Text(stringResource(R.string.proxy_field_group_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val suggestions = groups.filter { group ->
            group != value.trim() && group.contains(value.trim(), ignoreCase = true)
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { group ->
                    SuggestionChip(
                        onClick = { onValueChange(group) },
                        label = { Text(group) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolSelector(
    selectedType: ProxyType?,
    onTypeSelected: (ProxyType) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ProxyType.entries.forEachIndexed { index, proxyType ->
            SegmentedButton(
                selected = selectedType == proxyType,
                onClick = { onTypeSelected(proxyType) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ProxyType.entries.size
                )
            ) {
                Text(proxyType.name)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddEditProxyScreenPreview() {
    Proxy_switcher_vpnTheme {
        AddEditProxyScreen(
            proxy = ProxyEntity(
                id = 1L,
                host = "proxy.example",
                port = 1080,
                type = ProxyType.SOCKS5,
                label = "Primary",
                username = "user"
            ),
            isEdit = true,
            onBack = {},
            onSave = {},
            onDetectProtocol = { _, _ -> }
        )
    }
}
