package com.hightemp.proxy_switcher_vpn.proxy

import android.util.Base64
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.english
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_TIMEOUT_MILLIS = 4_000
private const val DEFAULT_TEST_TARGET_HOST = "example.com"
private const val DEFAULT_TEST_TARGET_PORT = 443
private const val SOCKS_VERSION = 0x05
private const val SOCKS_AUTH_VERSION = 0x01
private const val SOCKS_METHOD_NO_AUTH = 0x00
private const val SOCKS_METHOD_USERNAME_PASSWORD = 0x02
private const val SOCKS_METHOD_NONE_ACCEPTABLE = 0xff
private const val SOCKS_COMMAND_CONNECT = 0x01
private const val SOCKS_ADDRESS_DOMAIN = 0x03

data class ProxyTestResult(
    val success: Boolean,
    val message: UserText,
    val resolvedProxyHost: String? = null,
    /** Time until the tunnel through the proxy was open; set on success. */
    val latencyMillis: Long? = null
)

/** A connection through the proxy to a target host, ready for application data. */
sealed interface ProxyTunnelResult {
    class Opened(
        val socket: Socket,
        val resolvedProxyHost: String
    ) : ProxyTunnelResult, Closeable {
        override fun close() {
            runCatching { socket.close() }
        }
    }

    data class Failed(val result: ProxyTestResult) : ProxyTunnelResult
}

