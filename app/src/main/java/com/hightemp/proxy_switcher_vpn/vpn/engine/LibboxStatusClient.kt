package com.hightemp.proxy_switcher_vpn.vpn.engine

import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.events.VpnEvent
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStatsStore
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Subscribes to the libbox command server status stream and feeds traffic
 * counters into [VpnStatsStore]. The command server is started by
 * [LibboxVpnEngine] in the same process, so the client connects over the
 * libbox unix socket in the app base directory.
 */
@Singleton
class LibboxStatusClient @Inject constructor(
    private val statsStore: VpnStatsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var connectJob: Job? = null
    private var client: CommandClient? = null

    @Volatile
    private var enabled = false

    fun start() {
        synchronized(lock) {
            stopLocked()
            enabled = true
            connectJob = scope.launch { connectWithRetries() }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        enabled = false
        connectJob?.cancel()
        connectJob = null
        client?.let { activeClient -> runCatching { activeClient.disconnect() } }
        client = null
    }

    private suspend fun connectWithRetries() {
        var attempt = 0
        while (enabled && attempt < MAX_CONNECT_ATTEMPTS) {
            attempt++
            val candidate = runCatching {
                val options = CommandClientOptions().apply {
                    addCommand(Libbox.CommandStatus)
                    statusInterval = STATUS_INTERVAL_NANOS
                }
                CommandClient(StatusHandler(), options).also { it.connect() }
            }.getOrNull()

            if (candidate != null) {
                synchronized(lock) {
                    if (enabled) {
                        client = candidate
                    } else {
                        runCatching { candidate.disconnect() }
                    }
                }
                return
            }

            delay(RETRY_DELAY_MILLIS)
        }
        if (enabled) {
            AppLogger.warning(
                message = "Traffic statistics stream is unavailable: " +
                    "could not connect to the sing-box command server.",
                type = LogType.VPN
            )
        }
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (!enabled) return
            connectJob?.cancel()
            connectJob = scope.launch {
                delay(RETRY_DELAY_MILLIS)
                connectWithRetries()
            }
        }
    }

    private inner class StatusHandler : CommandClientHandler {
        override fun connected() {
            AppLogger.info(
                message = "Traffic statistics stream connected.",
                type = LogType.VPN
            )
        }

        override fun disconnected(message: String?) {
            synchronized(lock) { client = null }
            scheduleReconnect()
        }

        override fun writeStatus(message: StatusMessage?) {
            if (message == null) return
            statsStore.applyEvent(
                VpnEvent.Status(
                    timestampMillis = System.currentTimeMillis(),
                    connectionsIn = message.connectionsIn,
                    connectionsOut = message.connectionsOut,
                    trafficAvailable = message.trafficAvailable,
                    uplinkTotal = message.uplinkTotal,
                    downlinkTotal = message.downlinkTotal
                )
            )
        }

        override fun clearLogs() = Unit

        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit

        override fun setDefaultLogLevel(level: Int) = Unit

        override fun updateClashMode(newMode: String?) = Unit

        override fun writeConnectionEvents(events: ConnectionEvents?) = Unit

        override fun writeGroups(groups: OutboundGroupIterator?) = Unit

        override fun writeLogs(logs: LogIterator?) = Unit

        override fun writeOutbounds(outbounds: OutboundGroupItemIterator?) = Unit
    }

    private companion object {
        const val MAX_CONNECT_ATTEMPTS = 20
        const val RETRY_DELAY_MILLIS = 500L
        const val STATUS_INTERVAL_NANOS = 1_000_000_000L
    }
}
