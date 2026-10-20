package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTesterTest {
    private val resolver = FakeProxyNetworkResolver()
    private val tester: ProxyReachabilityTester = ProxyTester(
        ActiveVpnServiceBridge(),
        resolver
    )

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
        assertEquals(UserText.of(R.string.proxy_test_host_required), result.message)
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
        assertEquals(UserText.of(R.string.proxy_test_port_range), result.message)
    }

    @Test
    fun httpProbeUsesResolvedProxyAddressWithoutLeakingProxyHostToSocketDns() = runTest {
        val server = ConnectProbeServer()
        val port = server.start()
        resolver.target = ProxySocketTarget(
            socketAddress = InetSocketAddress(
                InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)),
                port
            )
        )

        val result = tester.test(
            ProxyEntity(
                host = "proxy.example.invalid",
                port = 8080,
                type = ProxyType.HTTP
            )
        )

        server.close()
        assertTrue(result.message.toString(), result.success)
        assertEquals("127.0.0.1", result.resolvedProxyHost)
        assertEquals("proxy.example.invalid", resolver.lastHost)
        assertEquals(8080, resolver.lastPort)
        assertFalse(resolver.lastPreferNonVpnNetwork)
        assertEquals("CONNECT example.com:443 HTTP/1.1", server.firstRequestLine)
    }

    private class FakeProxyNetworkResolver : ProxyNetworkResolver {
        var target: ProxySocketTarget? = null
        var lastHost: String? = null
        var lastPort: Int? = null
        var lastPreferNonVpnNetwork = false

        override fun resolve(
            host: String,
            port: Int,
            preferNonVpnNetwork: Boolean
        ): ProxySocketTarget {
            lastHost = host
            lastPort = port
            lastPreferNonVpnNetwork = preferNonVpnNetwork
            return target ?: ProxySocketTarget(InetSocketAddress(host, port))
        }
    }

    private class ConnectProbeServer {
        private val serverSocket = ServerSocket(0)
        private var worker: Thread? = null
        @Volatile
        var firstRequestLine: String? = null
            private set

        fun start(): Int {
            worker = Thread {
                serverSocket.use { server ->
                    val socket = server.accept()
                    socket.use {
                        val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        firstRequestLine = input.readLine()
                        it.getOutputStream().write(
                            "HTTP/1.1 200 Connection Established\r\n\r\n"
                                .toByteArray(Charsets.ISO_8859_1)
                        )
                    }
                }
            }.also { it.start() }
            return serverSocket.localPort
        }

        suspend fun close() {
            withContext(Dispatchers.IO) {
                serverSocket.close()
                worker?.join(1_000)
            }
        }
    }
}
