package com.hightemp.proxy_switcher_vpn.vpn.events

import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DnsEventLoggingAdapterTest {
    private val adapter = DnsEventLoggingAdapter()

    @Before
    fun setUp() {
        AppLogger.clear()
    }

    @After
    fun tearDown() {
        AppLogger.clear()
    }

    @Test
    fun incrementsDnsCounterAndRedactsDomainWhenDetailedLoggingDisabled() {
        val updatedCounters = adapter.handle(
            event = dnsEvent(domain = "private.example"),
            settings = AppSettings(domainDestinationLoggingEnabled = false),
            counters = VpnEngineCounters(dnsQueries = 2)
        )

        assertEquals(3L, updatedCounters.dnsQueries)
        val message = AppLogger.logs.value.single().message
        assertTrue(message.contains("DNS query"))
        assertTrue(message.contains("disabled"))
        assertFalse(message.contains("private.example"))
    }

    @Test
    fun logsDnsDomainWhenDetailedLoggingEnabled() {
        adapter.handle(
            event = dnsEvent(domain = "visible.example", queryType = "A"),
            settings = AppSettings(
                domainDestinationLoggingEnabled = true,
                privacyDisclosureAccepted = true
            ),
            counters = VpnEngineCounters()
        )

        val message = AppLogger.logs.value.single().message
        assertTrue(message.contains("visible.example"))
        assertTrue(message.contains("A"))
    }

    @Test
    fun logsDnsFailureAsWarning() {
        adapter.handle(
            event = dnsEvent(status = DnsEventStatus.FAILURE),
            settings = AppSettings(domainDestinationLoggingEnabled = false),
            counters = VpnEngineCounters()
        )

        val log = AppLogger.logs.value.single()
        assertEquals(VpnEngineLogLevel.WARNING, log.level)
        assertEquals(LogType.DNS, log.type)
        assertTrue(log.message.contains("failed"))
    }

    private fun dnsEvent(
        status: DnsEventStatus = DnsEventStatus.QUERY,
        domain: String = "example.com",
        queryType: String = "A"
    ): VpnEvent.Dns {
        return VpnEvent.Dns(
            timestampMillis = 1000L,
            status = status,
            domain = domain,
            queryType = queryType
        )
    }
}
