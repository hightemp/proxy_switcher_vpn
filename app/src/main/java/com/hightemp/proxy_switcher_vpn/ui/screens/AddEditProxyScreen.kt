package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProxyScreen(
    proxy: ProxyEntity?,
    isEdit: Boolean,
    onBack: () -> Unit,
    onSave: (ProxyEntity) -> Unit,
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
                title = { Text(if (isEdit) "Edit proxy" else "Add proxy") }
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
                onCancel = onBack,
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
            text = "Proxy was not found.",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyForm(
    proxy: ProxyEntity?,
    onSave: (ProxyEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var label by remember(proxy?.id) { mutableStateOf(proxy?.label.orEmpty()) }
    var host by remember(proxy?.id) { mutableStateOf(proxy?.host.orEmpty()) }
    var port by remember(proxy?.id) { mutableStateOf(proxy?.port?.toString().orEmpty()) }
    var type by remember(proxy?.id) { mutableStateOf(proxy?.type ?: ProxyType.SOCKS5) }
    var username by remember(proxy?.id) { mutableStateOf(proxy?.username.orEmpty()) }
    var password by remember(proxy?.id) { mutableStateOf(proxy?.password.orEmpty()) }
    var isEnabled by remember(proxy?.id) { mutableStateOf(proxy?.isEnabled ?: true) }
    var errorMessage by remember(proxy?.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                errorMessage = null
            },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it
                errorMessage = null
            },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ProtocolSelector(
            selectedType = type,
            onTypeSelected = { type = it }
        )
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text("Username (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password (optional)") },
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
                text = "Enabled",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it }
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
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
                Text("Cancel")
            }
            Button(
                onClick = {
                    val validation = ProxyFormValidator.validate(
                        hostInput = host,
                        portInput = port,
                        usernameInput = username,
                        passwordInput = password
                    )
                    if (validation.isValid) {
                        onSave(
                            ProxyEntity(
                                id = proxy?.id ?: 0L,
                                host = validation.host,
                                port = validation.port,
                                type = type,
                                username = validation.username,
                                password = validation.password,
                                label = label.trim().ifBlank { null },
                                isEnabled = isEnabled
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
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolSelector(
    selectedType: ProxyType,
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
            onSave = {}
        )
    }
}
