package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyType {
    SOCKS5,
    HTTP,
    HTTPS,
    SSH
}

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val type: ProxyType,
    val username: String? = null,
    val password: String? = null,
    val label: String? = null,
    val isEnabled: Boolean = true,
    /** Optional user-defined group, shown as a filter on the proxy list. */
    val groupName: String? = null,
    /** SSH only: OpenSSH or PEM private key text. */
    val privateKey: String? = null,
    /** SSH only: passphrase of [privateKey]. */
    val privateKeyPassphrase: String? = null,
    /** SSH only: confirmed server key in `authorized_keys` form; null until the user confirms it. */
    val hostKey: String? = null
) {
    /** Secret values that must be masked wherever text about this proxy is logged or shown. */
    fun sensitiveValues(): List<String> {
        return listOfNotNull(username, password, privateKey, privateKeyPassphrase)
            .filter { it.isNotBlank() }
    }

    /** SSH key fields mean nothing for other protocols; drop them when the type changes. */
    fun withoutStaleSshFields(): ProxyEntity {
        if (type == ProxyType.SSH) return this
        return copy(privateKey = null, privateKeyPassphrase = null, hostKey = null)
    }

    override fun toString(): String {
        return "ProxyEntity(" +
            "id=$id, " +
            "host='$host', " +
            "port=$port, " +
            "type=$type, " +
            "username=${username?.let { "'***'" }}, " +
            "password=${password?.let { "'***'" }}, " +
            "label=${label?.let { "'$it'" }}, " +
            "isEnabled=$isEnabled, " +
            "groupName=${groupName?.let { "'$it'" }}, " +
            "privateKey=${privateKey?.let { "'***'" }}, " +
            "privateKeyPassphrase=${privateKeyPassphrase?.let { "'***'" }}, " +
            "hostKey=${hostKey?.let { "'$it'" }}" +
            ")"
    }
}
