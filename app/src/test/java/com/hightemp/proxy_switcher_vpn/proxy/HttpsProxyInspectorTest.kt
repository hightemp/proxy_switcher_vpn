package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsProxyInspectorTest {
    private val proxy = ProxyEntity(id = 1L, host = "proxy.example", port = 1080, type = ProxyType.SOCKS5)
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private var responder: (host: String, path: String) -> String = { _, _ -> "HTTP/1.1 404 Not Found\r\n\r\n" }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        var host = ""
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Host:")) host = line.removePrefix("Host:").trim()
                        }
                        val path = requestLine.split(" ").getOrNull(1).orEmpty()
                        requests += "$host$path"
                        socket.getOutputStream().write(responder(host, path).toByteArray())
                        socket.getOutputStream().flush()
                    }
                }
            }
        }
    }

    /** Every tunnel goes to the local server; TLS is replaced by the plain socket. */
    private val inspector = HttpsProxyInspector(
        tunnels = object : ProxyTunnelOpener {
            override suspend fun openTunnel(
                proxy: ProxyEntity,
                targetHost: String,
                targetPort: Int,
                timeoutMillis: Int
            ): ProxyTunnelResult {
                return ProxyTunnelResult.Opened(Socket(server.inetAddress, server.localPort), "127.0.0.1")
            }
        },
        secureSocket = { socket, _ -> socket }
    )

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun exitInfoComesFromCloudflareTrace() = runTest {
        responder = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: 25\r\n\r\nip=203.0.113.9\nloc=NL\nx=1" }

        assertEquals(ExitInfo("203.0.113.9", "NL"), inspector.exitInfo(proxy))
        assertEquals(listOf("www.cloudflare.com/cdn-cgi/trace"), requests.toList())
    }

    @Test
    fun exitInfoFallsBackToIpinfo() = runTest {
        responder = { host, _ ->
            if (host == "ipinfo.io") {
                "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"ip\":\"198.51.100.2\",\"country\":\"BR\"}"
            } else {
                "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n"
            }
        }

        assertEquals(ExitInfo("198.51.100.2", "BR"), inspector.exitInfo(proxy))
        assertEquals(listOf("www.cloudflare.com/cdn-cgi/trace", "ipinfo.io/json"), requests.toList())
    }

    @Test
    fun downloadSpeedCountsBodyBytes() = runTest {
        val size = 200_000
        responder = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: $size\r\n\r\n" + "0".repeat(size) }

        val result = inspector.downloadSpeed(proxy, bytes = size.toLong())

        assertTrue(result.toString(), result is SpeedTestResult.Success)
        assertEquals(size.toLong(), (result as SpeedTestResult.Success).bytes)
        assertTrue(result.bitsPerSecond > 0)
        assertEquals(listOf("speed.cloudflare.com/__down?bytes=$size"), requests.toList())
    }

    @Test
    fun downloadSpeedReportsHttpErrors() = runTest {
        responder = { _, _ -> "HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\n\r\n" }

        val result = inspector.downloadSpeed(proxy, bytes = 1_000L)

        assertEquals(SpeedTestResult.Failure(UserText.of(com.hightemp.proxy_switcher_vpn.R.string.speed_test_http_status, 429)), result)
    }
}
