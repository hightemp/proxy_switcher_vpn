package com.hightemp.proxy_switcher_vpn.service

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTestResult
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.vpn.engine.FakeVpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnRuntimeControllerTest {
    @Before
    fun setUp() {
        AppLogger.clear()
    }

    @After
    fun tearDown() {
        AppLogger.clear()
    }

    @Test
    fun proxyProbeFailureStopsEngineFailClosedAndLogsFailure() = runTest {
        val engine = FakeVpnEngine()
        val controller = VpnRuntimeController(
            engine = engine,
            proxyTester = FakeReachabilityTester(
                ProxyTestResult(
                    success = false,
                    message = "Could not connect to proxy."
                )
            )
        )

        val result = controller.start(proxy())

        assertTrue(result is VpnRuntimeControllerResult.Failure)
        assertEquals(VpnEngineStatus.STOPPED, engine.state.value.status)
        assertEquals(
            "Selected upstream proxy failed: Could not connect to proxy.",
            (result as VpnRuntimeControllerResult.Failure).message
        )
        val logText = AppLogger.logs.value.joinToString(separator = "\n") { it.message }
        assertTrue(logText.contains("VPN stopped fail-closed"))
        assertTrue(logText.contains("Could not connect to proxy."))
        assertFalse(logText.contains("user-1"))
        assertFalse(logText.contains("secret-password"))
    }

    @Test
    fun engineStartFailureStopsEngineFailClosedAndLogsFailure() = runTest {
        val engine = FakeVpnEngine()
        engine.failNextStart("Upstream proxy refused connection.")
        val controller = VpnRuntimeController(
            engine = engine,
            proxyTester = FakeReachabilityTester(
                ProxyTestResult(success = true, message = "Proxy test succeeded.")
            )
        )

        val result = controller.start(proxy())

        assertTrue(result is VpnRuntimeControllerResult.Failure)
        assertEquals(VpnEngineStatus.STOPPED, engine.state.value.status)
        assertEquals(
            "Upstream proxy refused connection.",
            (result as VpnRuntimeControllerResult.Failure).message
        )
        val logText = AppLogger.logs.value.joinToString(separator = "\n") { it.message }
        assertTrue(logText.contains("VPN stopped fail-closed"))
        assertTrue(logText.contains("Upstream proxy refused connection."))
        assertFalse(logText.contains("user-1"))
        assertFalse(logText.contains("secret-password"))
    }

    private fun proxy(): ProxyEntity {
        return ProxyEntity(
            id = 9L,
            host = "proxy.example",
            port = 1080,
            type = ProxyType.SOCKS5,
            username = "user-1",
            password = "secret-password",
            label = "Primary"
        )
    }

    private class FakeReachabilityTester(
        private val result: ProxyTestResult
    ) : ProxyReachabilityTester {
        override suspend fun test(
            proxy: ProxyEntity,
            targetHost: String,
            targetPort: Int,
            timeoutMillis: Int
        ): ProxyTestResult = result
    }
}
