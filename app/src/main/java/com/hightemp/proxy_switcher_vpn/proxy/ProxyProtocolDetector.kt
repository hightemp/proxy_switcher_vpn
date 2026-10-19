package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType

/**
 * Detects the protocol of a parsed proxy string by probing real CONNECT
 * requests, in the same order as proxy-vouch Auto mode.
 */
class ProxyProtocolDetector(
    private val tester: ProxyReachabilityTester
) {
    suspend fun detect(proxy: ParsedProxyString): ProxyType? {
        return DETECTION_ORDER.firstOrNull { type ->
            tester.test(
                ProxyEntity(
                    host = proxy.host,
                    port = proxy.port,
                    type = type,
                    username = proxy.username,
                    password = proxy.password
                )
            ).success
        }
    }

    companion object {
        val DETECTION_ORDER = listOf(ProxyType.HTTPS, ProxyType.SOCKS5, ProxyType.HTTP)
    }
}
