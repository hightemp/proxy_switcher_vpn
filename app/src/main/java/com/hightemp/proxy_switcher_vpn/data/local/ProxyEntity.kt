package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyType {
    SOCKS5,
    HTTP,
    HTTPS
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
    val isEnabled: Boolean = true
) {
    override fun toString(): String {
        return "ProxyEntity(" +
            "id=$id, " +
            "host='$host', " +
            "port=$port, " +
            "type=$type, " +
            "username=${username?.let { "'***'" }}, " +
            "password=${password?.let { "'***'" }}, " +
            "label=${label?.let { "'$it'" }}, " +
            "isEnabled=$isEnabled" +
            ")"
    }
}
