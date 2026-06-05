package com.hightemp.proxy_switcher_vpn.vpn.stats

import com.hightemp.proxy_switcher_vpn.vpn.events.VpnEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VpnStatsStore(
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    fun startSession() {
        _stats.value = VpnStats(
            isRunning = true,
            startedAtMillis = nowMillis()
        )
    }

    fun stopSession() {
        _stats.update { current ->
            current.copy(
                isRunning = false,
                stoppedAtMillis = nowMillis()
            )
        }
    }

    fun recordBytes(
        bytesIn: Long = 0L,
        bytesOut: Long = 0L
    ) {
        if (bytesIn <= 0L && bytesOut <= 0L) return
        _stats.update { current ->
            current.copy(
                bytesIn = current.bytesIn + maxOf(0L, bytesIn),
                bytesOut = current.bytesOut + maxOf(0L, bytesOut),
                trafficStatsAvailable = true
            )
        }
    }

    fun recordConnectionOpened() {
        _stats.update { current ->
            current.copy(
                activeConnections = current.activeConnections + 1,
                totalConnections = current.totalConnections + 1,
                activeConnectionsAvailable = true
            )
        }
    }

    fun recordConnectionClosed() {
        _stats.update { current ->
            current.copy(
                activeConnections = maxOf(0, current.activeConnections - 1),
                activeConnectionsAvailable = true
            )
        }
    }

    fun recordFailure(message: String? = null) {
        _stats.update { current ->
            current.copy(
                failedConnections = current.failedConnections + 1,
                lastError = message ?: current.lastError
            )
        }
    }

    fun recordDnsQuery() {
        _stats.update { current ->
            current.copy(dnsQueries = current.dnsQueries + 1)
        }
    }

    fun recordBlockedUdp() {
        _stats.update { current ->
            current.copy(blockedUdp = current.blockedUdp + 1)
        }
    }

    fun recordBypassedUdp() {
        _stats.update { current ->
            current.copy(bypassedUdp = current.bypassedUdp + 1)
        }
    }

    fun applyEvent(event: VpnEvent) {
        when (event) {
            is VpnEvent.Dns -> recordDnsQuery()
            is VpnEvent.UdpBlocked -> recordBlockedUdp()
            is VpnEvent.Status -> applyStatus(event)
        }
    }

    private fun applyStatus(event: VpnEvent.Status) {
        _stats.update { current ->
            val activeConnections = if (
                event.connectionsIn != null && event.connectionsOut != null
            ) {
                maxOf(0, event.connectionsIn + event.connectionsOut)
            } else {
                null
            }

            current.copy(
                bytesIn = if (event.trafficAvailable && event.downlinkTotal != null) {
                    maxOf(0L, event.downlinkTotal)
                } else {
                    current.bytesIn
                },
                bytesOut = if (event.trafficAvailable && event.uplinkTotal != null) {
                    maxOf(0L, event.uplinkTotal)
                } else {
                    current.bytesOut
                },
                activeConnections = activeConnections ?: current.activeConnections,
                trafficStatsAvailable = event.trafficAvailable,
                activeConnectionsAvailable = activeConnections != null
            )
        }
    }
}
