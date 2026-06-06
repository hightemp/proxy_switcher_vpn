package com.hightemp.proxy_switcher_vpn.vpn.engine

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVpnEngineTest {
    @Test
    fun startMovesEngineToRunningWithoutLoggingSecrets() = runTest {
        val engine = FakeVpnEngine()

        val result = engine.start(startRequest())

        assertEquals(VpnEngineCommandResult.Success, result)
        assertEquals(VpnEngineStatus.RUNNING, engine.state.value.status)
        assertEquals(7L, engine.state.value.selectedProxy?.id)
        assertEquals(ProxyType.SOCKS5, engine.state.value.selectedProxy?.type)
        assertNull(engine.state.value.lastError)

        val logText = engine.logs.value.joinToString(separator = "\n") { it.message }
        assertFalse(logText.contains("user-1"))
        assertFalse(logText.contains("secret-password"))
    }

    @Test
    fun stopMovesEngineToStoppedAndClearsCounters() = runTest {
        val engine = FakeVpnEngine()
        engine.start(startRequest())
        engine.updateCounters(VpnEngineCounters(bytesIn = 128, bytesOut = 256))

        val result = engine.stop()

        assertEquals(VpnEngineCommandResult.Success, result)
        assertEquals(VpnEngineStatus.STOPPED, engine.state.value.status)
        assertEquals(VpnEngineCounters(), engine.counters.value)
    }

    @Test
    fun configuredStartFailureMovesEngineToError() = runTest {
        val engine = FakeVpnEngine()
        engine.failNextStart("Upstream proxy refused connection.")

        val result = engine.start(startRequest())

        assertTrue(result is VpnEngineCommandResult.Failure)
        assertEquals(VpnEngineStatus.ERROR, engine.state.value.status)
        assertEquals("Upstream proxy refused connection.", engine.state.value.lastError)
        assertEquals(1L, engine.counters.value.failedConnections)
    }

    @Test
    fun directStartMovesEngineToRunningWithoutSelectedProxy() = runTest {
        val engine = FakeVpnEngine()

        val result = engine.start(
            VpnEngineStartRequest(
                routeSelection = VpnRouteSelection.Direct,
                generatedConfig = """{"type":"direct"}"""
            )
        )

        assertEquals(VpnEngineCommandResult.Success, result)
        assertEquals(VpnEngineStatus.RUNNING, engine.state.value.status)
        assertNull(engine.state.value.selectedProxy)
    }

    @Test
    fun runtimeErrorMovesEngineToError() = runTest {
        val engine = FakeVpnEngine()
        engine.start(startRequest())

        engine.simulateRuntimeError("sing-box exited unexpectedly.")

        assertEquals(VpnEngineStatus.ERROR, engine.state.value.status)
        assertEquals("sing-box exited unexpectedly.", engine.state.value.lastError)
        assertEquals(1L, engine.counters.value.failedConnections)
    }

    private fun startRequest(): VpnEngineStartRequest {
        return VpnEngineStartRequest(
            routeSelection = VpnRouteSelection.Proxy(
                ProxyEntity(
                    id = 7L,
                    host = "proxy.example",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    username = "user-1",
                    password = "secret-password",
                    label = "Primary"
                )
            ),
            generatedConfig = """{"password":"secret-password"}"""
        )
    }
}
