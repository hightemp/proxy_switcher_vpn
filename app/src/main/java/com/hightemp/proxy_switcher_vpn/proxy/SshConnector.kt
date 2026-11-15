package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.concurrent.thread

sealed interface SshHostKeyResult {
    data class Success(val key: SshHostKey) : SshHostKeyResult
    data class Failure(val message: UserText) : SshHostKeyResult
}

/** Reads the key an SSH server presents so the user can confirm it before first use. */
interface SshHostKeyFetcher {
    suspend fun fetchSshHostKey(host: String, port: Int): SshHostKeyResult
}

/**
 * SSH client used for proxy checks. sing-box carries the VPN traffic; this
 * class only opens short-lived sessions through the same protected,
 * non-VPN sockets as the SOCKS5/HTTP checks.
 */
class SshConnector(
    private val activeVpnServiceBridge: ActiveVpnServiceBridge,
    private val proxyNetworkResolver: ProxyNetworkResolver
) {
    /**
     * Opens a `direct-tcpip` channel to [targetHost]:[targetPort]. The
     * returned socket is the local end of a loopback bridge, so callers can
     * layer TLS on it like on any proxy tunnel; closing it ends the session.
     */
    fun openTunnel(
        proxy: ProxyEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int
    ): ProxyTunnelResult {
        val expected = proxy.hostKey?.let(SshKeys::parseHostKey)
            ?: return failed(R.string.ssh_error_host_key_unconfirmed, permanent = true)
        val repository = CapturingHostKeyRepository(expected)
        val connection = ConnectionFactory(timeoutMillis)
        val session = try {
            connect(proxy, repository, connection, timeoutMillis)
        } catch (error: JSchException) {
            return ProxyTunnelResult.Failed(failure(error, repository))
        }
        try {
            val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            val input = channel.inputStream
            val output = channel.outputStream
            try {
                channel.connect(timeoutMillis)
            } catch (_: JSchException) {
                session.disconnect()
                return failed(R.string.ssh_error_channel)
            }
            val socket = bridge(input, output, timeoutMillis) {
                channel.disconnect()
                session.disconnect()
            }
            return ProxyTunnelResult.Opened(socket, connection.resolvedHost.orEmpty())
        } catch (error: Throwable) {
            session.disconnect()
            throw error
        }
    }

    /**
     * Cheap liveness probe for the upstream monitor: the server must answer
     * with an SSH banner. The full handshake with the host key check runs on
     * every start and reconnect.
     */
    fun probeBanner(proxy: ProxyEntity, timeoutMillis: Int): ProxyTestResult {
        val connection = ConnectionFactory(timeoutMillis)
        return try {
            connection.createSocket(proxy.host, proxy.port).use { socket ->
                val line = socket.getInputStream().readBannerLine()
                if (line.startsWith("SSH-")) {
                    ProxyTestResult(
                        success = true,
                        message = UserText.of(R.string.proxy_test_succeeded),
                        resolvedProxyHost = connection.resolvedHost
                    )
                } else {
                    ProxyTestResult(false, UserText.of(R.string.ssh_error_not_ssh))
                }
            }
        } catch (error: Exception) {
            ProxyTestResult(false, networkMessage(error) ?: UserText.of(R.string.ssh_error_failed))
        }
    }

    /** Connects without credentials; the key is captured before authentication. */
    fun fetchHostKey(host: String, port: Int, timeoutMillis: Int): SshHostKeyResult {
        val repository = CapturingHostKeyRepository(expected = null)
        val session = JSch().apply { hostKeyRepository = repository }
            .getSession(HOST_KEY_PROBE_USER, host.trim(), port)
        session.setSocketFactory(ConnectionFactory(timeoutMillis))
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "none")
        return try {
            session.connect(timeoutMillis)
            repository.captured?.let { SshHostKeyResult.Success(it) }
                ?: SshHostKeyResult.Failure(UserText.of(R.string.ssh_error_failed))
        } catch (error: JSchException) {
            repository.captured?.let { SshHostKeyResult.Success(it) }
                ?: SshHostKeyResult.Failure(networkMessage(error) ?: UserText.of(R.string.ssh_error_failed))
        } finally {
            session.disconnect()
        }
    }

    private fun connect(
        proxy: ProxyEntity,
        repository: CapturingHostKeyRepository,
        connection: ConnectionFactory,
        timeoutMillis: Int
    ): Session {
        val jsch = JSch().apply { hostKeyRepository = repository }
        val privateKey = SshKeys.normalizePrivateKey(proxy.privateKey)
        if (privateKey != null) {
            try {
                jsch.addIdentity(
                    "proxy-${proxy.id}",
                    privateKey.toByteArray(Charsets.UTF_8),
                    null,
                    proxy.privateKeyPassphrase?.toByteArray(Charsets.UTF_8)
                )
            } catch (_: JSchException) {
                throw KeyRejected()
            }
        }
        val session = jsch.getSession(proxy.username?.trim()?.ifBlank { null } ?: DEFAULT_USER, proxy.host.trim(), proxy.port)
        session.setSocketFactory(connection)
        session.timeout = timeoutMillis
        proxy.password?.takeIf { it.isNotEmpty() }?.let(session::setPassword)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey,password")
        repository.expected?.let { session.setConfig("server_host_key", it.algorithms.joinToString(",")) }
        session.connect(timeoutMillis)
        return session
    }

    private fun failure(error: JSchException, repository: CapturingHostKeyRepository): ProxyTestResult {
        val permanentMessage = when {
            error is KeyRejected -> R.string.ssh_error_key
            repository.mismatch -> R.string.ssh_error_host_key_changed
            error.message.orEmpty().startsWith("Auth ") -> R.string.ssh_error_auth
            else -> null
        }
        if (permanentMessage != null) {
            return ProxyTestResult(success = false, message = UserText.of(permanentMessage), permanent = true)
        }
        return ProxyTestResult(success = false, message = networkMessage(error) ?: UserText.of(R.string.ssh_error_failed))
    }

    private fun failed(message: Int, permanent: Boolean = false) =
        ProxyTunnelResult.Failed(ProxyTestResult(false, UserText.of(message), permanent = permanent))

    /** Opens the TCP connection to the SSH server on a protected, non-VPN network. */
    private inner class ConnectionFactory(private val timeoutMillis: Int) : SocketFactory {
        var resolvedHost: String? = null
            private set

        override fun createSocket(host: String, port: Int): Socket {
            val target = proxyNetworkResolver.resolve(
                host = host,
                port = port,
                preferNonVpnNetwork = activeVpnServiceBridge.isActive()
            )
            val socket = Socket()
            try {
                activeVpnServiceBridge.protectSocketIfActive(socket)
                target.bindSocket(socket)
                socket.connect(target.socketAddress, timeoutMillis)
                socket.soTimeout = timeoutMillis
            } catch (error: Throwable) {
                runCatching { socket.close() }
                throw error
            }
            resolvedHost = target.serverHost
            return socket
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }

    /** Remembers the presented key and accepts only [expected] when it is set. */
    private class CapturingHostKeyRepository(val expected: SshHostKey?) : HostKeyRepository {
        var captured: SshHostKey? = null
            private set
        var mismatch: Boolean = false
            private set

        override fun check(host: String?, key: ByteArray?): Int {
            val presented = key?.let(SshKeys::hostKey) ?: return HostKeyRepository.NOT_INCLUDED
            captured = presented
            return when {
                expected == null -> HostKeyRepository.NOT_INCLUDED
                presented == expected -> HostKeyRepository.OK
                else -> {
                    mismatch = true
                    HostKeyRepository.CHANGED
                }
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "proxy-switcher-vpn"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    private class KeyRejected : JSchException("Private key rejected.")

    companion object {
        private const val DEFAULT_USER = "root"
        private const val HOST_KEY_PROBE_USER = "host-key-probe"
        private const val MAX_BANNER_LENGTH = 255

        init {
            // Android's JCA has no Ed25519/X25519; use JSch's BouncyCastle implementations.
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
        }

        private fun networkMessage(error: Throwable): UserText? {
            return generateSequence(error) { it.cause }.firstNotNullOfOrNull { cause ->
                when (cause) {
                    is SocketTimeoutException -> UserText.of(R.string.proxy_test_timeout)
                    is UnknownHostException -> UserText.of(R.string.proxy_test_unresolved)
                    is ConnectException -> UserText.of(R.string.proxy_test_connect_failed)
                    else -> null
                }
            } ?: UserText.of(R.string.proxy_test_timeout).takeIf { error.message.orEmpty().startsWith("timeout") }
        }

        private fun InputStream.readBannerLine(): String {
            val line = StringBuilder()
            while (line.length < MAX_BANNER_LENGTH) {
                val value = read()
                if (value == -1 || value == '\n'.code) break
                if (value != '\r'.code) line.append(value.toChar())
            }
            return line.toString()
        }

        /**
         * Copies between the SSH channel and a loopback socket pair and returns
         * the caller's end. [onClose] runs once when either side ends.
         */
        private fun bridge(
            channelInput: InputStream,
            channelOutput: OutputStream,
            timeoutMillis: Int,
            onClose: () -> Unit
        ): Socket {
            val loopback = InetAddress.getByName("127.0.0.1")
            val (client, peer) = ServerSocket(0, 1, loopback).use { server ->
                server.soTimeout = timeoutMillis
                val client = Socket()
                client.connect(server.localSocketAddress, timeoutMillis)
                client to server.accept()
            }
            val closer = OnceCloseable {
                runCatching { peer.close() }
                runCatching(onClose)
            }
            thread(isDaemon = true, name = "ssh-bridge-out") {
                runCatching { peer.getInputStream().copyFlushing(channelOutput) }
                closer.close()
            }
            thread(isDaemon = true, name = "ssh-bridge-in") {
                runCatching { channelInput.copyFlushing(peer.getOutputStream()) }
                closer.close()
            }
            return client
        }

        private fun InputStream.copyFlushing(output: OutputStream) {
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = read(buffer)
                if (count < 0) return
                output.write(buffer, 0, count)
                output.flush()
            }
        }
    }

    private class OnceCloseable(private val action: () -> Unit) : Closeable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            action()
        }
    }
}
