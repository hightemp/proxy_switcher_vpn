package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import com.hightemp.proxy_switcher_vpn.vpn.udp.UdpPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDiagnosticsTest {
    @Test
    fun defaultDiagnosticsExposeUdpPolicyAndCounters() {
        val diagnostics = VpnDiagnostics(
            udpPolicy = UdpPolicy.mvpDefault(),
            counters = VpnEngineCounters(blockedUdp = 2, bypassedUdp = 0)
        )

        assertEquals("blocked", diagnostics.udp443Status)
        assertEquals("blocked", diagnostics.nonDnsUdpStatus)
        assertEquals(2L, diagnostics.counters.blockedUdp)
        assertEquals(0L, diagnostics.counters.bypassedUdp)
    }

    @Test
    fun diagnosticsFromStatsMarksAvailableStats() {
        val diagnostics = VpnDiagnostics.fromStats(
            stats = VpnStats(
                bytesIn = 10L,
                bytesOut = 20L,
                activeConnections = 2,
                trafficStatsAvailable = true,
                activeConnectionsAvailable = true
            )
        )

        assertEquals("available", diagnostics.trafficStatsStatus)
        assertEquals("available", diagnostics.activeConnectionStatsStatus)
        assertEquals(10L, diagnostics.counters.bytesIn)
        assertEquals(2, diagnostics.counters.activeConnections)
    }

    @Test
    fun diagnosticsFromStatsDocumentsUnsupportedExactStats() {
        val diagnostics = VpnDiagnostics.fromStats(stats = VpnStats())

        assertEquals("not_supported", diagnostics.trafficStatsStatus)
        assertEquals("not_supported", diagnostics.activeConnectionStatsStatus)
    }

    @Test
    fun diagnosticsRepositoryEmitsFlowWithMaskedConfigPreview() = runTest {
        val repository = VpnDiagnosticsRepository()
        val proxy = ProxyEntity(
            id = 7L,
            host = "proxy.example",
            port = 8443,
            type = ProxyType.HTTPS,
            username = "diagnostic-user",
            password = "diagnostic-secret",
            label = "Primary"
        )

        val diagnostics = repository.diagnostics(
            permissionStatus = MutableStateFlow(VpnPermissionDiagnosticStatus.GRANTED),
            runtimeState = MutableStateFlow(
                VpnRuntimeSnapshot(
                    status = VpnServiceStatus.RUNNING,
                    isForegroundServiceActive = true
                )
            ),
            selectedProxy = MutableStateFlow(proxy),
            stats = MutableStateFlow(
                VpnStats(
                    bytesIn = 12L,
                    bytesOut = 34L,
                    blockedUdp = 1L
                )
            )
        ).first()

        val preview = diagnostics.maskedConfigPreview
        assertNotNull(preview)
        assertFalse(preview!!.contains("diagnostic-user"))
        assertFalse(preview.contains("diagnostic-secret"))
        assertTrue(preview.contains("***"))
        assertEquals("granted", diagnostics.vpnPermission.value)
        assertEquals("running", diagnostics.foregroundService.value)
        assertEquals("sing-box/libbox", diagnostics.singBoxCore.label)
        assertEquals("libbox_running", diagnostics.singBoxCore.value)
        assertEquals("unsupported_disabled", diagnostics.ipv6.value)
        assertEquals("proxy_safe_doh_private_dns_blocked", diagnostics.dns.value)
        assertEquals("Primary:8443 (HTTPS)", diagnostics.selectedProxy.value)
        assertEquals(12L, diagnostics.counters.bytesIn)
        assertEquals(1L, diagnostics.counters.blockedUdp)
    }

    @Test
    fun diagnosticsRepositoryDoesNotPutCredentialsInSelectedProxySummary() {
        val repository = VpnDiagnosticsRepository()
        val diagnostics = repository.buildDiagnostics(
            permission = VpnPermissionDiagnosticStatus.UNKNOWN,
            runtime = VpnRuntimeSnapshot(),
            proxy = ProxyEntity(
                id = 1L,
                host = "proxy.example",
                port = 1080,
                type = ProxyType.SOCKS5,
                username = "user",
                password = "secret"
            ),
            stats = VpnStats()
        )

        assertFalse(diagnostics.selectedProxy.value.contains("user"))
        assertFalse(diagnostics.selectedProxy.value.contains("secret"))
        assertEquals("proxy.example:1080 (SOCKS5)", diagnostics.selectedProxy.value)
    }
}
