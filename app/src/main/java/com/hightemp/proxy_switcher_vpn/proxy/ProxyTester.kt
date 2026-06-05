package com.hightemp.proxy_switcher_vpn.proxy

import android.util.Base64
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
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
    val message: String,
    val resolvedProxyHost: String? = null
)

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
) : ProxyReachabilityTester {
    override suspend fun test(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        val validationError = validate(proxy, targetHost, targetPort, timeoutMillis)
        if (validationError != null) {
            return@withContext ProxyTestResult(success = false, message = validationError)
        }

        runCatching {
            when (proxy.type) {
                ProxyType.SOCKS5 -> testSocks5(proxy, targetHost, targetPort, timeoutMillis)
                ProxyType.HTTP -> testHttpConnect(proxy, targetHost, targetPort, timeoutMillis)
                ProxyType.HTTPS -> testHttpsConnect(proxy, targetHost, targetPort, timeoutMillis)
            }
        }.getOrElse { throwable ->
            throwable.toSanitizedFailure()
        }
    }

    private fun validate(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): String? {
        return when {
            proxy.host.isBlank() -> "Proxy host is required."
            proxy.port !in 1..65535 -> "Proxy port must be between 1 and 65535."
            targetHost.isBlank() -> "Test target host is required."
            targetPort !in 1..65535 -> "Test target port must be between 1 and 65535."
            timeoutMillis <= 0 -> "Proxy test timeout must be positive."
            else -> null
        }
    }

    private fun testHttpConnect(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTestResult = openProxySocket(proxy, useTls = false, timeoutMillis).use { connection ->
        writeConnectRequest(connection.socket, proxy, targetHost, targetPort)
        readConnectResponse(connection.socket).withResolvedProxyHost(connection.resolvedProxyHost)
    }

    private fun testHttpsConnect(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTestResult = openProxySocket(proxy, useTls = true, timeoutMillis).use { connection ->
        writeConnectRequest(connection.socket, proxy, targetHost, targetPort)
        readConnectResponse(connection.socket).withResolvedProxyHost(connection.resolvedProxyHost)
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
        val input = BufferedInputStream(socket.getInputStream())
        val statusLine = input.readAsciiLine()
        val statusCode = statusLine.split(" ", limit = 3).getOrNull(1)?.toIntOrNull()
        return when {
            statusCode in 200..299 -> {
                ProxyTestResult(success = true, message = "Proxy test succeeded.")
            }
            statusCode == 407 -> {
                ProxyTestResult(
                    success = false,
                    message = "Proxy requires authentication or rejected credentials."
                )
            }
            statusCode != null -> {
                ProxyTestResult(
                    success = false,
                    message = "Proxy CONNECT failed with HTTP $statusCode."
                )
            }
            else -> {
                ProxyTestResult(success = false, message = "Proxy returned an invalid response.")
            }
        }
    }

    private fun testSocks5(
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
        Socket().use { socket ->
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
                return ProxyTestResult(false, "SOCKS5 server returned an invalid version.")
            }

            when (selectedMethod) {
                SOCKS_METHOD_NO_AUTH -> Unit
                SOCKS_METHOD_USERNAME_PASSWORD -> {
                    if (username == null) {
                        return ProxyTestResult(
                            false,
                            "SOCKS5 proxy requires username/password authentication."
                        )
                    }
                    val authResult = authenticateSocks5(input, output, username, password)
                    if (authResult != null) return authResult
                }
                SOCKS_METHOD_NONE_ACCEPTABLE -> {
                    return ProxyTestResult(false, "SOCKS5 proxy rejected available auth methods.")
                }
                else -> {
                    return ProxyTestResult(false, "SOCKS5 proxy selected unsupported auth method.")
                }
            }

            val targetHostBytes = targetHost.toByteArray(Charsets.UTF_8)
            if (targetHostBytes.size > 255) {
                return ProxyTestResult(false, "Test target host is too long.")
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
                return ProxyTestResult(false, "SOCKS5 connect response had an invalid version.")
            }
            if (reply != 0) {
                return ProxyTestResult(false, socks5ReplyMessage(reply))
            }

            input.skipSocks5Address(addressType)
            input.skipFully(2)
            return ProxyTestResult(
                success = true,
                message = "Proxy test succeeded.",
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
            return ProxyTestResult(false, "SOCKS5 credentials are too long.")
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
                ProxyTestResult(false, "SOCKS5 auth response had an invalid version.")
            }
            authStatus != 0 -> {
                ProxyTestResult(false, "SOCKS5 proxy rejected credentials.")
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
            is SocketTimeoutException -> "Proxy test timed out."
            is UnknownHostException -> "Proxy host could not be resolved."
            is ConnectException -> "Could not connect to proxy."
            is SSLException -> "TLS handshake with HTTPS proxy failed."
            is EOFException -> "Proxy closed the connection during the test."
            else -> "Proxy test failed."
        }
        return ProxyTestResult(success = false, message = message)
    }

    private fun ProxyTestResult.withResolvedProxyHost(resolvedProxyHost: String): ProxyTestResult {
        return if (success) copy(resolvedProxyHost = resolvedProxyHost) else this
    }

    private fun socks5ReplyMessage(reply: Int): String {
        return when (reply) {
            0x01 -> "SOCKS5 proxy reported a general failure."
            0x02 -> "SOCKS5 proxy disallows the connection."
            0x03 -> "SOCKS5 proxy reported network unreachable."
            0x04 -> "SOCKS5 proxy reported host unreachable."
            0x05 -> "SOCKS5 proxy reported connection refused."
            0x06 -> "SOCKS5 proxy reported TTL expired."
            0x07 -> "SOCKS5 proxy does not support CONNECT."
            0x08 -> "SOCKS5 proxy does not support the target address type."
            else -> "SOCKS5 proxy returned an unknown failure."
        }
    }

    private fun BufferedInputStream.readAsciiLine(): String {
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
