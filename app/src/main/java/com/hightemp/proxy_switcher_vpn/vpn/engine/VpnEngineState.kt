package com.hightemp.proxy_switcher_vpn.vpn.engine

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType

enum class VpnEngineStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

enum class VpnEngineLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class SelectedProxySummary(
    val id: Long,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val label: String?
) {
    companion object {
        fun from(proxy: ProxyEntity): SelectedProxySummary {
            return SelectedProxySummary(
                id = proxy.id,
                type = proxy.type,
                host = proxy.host,
                port = proxy.port,
                label = proxy.label
            )
        }
    }
}

data class VpnEngineState(
    val status: VpnEngineStatus = VpnEngineStatus.STOPPED,
    val selectedProxy: SelectedProxySummary? = null,
    val lastError: String? = null
)

data class VpnEngineCounters(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val activeConnections: Int = 0,
    val totalConnections: Long = 0,
    val failedConnections: Long = 0,
    val dnsQueries: Long = 0,
    val blockedUdp: Long = 0,
    val bypassedUdp: Long = 0
)

data class VpnEngineLog(
    val timestampMillis: Long,
    val level: VpnEngineLogLevel,
    val message: String
)
