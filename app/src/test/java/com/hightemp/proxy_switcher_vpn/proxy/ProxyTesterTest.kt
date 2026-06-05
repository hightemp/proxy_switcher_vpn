package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProxyTesterTest {
    private val tester: ProxyReachabilityTester = ProxyTester(ActiveVpnServiceBridge())

    @Test
    fun invalidProxyHostFailsBeforeNetworkProbe() = runTest {
        val result = tester.test(
            ProxyEntity(
                host = " ",
                port = 1080,
                type = ProxyType.SOCKS5
            )
        )

        assertFalse(result.success)
        assertEquals("Proxy host is required.", result.message)
    }

    @Test
    fun invalidProxyPortFailsBeforeNetworkProbeAndDoesNotExposeCredentials() = runTest {
        val result = tester.test(
            ProxyEntity(
                host = "proxy.example",
                port = 0,
                type = ProxyType.HTTP,
                username = "user",
                password = "secret-password"
            )
        )

        assertFalse(result.success)
        assertEquals("Proxy port must be between 1 and 65535.", result.message)
        assertFalse(result.message.contains("user"))
        assertFalse(result.message.contains("secret-password"))
    }
}
