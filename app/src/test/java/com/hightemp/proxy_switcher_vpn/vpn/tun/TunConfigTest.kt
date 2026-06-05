package com.hightemp.proxy_switcher_vpn.vpn.tun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunConfigTest {
    @Test
    fun mvpDefaultCapturesIpv4SplitDefaultRoutes() {
        val config = TunConfig.mvpDefault()

        assertEquals(TunAddress("172.19.0.1", 30), config.ipv4Address)
        assertEquals(
            listOf(
                TunRoute("0.0.0.0", 1),
                TunRoute("128.0.0.0", 1)
            ),
            config.ipv4Routes
        )
        assertEquals(DnsRouteStrategy.PROXY_SAFE, config.dnsRouteStrategy)
        assertEquals(listOf("172.19.0.2"), config.dnsServerAddresses)
    }

    @Test
    fun mvpDefaultKeepsIpv6UnsupportedAndDisabled() {
        val config = TunConfig.mvpDefault()

        assertTrue(config.isIpv4Only)
        assertEquals(Ipv6Mode.UNSUPPORTED_DISABLED, config.ipv6Mode)
        assertTrue(config.ipv6Routes.isEmpty())
    }

    @Test
    fun tunSessionTransitionsArePureStateChanges() {
        val configured = TunSession(config = TunConfig.mvpDefault())

        val established = configured.markEstablished(
            fileDescriptor = 12,
            nowMillis = 1000L
        )
        assertEquals(TunSessionStatus.ESTABLISHED, established.status)
        assertEquals(12, established.fileDescriptor)
        assertEquals(1000L, established.establishedAtMillis)
        assertNull(established.lastError)

        val closed = established.markClosed()
        assertEquals(TunSessionStatus.CLOSED, closed.status)
        assertNull(closed.fileDescriptor)
        assertNull(closed.lastError)
    }

    @Test
    fun tunSessionCanRepresentSetupError() {
        val session = TunSession(config = TunConfig.mvpDefault())
            .markError("TUN establish returned null.")

        assertEquals(TunSessionStatus.ERROR, session.status)
        assertEquals("TUN establish returned null.", session.lastError)
        assertNull(session.fileDescriptor)
    }
}
