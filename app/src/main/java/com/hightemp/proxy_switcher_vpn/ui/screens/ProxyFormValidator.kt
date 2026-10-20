package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText

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
}
