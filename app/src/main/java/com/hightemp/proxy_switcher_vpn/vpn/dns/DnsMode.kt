package com.hightemp.proxy_switcher_vpn.vpn.dns

enum class DnsTransport {
    DNS_OVER_HTTPS
}

enum class DnsRouteMode {
    SELECTED_PROXY,
    DIRECT_EXPLICIT
}

data class DnsMode(
    val transport: DnsTransport,
    val routeMode: DnsRouteMode,
    val server: String,
    val serverPort: Int,
    val path: String,
    val detourOutboundTag: String?
) {
    val isProxySafe: Boolean =
        when (routeMode) {
            DnsRouteMode.SELECTED_PROXY -> !detourOutboundTag.isNullOrBlank()
            DnsRouteMode.DIRECT_EXPLICIT -> true
        }

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

        fun directDoh(): DnsMode {
            return DnsMode(
                transport = DnsTransport.DNS_OVER_HTTPS,
                routeMode = DnsRouteMode.DIRECT_EXPLICIT,
                server = "1.1.1.1",
                serverPort = 443,
                path = "/dns-query",
                detourOutboundTag = null
            )
        }
    }
}