interface ProxyTunnelOpener {
    suspend fun openTunnel(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult
}

interface ProxyReachabilityTester {
    suspend fun test(
        proxy: ProxyEntity,
        targetHost: String = DEFAULT_TEST_TARGET_HOST,
        targetPort: Int = DEFAULT_TEST_TARGET_PORT,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
    ): ProxyTestResult
}

class ProxyTester @Inject constructor(
    private val activeVpnServiceBridge: ActiveVpnServiceBridge,
    private val proxyNetworkResolver: ProxyNetworkResolver
) : ProxyReachabilityTester, ProxyTunnelOpener {
    override suspend fun test(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        val validationError = validate(proxy, targetHost, targetPort, timeoutMillis)
        if (validationError != null) {
            AppLogger.warning(
                message = "Proxy test skipped for ${proxy.debugLabel()}: ${validationError.english()}",
                type = LogType.PROXY,
                sensitiveValues = proxy.sensitiveValues()
            )
            return@withContext ProxyTestResult(success = false, message = validationError)
        }

        AppLogger.debug(
            message = "Proxy test started for ${proxy.debugLabel()}; " +
                "target=$targetHost:$targetPort; timeout=${timeoutMillis}ms; " +
                "vpnActive=${activeVpnServiceBridge.isActive()}.",
            type = LogType.PROXY,
            sensitiveValues = proxy.sensitiveValues()
        )

        val startedAtNanos = System.nanoTime()
        val result = runCatching {
            when (val tunnel = openTunnelBlocking(proxy, targetHost, targetPort, timeoutMillis)) {
                is ProxyTunnelResult.Opened -> tunnel.use {
                    ProxyTestResult(
                        success = true,
                        message = UserText.of(R.string.proxy_test_succeeded),
                        resolvedProxyHost = tunnel.resolvedProxyHost,
                        latencyMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                    )
                }
                is ProxyTunnelResult.Failed -> tunnel.result
            }
        }.getOrElse { throwable ->
            throwable.toSanitizedFailure()
        }

        if (result.success) {
            AppLogger.debug(
                message = "Proxy test succeeded for ${proxy.debugLabel()}; " +
                    "resolvedProxyHost=${result.resolvedProxyHost ?: "unresolved"}; " +
                    "latency=${result.latencyMillis}ms; " +
                    "message=${result.message.english()}",
                type = LogType.PROXY,
                sensitiveValues = proxy.sensitiveValues()
            )
        } else {
            AppLogger.warning(
                message = "Proxy test failed for ${proxy.debugLabel()}: ${result.message.english()}",
                type = LogType.PROXY,
                sensitiveValues = proxy.sensitiveValues()
            )
        }

        result
    }

    private fun validate(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): UserText? {
        return when {
            proxy.host.isBlank() -> UserText.of(R.string.proxy_test_host_required)
            proxy.port !in 1..65535 -> UserText.of(R.string.proxy_test_port_range)
            targetHost.isBlank() -> UserText.of(R.string.proxy_test_target_required)
            targetPort !in 1..65535 -> UserText.of(R.string.proxy_test_target_port_range)
            timeoutMillis <= 0 -> UserText.of(R.string.proxy_test_timeout_positive)
            else -> null
        }
    }

    override suspend fun openTunnel(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult = withContext(Dispatchers.IO) {
        validate(proxy, targetHost, targetPort, timeoutMillis)?.let { error ->
            return@withContext ProxyTunnelResult.Failed(ProxyTestResult(success = false, message = error))
        }
        runCatching { openTunnelBlocking(proxy, targetHost, targetPort, timeoutMillis) }
            .getOrElse { throwable -> ProxyTunnelResult.Failed(throwable.toSanitizedFailure()) }
    }

    private fun openTunnelBlocking(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult {
        return when (proxy.type) {
            ProxyType.SOCKS5 -> openSocks5Tunnel(proxy, targetHost, targetPort, timeoutMillis)
            ProxyType.HTTP -> openHttpTunnel(proxy, useTls = false, targetHost, targetPort, timeoutMillis)
            ProxyType.HTTPS -> openHttpTunnel(proxy, useTls = true, targetHost, targetPort, timeoutMillis)
        }
    }

    private fun openHttpTunnel(
        proxy: ProxyEntity,
        useTls: Boolean,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult {
        val connection = openProxySocket(proxy, useTls, timeoutMillis)
        try {
            writeConnectRequest(connection.socket, proxy, targetHost, targetPort)
            val result = readConnectResponse(connection.socket)
            if (!result.success) {
                connection.close()
                return ProxyTunnelResult.Failed(result)
            }
            return ProxyTunnelResult.Opened(connection.socket, connection.resolvedProxyHost)
        } catch (throwable: Throwable) {
            connection.close()
            throw throwable
        }
    }

    private fun openProxySocket(
        proxy: ProxyEntity,
        useTls: Boolean,
        timeoutMillis: Int
    ): OpenProxySocket {
        val target = proxyNetworkResolver.resolve(
            host = proxy.host,
            port = proxy.port,
            preferNonVpnNetwork = activeVpnServiceBridge.isActive()
        )
        val rawSocket = Socket()
        try {
            activeVpnServiceBridge.protectSocketIfActive(rawSocket)
            target.bindSocket(rawSocket)
            rawSocket.connect(target.socketAddress, timeoutMillis)
            rawSocket.soTimeout = timeoutMillis
            if (!useTls) {
                return OpenProxySocket(rawSocket, target.serverHost)
            }

            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslSocketFactory.createSocket(
                rawSocket,
                proxy.host,
                proxy.port,
                true
            ) as SSLSocket
            sslSocket.sslParameters = sslSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            sslSocket.soTimeout = timeoutMillis
            sslSocket.startHandshake()
            return OpenProxySocket(sslSocket, target.serverHost)
        } catch (throwable: Throwable) {
            rawSocket.closeQuietly()
            throw throwable
        }
    }

    private fun writeConnectRequest(
        socket: Socket,
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int
    ) {
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = buildString {
            append("CONNECT ")
            append(targetHost)
            append(':')
            append(targetPort)
            append(" HTTP/1.1\r\n")
            append("Host: ")
            append(targetHost)
            append(':')
            append(targetPort)
            append("\r\n")
            proxy.basicAuthHeader()?.let { header ->
                append(header)
                append("\r\n")
            }
            append("Proxy-Connection: close\r\n")
            append("\r\n")
        }
        output.write(request.toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun readConnectResponse(socket: Socket): ProxyTestResult {
        // Unbuffered so no tunnel bytes are consumed past the response head.
        val input = socket.getInputStream()
        val statusLine = input.readAsciiLine()
        while (input.readAsciiLine().isNotEmpty()) {
            // Skip response headers.
        }
        val statusCode = statusLine.split(" ", limit = 3).getOrNull(1)?.toIntOrNull()
        return when {
            statusCode in 200..299 -> {
                ProxyTestResult(success = true, message = UserText.of(R.string.proxy_test_succeeded))
            }
            statusCode == 407 -> {
                ProxyTestResult(
                    success = false,
                    message = UserText.of(R.string.proxy_test_http_auth)
                )
            }
            statusCode != null -> {
                ProxyTestResult(
                    success = false,
                    message = UserText.of(R.string.proxy_test_http_status, statusCode)
                )
            }
            else -> {
                ProxyTestResult(success = false, message = UserText.of(R.string.proxy_test_invalid_response))
            }
        }
    }

    private fun openSocks5Tunnel(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult {
        val socket = Socket()
        try {
            val result = socks5Connect(socket, proxy, targetHost, targetPort, timeoutMillis)
            if (!result.success) {
                socket.closeQuietly()
                return ProxyTunnelResult.Failed(result)
            }
            return ProxyTunnelResult.Opened(socket, result.resolvedProxyHost.orEmpty())
        } catch (throwable: Throwable) {
            socket.closeQuietly()
            throw throwable
        }
    }

    private fun socks5Connect(
        socket: Socket,
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTestResult {
        val target = proxyNetworkResolver.resolve(
            host = proxy.host,
            port = proxy.port,
            preferNonVpnNetwork = activeVpnServiceBridge.isActive()
        )
        run {
            activeVpnServiceBridge.protectSocketIfActive(socket)
            target.bindSocket(socket)
            socket.connect(target.socketAddress, timeoutMillis)
            socket.soTimeout = timeoutMillis

            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val username = proxy.username?.trim()?.ifBlank { null }
            val password = proxy.password.orEmpty()

            output.writeByte(SOCKS_VERSION)
            if (username == null) {
                output.writeByte(1)
                output.writeByte(SOCKS_METHOD_NO_AUTH)
            } else {
                output.writeByte(2)
                output.writeByte(SOCKS_METHOD_NO_AUTH)
                output.writeByte(SOCKS_METHOD_USERNAME_PASSWORD)
            }
            output.flush()

            val version = input.readRequiredByte()
            val selectedMethod = input.readRequiredByte()
            if (version != SOCKS_VERSION) {
                return ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_version))
            }

            when (selectedMethod) {
                SOCKS_METHOD_NO_AUTH -> Unit
                SOCKS_METHOD_USERNAME_PASSWORD -> {
                    if (username == null) {
                        return ProxyTestResult(
                            false,
                            UserText.of(R.string.proxy_test_socks_auth_required)
                        )
                    }
                    val authResult = authenticateSocks5(input, output, username, password)
                    if (authResult != null) return authResult
                }
                SOCKS_METHOD_NONE_ACCEPTABLE -> {
                    return ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_methods_rejected))
                }
                else -> {
                    return ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_method_unsupported))
                }
            }

            val targetHostBytes = targetHost.toByteArray(Charsets.UTF_8)
            if (targetHostBytes.size > 255) {
                return ProxyTestResult(false, UserText.of(R.string.proxy_test_target_too_long))
            }

            output.writeByte(SOCKS_VERSION)
            output.writeByte(SOCKS_COMMAND_CONNECT)
            output.writeByte(0)
            output.writeByte(SOCKS_ADDRESS_DOMAIN)
            output.writeByte(targetHostBytes.size)
            output.write(targetHostBytes)
            output.writeByte((targetPort shr 8) and 0xff)
            output.writeByte(targetPort and 0xff)
            output.flush()

            val responseVersion = input.readRequiredByte()
            val reply = input.readRequiredByte()
            input.readRequiredByte()
            val addressType = input.readRequiredByte()

            if (responseVersion != SOCKS_VERSION) {
                return ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_connect_version))
            }
            if (reply != 0) {
                return ProxyTestResult(false, socks5ReplyMessage(reply))
            }

            input.skipSocks5Address(addressType)
            input.skipFully(2)
            return ProxyTestResult(
                success = true,
                message = UserText.of(R.string.proxy_test_succeeded),
                resolvedProxyHost = target.serverHost
            )
        }
    }

