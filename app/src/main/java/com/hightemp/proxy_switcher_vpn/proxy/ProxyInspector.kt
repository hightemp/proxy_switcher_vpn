package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SpeedTestResult {
    data class Success(
        val bitsPerSecond: Long,
        val bytes: Long,
        val millis: Long
    ) : SpeedTestResult

    data class Failure(val message: UserText) : SpeedTestResult
}

/** Looks at a proxy from the outside: exit address and download speed. */
interface ProxyInspector {
    suspend fun exitInfo(proxy: ProxyEntity): ExitInfo?

    suspend fun downloadSpeed(proxy: ProxyEntity, bytes: Long = DEFAULT_SPEED_TEST_BYTES): SpeedTestResult

    companion object {
        const val DEFAULT_SPEED_TEST_BYTES = 10_000_000L
    }
}

/**
 * Makes HTTPS requests through a proxy tunnel. Exit info comes from the
 * Cloudflare trace endpoint with ipinfo.io as a fallback; the speed test
 * downloads from speed.cloudflare.com.
 */
class HttpsProxyInspector(
    private val tunnels: ProxyTunnelOpener,
    private val secureSocket: (Socket, String) -> Socket
) : ProxyInspector {
    @Inject
    constructor(tunnels: ProxyTunnelOpener) : this(tunnels, ::tlsSocket)

    override suspend fun exitInfo(proxy: ProxyEntity): ExitInfo? {
        return fetchText(proxy, CLOUDFLARE_HOST, CLOUDFLARE_TRACE_PATH)
            ?.let(ProxyCheckParsers::parseCloudflareTrace)
            ?: fetchText(proxy, IPINFO_HOST, IPINFO_PATH)?.let(ProxyCheckParsers::parseIpinfo)
    }

    override suspend fun downloadSpeed(proxy: ProxyEntity, bytes: Long): SpeedTestResult =
        withContext(Dispatchers.IO) {
            val tunnel = when (val result = tunnels.openTunnel(proxy, SPEED_HOST, HTTPS_PORT, TIMEOUT_MILLIS)) {
                is ProxyTunnelResult.Failed -> return@withContext SpeedTestResult.Failure(result.result.message)
                is ProxyTunnelResult.Opened -> result
            }
            tunnel.use {
                runCatching {
                    secureSocket(tunnel.socket, SPEED_HOST).use { socket ->
                        socket.soTimeout = TIMEOUT_MILLIS
                        writeGet(socket, SPEED_HOST, "$SPEED_PATH$bytes")
                        val input = BufferedInputStream(socket.getInputStream())
                        val head = ProxyCheckParsers.readHttpHead(input)
                        if (head.statusCode !in 200..299) {
                            return@use SpeedTestResult.Failure(
                                UserText.of(R.string.speed_test_http_status, head.statusCode)
                            )
                        }
                        measureDownload(input, bytes)
                    }
                }.getOrElse { error ->
                    AppLogger.warning(
                        message = "Speed test failed: ${error.javaClass.simpleName}.",
                        type = LogType.PROXY
                    )
                    SpeedTestResult.Failure(
                        if (error is SocketTimeoutException) {
                            UserText.of(R.string.proxy_test_timeout)
                        } else {
                            UserText.of(R.string.speed_test_failed)
                        }
                    )
                }
            }
        }

    /** Counts body bytes until [expected] or the time limit; a partial download still counts. */
    private fun measureDownload(input: InputStream, expected: Long): SpeedTestResult {
        val buffer = ByteArray(32_768)
        val startedAt = System.nanoTime()
        var received = 0L
        while (received < expected) {
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
            if (elapsedMillis > SPEED_TEST_LIMIT_MILLIS) break
            val read = runCatching {
                input.read(buffer, 0, minOf(buffer.size.toLong(), expected - received).toInt())
            }.getOrElse { error ->
                if (error is SocketTimeoutException && received >= MIN_PARTIAL_BYTES) -1 else throw error
            }
            if (read == -1) break
            received += read
        }
        val millis = maxOf(1L, (System.nanoTime() - startedAt) / 1_000_000L)
        if (received < minOf(expected, MIN_PARTIAL_BYTES)) {
            return SpeedTestResult.Failure(UserText.of(R.string.speed_test_failed))
        }
        return SpeedTestResult.Success(
            bitsPerSecond = ProxyCheckParsers.bitsPerSecond(received, millis),
            bytes = received,
            millis = millis
        )
    }

    private suspend fun fetchText(proxy: ProxyEntity, host: String, path: String): String? =
        withContext(Dispatchers.IO) {
            val tunnel = tunnels.openTunnel(proxy, host, HTTPS_PORT, TIMEOUT_MILLIS)
                as? ProxyTunnelResult.Opened ?: return@withContext null
            tunnel.use {
                runCatching {
                    secureSocket(tunnel.socket, host).use { socket ->
                        socket.soTimeout = TIMEOUT_MILLIS
                        writeGet(socket, host, path)
                        val response = ProxyCheckParsers.readHttpResponse(
                            BufferedInputStream(socket.getInputStream()),
                            maxBodyBytes = MAX_TEXT_BODY_BYTES
                        )
                        response.body.takeIf { response.statusCode in 200..299 }
                    }
                }.getOrNull()
            }
        }

    private fun writeGet(socket: Socket, host: String, path: String) {
        val request = "GET $path HTTP/1.1\r\n" +
            "Host: $host\r\n" +
            "User-Agent: ProxySwitcherVPN\r\n" +
            "Accept: */*\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val TIMEOUT_MILLIS = 10_000
        const val SPEED_TEST_LIMIT_MILLIS = 30_000L
        const val MIN_PARTIAL_BYTES = 1_000_000L
        const val MAX_TEXT_BODY_BYTES = 16_384
        const val CLOUDFLARE_HOST = "www.cloudflare.com"
        const val CLOUDFLARE_TRACE_PATH = "/cdn-cgi/trace"
        const val IPINFO_HOST = "ipinfo.io"
        const val IPINFO_PATH = "/json"
        const val SPEED_HOST = "speed.cloudflare.com"
        const val SPEED_PATH = "/__down?bytes="
    }
}

/** TLS over an established tunnel with SNI and hostname verification. */
private fun tlsSocket(tunnel: Socket, host: String): Socket {
    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
    val socket = factory.createSocket(tunnel, host, 443, true) as SSLSocket
    socket.sslParameters = socket.sslParameters.apply {
        endpointIdentificationAlgorithm = "HTTPS"
    }
    socket.startHandshake()
    return socket
}
