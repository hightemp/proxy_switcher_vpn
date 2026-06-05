package com.hightemp.proxy_switcher_vpn.vpn.dns

enum class DnsTransport {
    DNS_OVER_HTTPS
}

enum class DnsRouteMode {
    SELECTED_PROXY
}

data class DnsMode(
    val transport: DnsTransport,
    val routeMode: DnsRouteMode,
    val server: String,
    val serverPort: Int,
    val path: String,
    val detourOutboundTag: String
) {
    val isProxySafe: Boolean =
        routeMode == DnsRouteMode.SELECTED_PROXY && detourOutboundTag.isNotBlank()

    companion object {
        fun proxySafeDoh(detourOutboundTag: String): DnsMode {
            return DnsMode(
                transport = DnsTransport.DNS_OVER_HTTPS,
                routeMode = DnsRouteMode.SELECTED_PROXY,
                server = "1.1.1.1",
                serverPort = 443,
                path = "/dns-query",
                detourOutboundTag = detourOutboundTag
            )
        }
    }
}