    private data class OpenProxySocket(
        val socket: Socket,
        val resolvedProxyHost: String
    ) : Closeable {
        override fun close() {
            socket.close()
        }
    }

    private fun authenticateSocks5(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        username: String,
        password: String
    ): ProxyTestResult? {
        val usernameBytes = username.toByteArray(Charsets.UTF_8)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        if (usernameBytes.size > 255 || passwordBytes.size > 255) {
            return ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_credentials_long))
        }

        output.writeByte(SOCKS_AUTH_VERSION)
        output.writeByte(usernameBytes.size)
        output.write(usernameBytes)
        output.writeByte(passwordBytes.size)
        output.write(passwordBytes)
        output.flush()

        val authVersion = input.readRequiredByte()
        val authStatus = input.readRequiredByte()
        return when {
            authVersion != SOCKS_AUTH_VERSION -> {
                ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_auth_version))
            }
            authStatus != 0 -> {
                ProxyTestResult(false, UserText.of(R.string.proxy_test_socks_credentials_rejected))
            }
            else -> null
        }
    }

    private fun ProxyEntity.basicAuthHeader(): String? {
        val username = username?.trim()?.ifBlank { null } ?: return null
        val password = password.orEmpty()
        val token = Base64.encodeToString(
            "$username:$password".toByteArray(Charsets.ISO_8859_1),
            Base64.NO_WRAP
        )
        return "Proxy-Authorization: Basic $token"
    }

    private fun Throwable.toSanitizedFailure(): ProxyTestResult {
        val message = when (this) {
            is SocketTimeoutException -> UserText.of(R.string.proxy_test_timeout)
            is UnknownHostException -> UserText.of(R.string.proxy_test_unresolved)
            is ConnectException -> UserText.of(R.string.proxy_test_connect_failed)
            is SSLException -> UserText.of(R.string.proxy_test_tls_failed)
            is EOFException -> UserText.of(R.string.proxy_test_closed)
            else -> UserText.of(R.string.proxy_test_failed)
        }
        return ProxyTestResult(success = false, message = message)
    }

    private fun ProxyEntity.debugLabel(): String {
        return "$type ${label ?: host}:$port"
    }

    private fun ProxyEntity.sensitiveValues(): List<String> {
        return listOfNotNull(username, password)
            .filter { it.isNotBlank() }
    }

    private fun socks5ReplyMessage(reply: Int): UserText {
        return when (reply) {
            0x01 -> UserText.of(R.string.proxy_test_socks_reply_general)
            0x02 -> UserText.of(R.string.proxy_test_socks_reply_disallowed)
            0x03 -> UserText.of(R.string.proxy_test_socks_reply_network)
            0x04 -> UserText.of(R.string.proxy_test_socks_reply_host)
            0x05 -> UserText.of(R.string.proxy_test_socks_reply_refused)
            0x06 -> UserText.of(R.string.proxy_test_socks_reply_ttl)
            0x07 -> UserText.of(R.string.proxy_test_socks_reply_command)
            0x08 -> UserText.of(R.string.proxy_test_socks_reply_address)
            else -> UserText.of(R.string.proxy_test_socks_reply_unknown)
        }
    }

    private fun InputStream.readAsciiLine(): String {
        val buffer = StringBuilder()
        while (true) {
            val value = read()
            if (value == -1) {
                if (buffer.isEmpty()) throw EOFException()
                break
            }
            if (value == '\n'.code) break
            if (value != '\r'.code) buffer.append(value.toChar())
            if (buffer.length > 4_096) throw IOException("Response line is too long.")
        }
        return buffer.toString()
    }

    private fun BufferedInputStream.readRequiredByte(): Int {
        val value = read()
        if (value == -1) throw EOFException()
        return value
    }

    private fun BufferedInputStream.skipSocks5Address(addressType: Int) {
        when (addressType) {
            0x01 -> skipFully(4)
            0x03 -> skipFully(readRequiredByte())
            0x04 -> skipFully(16)
            else -> throw IOException("Unknown SOCKS5 address type.")
        }
    }

    private fun BufferedInputStream.skipFully(byteCount: Int) {
        var remaining = byteCount.toLong()
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) throw EOFException()
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun BufferedOutputStream.writeByte(value: Int) {
        write(value and 0xff)
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }
}
