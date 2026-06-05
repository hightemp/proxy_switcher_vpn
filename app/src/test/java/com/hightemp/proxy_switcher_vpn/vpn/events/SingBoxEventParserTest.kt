package com.hightemp.proxy_switcher_vpn.vpn.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingBoxEventParserTest {
    private val parser = SingBoxEventParser(nowMillis = { 1234L })

    @Test
    fun parsesDnsExchangeLogLine() {
        val event = parser.parseLogLine("dns: exchange example.com. IN A")

        val dns = event as VpnEvent.Dns
        assertEquals(1234L, dns.timestampMillis)
        assertEquals(DnsEventStatus.QUERY, dns.status)
        assertEquals("example.com", dns.domain)
        assertEquals("A", dns.queryType)
    }

    @Test
    fun parsesDnsFailureLogLine() {
        val event = parser.parseLogLine("dns: exchange failed for example.org. IN AAAA")

        val dns = event as VpnEvent.Dns
        assertEquals(DnsEventStatus.FAILURE, dns.status)
        assertEquals("example.org", dns.domain)
        assertEquals("AAAA", dns.queryType)
    }

    @Test
    fun ignoresNonDnsLogLine() {
        assertNull(parser.parseLogLine("router: match port=53 => hijack-dns"))
    }

    @Test
    fun parsesUdpRejectLogLine() {
        val event = parser.parseLogLine(
            "router: pre-match network=udp port=443 => reject(drop)"
        )

        val udp = event as VpnEvent.UdpBlocked
        assertEquals(1234L, udp.timestampMillis)
        assertEquals(443, udp.port)
    }
}
