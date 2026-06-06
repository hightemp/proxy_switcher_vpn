package com.hightemp.proxy_switcher_vpn.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsModeTest {
    @Test
    fun proxySafeDohModeExposesDiagnosticsFields() {
        val mode = DnsMode.proxySafeDoh(detourOutboundTag = "proxy")

        assertEquals(DnsTransport.DNS_OVER_HTTPS, mode.transport)
        assertEquals(DnsRouteMode.SELECTED_PROXY, mode.routeMode)
        assertEquals("1.1.1.1", mode.server)
        assertEquals(443, mode.serverPort)
        assertEquals("/dns-query", mode.path)
        assertEquals("proxy", mode.detourOutboundTag)
        assertTrue(mode.isProxySafe)
    }

    @Test
    fun directDohModeUsesExplicitDirectRouteMode() {
        val mode = DnsMode.directDoh()

        assertEquals(DnsTransport.DNS_OVER_HTTPS, mode.transport)
        assertEquals(DnsRouteMode.DIRECT_EXPLICIT, mode.routeMode)
        assertEquals(null, mode.detourOutboundTag)
        assertTrue(mode.isProxySafe)
    }
}
