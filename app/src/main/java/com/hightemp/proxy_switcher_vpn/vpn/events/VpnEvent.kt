package com.hightemp.proxy_switcher_vpn.vpn.events

enum class DnsEventStatus {
    QUERY,
    FAILURE
}

sealed interface VpnEvent {
    val timestampMillis: Long

    data class Dns(
        override val timestampMillis: Long,
        val status: DnsEventStatus,
        val domain: String?,
        val queryType: String?,
        val server: String? = null
    ) : VpnEvent

    data class UdpBlocked(
        override val timestampMillis: Long,
        val destination: String? = null,
        val port: Int? = null
    ) : VpnEvent

    data class Status(
        override val timestampMillis: Long,
        val connectionsIn: Int? = null,
        val connectionsOut: Int? = null,
        val trafficAvailable: Boolean = false,
        val uplinkTotal: Long? = null,
        val downlinkTotal: Long? = null
    ) : VpnEvent
}
