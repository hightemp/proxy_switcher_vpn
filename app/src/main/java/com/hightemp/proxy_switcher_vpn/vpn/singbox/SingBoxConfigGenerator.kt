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

data class SingBoxProxyEndpoint(
    val server: String,
    val tlsServerName: String? = null,
    val domainResolver: String? = null
) {
    companion object {
        fun fromProxy(selectedProxy: ProxyEntity): SingBoxProxyEndpoint {
            val trimmedHost = selectedProxy.host.trim()
            return SingBoxProxyEndpoint(
                server = trimmedHost,
                domainResolver = if (trimmedHost.isNumericAddressLiteral()) {
                    null
                } else {
                    DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG
                }
            )
        }

        fun resolved(
            selectedProxy: ProxyEntity,
            resolvedServer: String
        ): SingBoxProxyEndpoint {
            val trimmedHost = selectedProxy.host.trim()
            val trimmedServer = resolvedServer.trim()
            return SingBoxProxyEndpoint(
                server = trimmedServer,
                tlsServerName = trimmedHost.takeIf {
                    selectedProxy.type == ProxyType.HTTPS &&
                        trimmedServer != trimmedHost &&
                        !trimmedHost.isNumericAddressLiteral()
                },
                domainResolver = null
            )
        }
    }
}

class SingBoxConfigGenerator(
    private val serializer: SingBoxConfigSerializer = SingBoxConfigSerializer(),
    private val tunConfig: TunConfig = TunConfig.mvpDefault(),
    private val dnsMode: DnsMode = DnsMode.proxySafeDoh(DEFAULT_PROXY_OUTBOUND_TAG),
    private val udpPolicy: UdpPolicy = UdpPolicy.mvpDefault()
) {
    fun generate(
        selectedProxy: ProxyEntity,
        proxyEndpoint: SingBoxProxyEndpoint = SingBoxProxyEndpoint.fromProxy(selectedProxy)
    ): GeneratedSingBoxConfig {
        require(selectedProxy.host.isNotBlank()) { "Proxy host must not be blank." }
        require(proxyEndpoint.server.isNotBlank()) { "Proxy endpoint server must not be blank." }
        require(selectedProxy.port in 1..65535) { "Proxy port must be between 1 and 65535." }
        require(tunConfig.isIpv4Only) { "MVP sing-box config supports IPv4-only TUN settings." }
        require(dnsMode.isProxySafe) { "MVP DNS must use a proxy-safe route." }
        require(dnsMode.transport == DnsTransport.DNS_OVER_HTTPS) {
            "MVP sing-box config currently supports DNS over HTTPS."
        }
        require(udpPolicy.blocksUdp443) { "MVP UDP policy must block UDP/443." }

        val config = buildConfig(
            dnsMode = dnsMode,
            includeProxyHostBootstrap = proxyEndpoint.domainResolver != null,
            outbounds = listOf(selectedProxy.toOutbound(proxyEndpoint)),
            finalOutboundTag = DEFAULT_PROXY_OUTBOUND_TAG,
            autoDetectInterface = !proxyEndpoint.server.isLoopbackAddressLiteral()
        )

        return GeneratedSingBoxConfig(
            json = serializer.serialize(config),
            maskedPreview = serializer.serialize(config, maskSecrets = true),
            udpPolicy = udpPolicy
        )
    }

    fun generateDirect(): GeneratedSingBoxConfig {
        require(tunConfig.isIpv4Only) { "MVP sing-box config supports IPv4-only TUN settings." }
        require(udpPolicy.blocksUdp443) { "MVP UDP policy must block UDP/443." }

        val directDnsMode = DnsMode.directDoh()
        require(directDnsMode.isProxySafe) {
            "Direct VPN mode DNS must use an explicit routed detour."
        }

        val config = buildConfig(
            dnsMode = directDnsMode,
            includeProxyHostBootstrap = false,
            outbounds = listOf(SingBoxDirectOutbound()),
            finalOutboundTag = DEFAULT_DIRECT_OUTBOUND_TAG,
            autoDetectInterface = true
        )

        return GeneratedSingBoxConfig(
            json = serializer.serialize(config),
            maskedPreview = serializer.serialize(config, maskSecrets = true),
            udpPolicy = udpPolicy
        )
    }

    private fun buildConfig(
        dnsMode: DnsMode,
        includeProxyHostBootstrap: Boolean,
        outbounds: List<SingBoxOutbound>,
        finalOutboundTag: String,
        autoDetectInterface: Boolean
    ): SingBoxConfig {
        return SingBoxConfig(
            dns = dnsMode.toDnsConfig(
                includeProxyHostBootstrap = includeProxyHostBootstrap
            ),
            inbounds = listOf(tunConfig.toTunInbound()),
            outbounds = outbounds,
            route = SingBoxRouteConfig(
                finalTag = finalOutboundTag,
                autoDetectInterface = autoDetectInterface,
                rules = listOf(
                    SingBoxSniffRouteRule(),
                    SingBoxDnsHijackRouteRule(),
                    tunConfig.toPrivateDnsRejectRule()
                ) + udpPolicy.toRouteRules()
            )
        )
    }

    private fun ProxyEntity.toOutbound(endpoint: SingBoxProxyEndpoint): SingBoxOutbound {
        return when (type) {
            ProxyType.SOCKS5 -> SingBoxSocksOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = endpoint.server,
                serverPort = port,
                domainResolver = endpoint.domainResolver,
                username = username,
                password = password
            )
            ProxyType.HTTP -> SingBoxHttpOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = endpoint.server,
                serverPort = port,
                domainResolver = endpoint.domainResolver,
                username = username,
                password = password
            )
            ProxyType.HTTPS -> SingBoxHttpOutbound(
                tag = DEFAULT_PROXY_OUTBOUND_TAG,
                server = endpoint.server,
                serverPort = port,
                username = username,
                password = password,
                domainResolver = endpoint.domainResolver,
                tls = SingBoxTlsConfig(
                    enabled = true,
                    serverName = endpoint.tlsServerName
                )
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

    private fun DnsMode.toDnsConfig(includeProxyHostBootstrap: Boolean): SingBoxDnsConfig {
        val servers = buildList {
            add(
                SingBoxHttpsDnsServer(
                    server = server,
                    serverPort = serverPort,
                    path = path,
                    detour = detourOutboundTag
                )
            )
            if (includeProxyHostBootstrap) {
                add(SingBoxLocalDnsServer())
            }
        }
        return SingBoxDnsConfig(
            servers = servers
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

private fun String.isNumericAddressLiteral(): Boolean {
    val value = trim()
    return IPV4_LITERAL.matches(value) || ':' in value
}

private fun String.isLoopbackAddressLiteral(): Boolean {
    val value = trim().lowercase()
    return value == "localhost" ||
        value == "::1" ||
        value == "0:0:0:0:0:0:0:1" ||
        value.startsWith("127.")
}

private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
