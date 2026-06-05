package com.hightemp.proxy_switcher_vpn.vpn.singbox

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.dns.DnsMode
import com.hightemp.proxy_switcher_vpn.vpn.dns.DnsTransport
import com.hightemp.proxy_switcher_vpn.vpn.tun.TunConfig
import com.hightemp.proxy_switcher_vpn.vpn.udp.UdpPolicy

data class GeneratedSingBoxConfig(
    val json: String,
    val maskedPreview: String,
    val udpPolicy: UdpPolicy
)

class SingBoxConfigGenerator(
    private val serializer: SingBoxConfigSerializer = SingBoxConfigSerializer(),
    private val tunConfig: TunConfig = TunConfig.mvpDefault(),
    private val dnsMode: DnsMode = DnsMode.proxySafeDoh(DEFAULT_PROXY_OUTBOUND_TAG),
    private val udpPolicy: UdpPolicy = UdpPolicy.mvpDefault()
) {
    fun generate(selectedProxy: ProxyEntity): GeneratedSingBoxConfig {
        require(selectedProxy.host.isNotBlank()) { "Proxy host must not be blank." }
        require(selectedProxy.port in 1..65535) { "Proxy port must be between 1 and 65535." }
        require(tunConfig.isIpv4Only) { "MVP sing-box config supports IPv4-only TUN settings." }
        require(dnsMode.isProxySafe) { "MVP DNS must use a proxy-safe route." }
        require(dnsMode.transport == DnsTransport.DNS_OVER_HTTPS) {
            "MVP sing-box config currently supports DNS over HTTPS."
        }
        require(udpPolicy.blocksUdp443) { "MVP UDP policy must block UDP/443." }

        val config = SingBoxConfig(
            dns = dnsMode.toDnsConfig(),
            inbounds = listOf(tunConfig.toTunInbound()),
            outbounds = listOf(selectedProxy.toOutbound()),
            route = SingBoxRouteConfig(
                finalTag = DEFAULT_PROXY_OUTBOUND_TAG,
                autoDetectInterface = true,
                rules = listOf(
                    SingBoxDnsHijackRouteRule(),
                    tunConfig.toPrivateDnsRejectRule()
                ) + udpPolicy.toRouteRules()
            )
        )

        return GeneratedSingBoxConfig(
            json = serializer.serialize(config),
            maskedPreview = serializer.serialize(config, maskSecrets = true),
            udpPolicy = udpPolicy
        )
    }

    private fun ProxyEntity.toOutbound(): SingBoxOutbound {
        return when (type) {
            ProxyType.SOCKS5 -> SingBoxSocksOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = host,
                serverPort = port,
                username = username,
                password = password
            )
            ProxyType.HTTP -> SingBoxHttpOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = host,
                serverPort = port,
                username = username,
                password = password
            )
            ProxyType.HTTPS -> SingBoxHttpOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = host,
                serverPort = port,
                username = username,
                password = password,
                tls = SingBoxTlsConfig(enabled = true)
            )
        }
    }

    private fun TunConfig.toTunInbound(): SingBoxTunInbound {
        return SingBoxTunInbound(
            address = listOf("${ipv4Address.address}/${ipv4Address.prefixLength}"),
            mtu = mtu,
            dnsAddress = dnsServerAddresses,
            routeAddress = ipv4Routes.map { "${it.destination}/${it.prefixLength}" }
        )
    }

    private fun DnsMode.toDnsConfig(): SingBoxDnsConfig {
        return SingBoxDnsConfig(
            servers = listOf(
                SingBoxHttpsDnsServer(
                    server = server,
                    serverPort = serverPort,
                    path = path,
                    detour = detourOutboundTag
                )
            )
        )
    }

    private fun UdpPolicy.toRouteRules(): List<SingBoxRouteRule> {
        if (!blocksUdp443) return emptyList()
        return listOf(
            SingBoxRejectRouteRule(
                network = "udp",
                port = UdpPolicy.UDP_443_PORT,
                method = "drop"
            )
        )
    }

    private fun TunConfig.toPrivateDnsRejectRule(): SingBoxRouteRule {
        return SingBoxRejectRouteRule(
            network = "tcp",
            port = ANDROID_PRIVATE_DNS_PORT,
            ipCidrs = dnsServerAddresses.map { "$it/32" },
            method = "default"
        )
    }

    private companion object {
        const val ANDROID_PRIVATE_DNS_PORT = 853
    }
}
