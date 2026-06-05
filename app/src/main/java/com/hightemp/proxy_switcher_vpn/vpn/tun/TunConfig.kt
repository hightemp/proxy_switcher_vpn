package com.hightemp.proxy_switcher_vpn.vpn.tun

data class TunAddress(
    val address: String,
    val prefixLength: Int
)

data class TunRoute(
    val destination: String,
    val prefixLength: Int
)

enum class DnsRouteStrategy {
    PROXY_SAFE,
    DIRECT_UNSUPPORTED
}

enum class Ipv6Mode {
    UNSUPPORTED_DISABLED
}

data class TunConfig(
    val sessionName: String,
    val mtu: Int,
    val ipv4Address: TunAddress,
    val ipv4Routes: List<TunRoute>,
    val dnsServerAddresses: List<String>,
    val dnsRouteStrategy: DnsRouteStrategy,
    val ipv6Mode: Ipv6Mode,
    val ipv6Routes: List<TunRoute>
) {
    val isIpv4Only: Boolean =
        ipv6Mode == Ipv6Mode.UNSUPPORTED_DISABLED && ipv6Routes.isEmpty()

    companion object {
        fun mvpDefault(): TunConfig {
            return TunConfig(
                sessionName = "Proxy Switcher VPN",
                mtu = 9000,
                ipv4Address = TunAddress(
                    address = "172.19.0.1",
                    prefixLength = 30
                ),
                ipv4Routes = listOf(
                    TunRoute(destination = "0.0.0.0", prefixLength = 1),
                    TunRoute(destination = "128.0.0.0", prefixLength = 1)
                ),
                dnsServerAddresses = listOf("172.19.0.2"),
                dnsRouteStrategy = DnsRouteStrategy.PROXY_SAFE,
                ipv6Mode = Ipv6Mode.UNSUPPORTED_DISABLED,
                ipv6Routes = emptyList()
            )
        }
    }
}
