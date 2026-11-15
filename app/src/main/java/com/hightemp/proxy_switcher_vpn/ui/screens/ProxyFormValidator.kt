package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.proxy.SshKeys

data class ProxyFormValidationResult(
    val host: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val errorMessage: UserText? = null
) {
    val isValid: Boolean = errorMessage == null
}

object ProxyFormValidator {
    fun validate(
        hostInput: String,
        portInput: String,
        usernameInput: String,
        passwordInput: String
    ): ProxyFormValidationResult {
        val host = hostInput.trim()
        val portText = portInput.trim()
        val port = portText.toIntOrNull()
        val username = usernameInput.trim().ifBlank { null }
        val password = passwordInput.takeIf { it.isNotBlank() }

        val error = when {
            host.isBlank() -> UserText.of(R.string.validation_host_required)
            host.any { it.isWhitespace() } -> UserText.of(R.string.validation_host_whitespace)
            portText.isBlank() -> UserText.of(R.string.validation_port_required)
            port == null -> UserText.of(R.string.validation_port_number)
            port !in 1..65535 -> UserText.of(R.string.validation_port_range)
            username == null && password != null ->
                UserText.of(R.string.validation_username_required)
            else -> null
        }

        return ProxyFormValidationResult(
            host = host,
            port = port ?: 0,
            username = username,
            password = password,
            errorMessage = error
        )
    }

    /**
     * Extra SSH rules on top of [validate]: a username and a password or a
     * private key in a format sing-box accepts. A missing server key does not
     * block saving; the VPN refuses to start until it is confirmed.
     */
    fun validateSsh(
        usernameInput: String,
        passwordInput: String,
        privateKeyInput: String
    ): UserText? {
        val privateKey = SshKeys.normalizePrivateKey(privateKeyInput)
        return when {
            usernameInput.isBlank() -> UserText.of(R.string.validation_ssh_username_required)
            passwordInput.isEmpty() && privateKey == null -> UserText.of(R.string.ssh_error_credentials_required)
            privateKey != null -> SshKeys.privateKeyProblem(privateKey)
            else -> null
        }
    }
}
