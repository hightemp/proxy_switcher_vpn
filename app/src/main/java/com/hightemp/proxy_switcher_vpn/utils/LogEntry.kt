package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel

enum class LogType {
    GENERAL,
    VPN,
    PROXY,
    DNS,
    UDP,
    STATS
}

data class LogEntry(
    val timestampMillis: Long,
    val level: VpnEngineLogLevel,
    val type: LogType,
    val message: String
)
