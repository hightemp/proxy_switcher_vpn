package com.hightemp.proxy_switcher_vpn.vpn.udp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpPolicyTest {
    @Test
    fun mvpDefaultBlocksUdp443AndNonDnsUdp() {
        val policy = UdpPolicy.mvpDefault()

        assertEquals(Udp443Policy.BLOCK, policy.udp443Policy)
        assertEquals(NonDnsUdpPolicy.BLOCK, policy.nonDnsUdpPolicy)
        assertTrue(policy.blocksUdp443)
        assertTrue(UdpPolicy.UDP_443_PORT in policy.blockedPorts)
        assertFalse(policy.allowsDirectNonDnsUdp)
        assertEquals("UDP/443 blocked; non-DNS UDP blocked.", policy.diagnosticSummary)
    }
}
