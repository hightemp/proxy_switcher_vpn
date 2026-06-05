package com.hightemp.proxy_switcher_vpn.vpn.stats

import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters

data class VpnStats(
    val isRunning: Boolean = false,
    val startedAtMillis: Long? = null,
    val stoppedAtMillis: Long? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val activeConnections: Int = 0,
    val totalConnections: Long = 0L,
    val failedConnections: Long = 0L,
    val dnsQueries: Long = 0L,
    val blockedUdp: Long = 0L,
    val bypassedUdp: Long = 0L,
    val trafficStatsAvailable: Boolean = false,
    val activeConnectionsAvailable: Boolean = false,
    val lastError: String? = null
) {
    val totalBytes: Long
        get() = bytesIn + bytesOut

    fun uptimeMillis(nowMillis: Long): Long {
        val startedAt = startedAtMillis ?: return 0L
        val endMillis = if (isRunning) {
            nowMillis
        } else {
            stoppedAtMillis ?: nowMillis
        }
        return maxOf(0L, endMillis - startedAt)
    }

    fun toEngineCounters(): VpnEngineCounters {
        return VpnEngineCounters(
            bytesIn = bytesIn,
            bytesOut = bytesOut,
            activeConnections = activeConnections,
            totalConnections = totalConnections,
            failedConnections = failedConnections,
            dnsQueries = dnsQueries,
            blockedUdp = blockedUdp,
            bypassedUdp = bypassedUdp
        )
    }
}
