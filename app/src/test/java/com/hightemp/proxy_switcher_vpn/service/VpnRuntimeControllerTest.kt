package com.hightemp.proxy_switcher_vpn.service

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.proxy.ProxyNetworkResolver
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxySocketTarget
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTestResult
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.vpn.engine.FakeVpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineStatus
import java.net.InetAddress
import java.net.InetSocketAddress
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
            ),
            proxyNetworkResolver = FakeProxyNetworkResolver()
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
            ),
            proxyNetworkResolver = FakeProxyNetworkResolver()
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

    @Test
    fun startUsesResolvedProxyEndpointInGeneratedConfig() = runTest {
        val engine = FakeVpnEngine()
        val resolver = FakeProxyNetworkResolver(throwsOnResolve = true)
        val controller = VpnRuntimeController(
            engine = engine,
            proxyTester = FakeReachabilityTester(
                ProxyTestResult(
                    success = true,
                    message = "Proxy test succeeded.",
                    resolvedProxyHost = "203.0.113.10"
                )
            ),
            proxyNetworkResolver = resolver
        )

        val result = controller.start(proxy(type = ProxyType.HTTPS))

        assertEquals(VpnRuntimeControllerResult.Success, result)
        val generatedConfig = engine.lastStartRequest?.generatedConfig.orEmpty()
        assertTrue(generatedConfig.contains("\"server\":\"203.0.113.10\""))
        assertTrue(generatedConfig.contains("\"server_name\":\"proxy.example\""))
        assertFalse(generatedConfig.contains("\"domain_resolver\""))
        assertFalse(generatedConfig.contains("\"type\":\"local\""))
        assertEquals(0, resolver.resolveCalls)
    }

    @Test
    fun proxyBootstrapResolutionFailureStopsEngineFailClosed() = runTest {
        val engine = FakeVpnEngine()
        val controller = VpnRuntimeController(
            engine = engine,
            proxyTester = FakeReachabilityTester(
                ProxyTestResult(success = true, message = "Proxy test succeeded.")
            ),
            proxyNetworkResolver = FakeProxyNetworkResolver(throwsOnResolve = true)
        )

        val result = controller.start(proxy())

        assertTrue(result is VpnRuntimeControllerResult.Failure)
        assertEquals(VpnEngineStatus.STOPPED, engine.state.value.status)
        assertEquals(
            "Selected upstream proxy failed: Proxy host bootstrap resolution failed.",
            (result as VpnRuntimeControllerResult.Failure).message
        )
        assertEquals(null, engine.lastStartRequest)
    }


    private fun proxy(): ProxyEntity {
        return proxy(type = ProxyType.SOCKS5)
    }

    private fun proxy(type: ProxyType): ProxyEntity {
        return ProxyEntity(
            id = 9L,
            host = "proxy.example",
            port = 1080,
            type = type,
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

    private class FakeProxyNetworkResolver(
        private val resolvedHost: String = "198.51.100.10",
        private val throwsOnResolve: Boolean = false
    ) : ProxyNetworkResolver {
        var resolveCalls = 0
            private set
        var lastPreferNonVpnNetwork = true
            private set

        override fun resolve(
            host: String,
            port: Int,
            preferNonVpnNetwork: Boolean
        ): ProxySocketTarget {
            resolveCalls += 1
            lastPreferNonVpnNetwork = preferNonVpnNetwork
            if (throwsOnResolve) {
                error("Resolution failed.")
            }
            return ProxySocketTarget(
                socketAddress = InetSocketAddress(
                    InetAddress.getByName(resolvedHost),
                    port
                )
            )
        }
    }
}
