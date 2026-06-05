package com.hightemp.proxy_switcher_vpn.vpn.stats

import com.hightemp.proxy_switcher_vpn.vpn.events.DnsEventStatus
import com.hightemp.proxy_switcher_vpn.vpn.events.VpnEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStatsStoreTest {
    @Test
    fun startSessionResetsCountersAndCalculatesDeterministicUptime() {
        val clock = MutableClock(nowMillis = 1_000L)
        val store = VpnStatsStore(nowMillis = clock::now)
        store.recordBytes(bytesIn = 10L, bytesOut = 20L)

        store.startSession()
        clock.nowMillis = 1_750L

        val stats = store.stats.value
        assertTrue(stats.isRunning)
        assertEquals(0L, stats.bytesIn)
        assertEquals(0L, stats.bytesOut)
        assertEquals(750L, stats.uptimeMillis(clock.now()))
    }

    @Test
    fun stopSessionFreezesUptimeAtStopTime() {
        val clock = MutableClock(nowMillis = 2_000L)
        val store = VpnStatsStore(nowMillis = clock::now)

        store.startSession()
        clock.nowMillis = 2_500L
        store.stopSession()
        clock.nowMillis = 5_000L

        val stats = store.stats.value
        assertFalse(stats.isRunning)
        assertEquals(500L, stats.uptimeMillis(clock.now()))
    }

    @Test
    fun countersUpdateThroughRecordMethods() {
        val store = VpnStatsStore()

        store.startSession()
        store.recordBytes(bytesIn = 128L, bytesOut = 256L)
        store.recordConnectionOpened()
        store.recordConnectionOpened()
        store.recordConnectionClosed()
        store.recordFailure("Upstream proxy failed.")
        store.recordDnsQuery()
        store.recordBlockedUdp()
        store.recordBypassedUdp()

        val stats = store.stats.value
        assertEquals(128L, stats.bytesIn)
        assertEquals(256L, stats.bytesOut)
        assertEquals(384L, stats.totalBytes)
        assertEquals(1, stats.activeConnections)
        assertEquals(2L, stats.totalConnections)
        assertEquals(1L, stats.failedConnections)
        assertEquals(1L, stats.dnsQueries)
        assertEquals(1L, stats.blockedUdp)
        assertEquals(1L, stats.bypassedUdp)
        assertEquals("Upstream proxy failed.", stats.lastError)
    }

    @Test
    fun applyDnsEventUpdatesDnsCounter() {
        val store = VpnStatsStore()

        store.applyEvent(
            VpnEvent.Dns(
                timestampMillis = 100L,
                status = DnsEventStatus.QUERY,
                domain = "example.com",
                queryType = "A"
            )
        )

        assertEquals(1L, store.stats.value.dnsQueries)
    }

    @Test
    fun applyUdpBlockedEventUpdatesBlockedUdpCounter() {
        val store = VpnStatsStore()

        store.applyEvent(
            VpnEvent.UdpBlocked(
                timestampMillis = 100L,
                port = 443
            )
        )

        assertEquals(1L, store.stats.value.blockedUdp)
    }

    @Test
    fun applyStatusEventUpdatesAvailableTrafficAndActiveConnections() {
        val store = VpnStatsStore()

        store.applyEvent(
            VpnEvent.Status(
                timestampMillis = 100L,
                connectionsIn = 2,
                connectionsOut = 1,
                trafficAvailable = true,
                uplinkTotal = 300L,
                downlinkTotal = 700L
            )
        )

        val stats = store.stats.value
        assertEquals(700L, stats.bytesIn)
        assertEquals(300L, stats.bytesOut)
        assertEquals(3, stats.activeConnections)
        assertTrue(stats.trafficStatsAvailable)
        assertTrue(stats.activeConnectionsAvailable)
    }

    @Test
    fun applyStatusEventMarksUnavailableStatsWithoutInventingValues() {
        val store = VpnStatsStore()
        store.recordBytes(bytesIn = 12L, bytesOut = 34L)

        store.applyEvent(
            VpnEvent.Status(
                timestampMillis = 100L,
                trafficAvailable = false
            )
        )

        val stats = store.stats.value
        assertEquals(12L, stats.bytesIn)
        assertEquals(34L, stats.bytesOut)
        assertFalse(stats.trafficStatsAvailable)
        assertFalse(stats.activeConnectionsAvailable)
    }

    @Test
    fun statsConvertToEngineCounters() {
        val store = VpnStatsStore()
        store.recordBytes(bytesIn = 7L, bytesOut = 9L)
        store.recordDnsQuery()
        store.recordBlockedUdp()

        val counters = store.stats.value.toEngineCounters()

        assertEquals(7L, counters.bytesIn)
        assertEquals(9L, counters.bytesOut)
        assertEquals(1L, counters.dnsQueries)
        assertEquals(1L, counters.blockedUdp)
    }

    private data class MutableClock(
        var nowMillis: Long
    ) {
        fun now(): Long = nowMillis
    }
}
