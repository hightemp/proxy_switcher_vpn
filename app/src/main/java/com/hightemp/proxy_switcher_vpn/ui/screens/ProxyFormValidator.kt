package com.hightemp.proxy_switcher_vpn.ui.screens

data class ProxyFormValidationResult(
    val host: String = "",
    val port: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val errorMessage: String? = null
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
            host.isBlank() -> "Host is required"
            host.any { it.isWhitespace() } -> "Host must not contain whitespace"
            portText.isBlank() -> "Port is required"
            port == null -> "Port must be a number"
            port !in 1..65535 -> "Port must be between 1 and 65535"
            username == null && password != null -> "Username is required when password is set"
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
