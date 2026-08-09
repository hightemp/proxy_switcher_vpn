package com.hightemp.proxy_switcher_vpn.vpn.engine

import android.content.Context
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import com.hightemp.proxy_switcher_vpn.vpn.platform.AndroidLibboxPlatformInterface
import com.hightemp.proxy_switcher_vpn.vpn.platform.DefaultNetworkMonitor
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.routing.proxyOrNull
import com.hightemp.proxy_switcher_vpn.vpn.routing.sensitiveValues
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStatsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.OverrideOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LibboxVpnEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformInterface: AndroidLibboxPlatformInterface,
    private val activeVpnServiceBridge: ActiveVpnServiceBridge,
    private val defaultNetworkMonitor: DefaultNetworkMonitor,
    private val statsStore: VpnStatsStore,
    private val statusClient: LibboxStatusClient
) : VpnEngine {
    private val lifecycleLock = Mutex()

    private val _state = MutableStateFlow(VpnEngineState())
    override val state: StateFlow<VpnEngineState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<VpnEngineLog>>(emptyList())
    override val logs: StateFlow<List<VpnEngineLog>> = _logs.asStateFlow()

    private val _counters = MutableStateFlow(VpnEngineCounters())
    override val counters: StateFlow<VpnEngineCounters> = _counters.asStateFlow()

    private var commandServer: CommandServer? = null

    override suspend fun start(
        request: VpnEngineStartRequest
    ): VpnEngineCommandResult = withContext(Dispatchers.IO) {
        lifecycleLock.withLock {
            closeCommandServerLocked()

            val selectedProxy = request.routeSelection.proxyOrNull()?.let(SelectedProxySummary::from)
            _state.value = VpnEngineState(
                status = VpnEngineStatus.STARTING,
                selectedProxy = selectedProxy
            )
            appendLog(
                VpnEngineLogLevel.INFO,
                "Starting sing-box for ${request.routeSelection.logLabel()}."
            )
            AppLogger.info(
                message = "Starting sing-box VPN engine.",
                type = LogType.VPN
            )

            val handler = LibboxCommandServerHandler(
                onServiceStop = ::handleLibboxServiceStop,
                onServiceReload = {
                    appendLog(VpnEngineLogLevel.INFO, "sing-box requested service reload.")
                },
                onDebugMessage = {
                    appendLog(VpnEngineLogLevel.DEBUG, "sing-box debug message received.")
                }
            )

            val startResult = runCatching {
                LibboxSetup.ensureInitialized(context)
                val server = CommandServer(handler, platformInterface)
                commandServer = server
                server.start()
                server.startOrReloadService(request.generatedConfig, OverrideOptions())
            }

            startResult.fold(
                onSuccess = {
                    _state.value = VpnEngineState(
                        status = VpnEngineStatus.RUNNING,
                        selectedProxy = selectedProxy
                    )
                    statsStore.startSession()
                    statusClient.start()
                    appendLog(VpnEngineLogLevel.INFO, "sing-box VPN engine running.")
                    AppLogger.info(
                        message = "sing-box VPN engine running.",
                        type = LogType.VPN
                    )
                    VpnEngineCommandResult.Success
                },
                onFailure = { throwable ->
                    val safeMessage = throwable.toSafeMessage(request.routeSelection)
                    closeCommandServerLocked()
                    _counters.update {
                        it.copy(failedConnections = it.failedConnections + 1)
                    }
                    _state.value = VpnEngineState(
                        status = VpnEngineStatus.ERROR,
                        selectedProxy = selectedProxy,
                        lastError = "sing-box failed to start: $safeMessage"
                    )
                    appendLog(
                        VpnEngineLogLevel.ERROR,
                        "sing-box failed to start: $safeMessage"
                    )
                    AppLogger.error(
                        message = "sing-box failed to start: $safeMessage",
                        type = LogType.VPN,
                        sensitiveValues = request.routeSelection.sensitiveValues()
                    )
                    VpnEngineCommandResult.Failure(
                        message = "sing-box failed to start: $safeMessage",
                        cause = throwable
                    )
                }
            )
        }
    }

    override suspend fun stop(): VpnEngineCommandResult = withContext(Dispatchers.IO) {
        lifecycleLock.withLock {
            val selectedProxy = _state.value.selectedProxy
            if (_state.value.status == VpnEngineStatus.STOPPED && commandServer == null) {
                activeVpnServiceBridge.closeTun()
                defaultNetworkMonitor.stop()
                return@withLock VpnEngineCommandResult.Success
            }

            _state.value = VpnEngineState(
                status = VpnEngineStatus.STOPPING,
                selectedProxy = selectedProxy
            )
            appendLog(VpnEngineLogLevel.INFO, "Stopping sing-box VPN engine.")

            val closeFailure = closeCommandServerLocked()
            return@withLock if (closeFailure == null) {
                _state.value = VpnEngineState(status = VpnEngineStatus.STOPPED)
                _counters.value = VpnEngineCounters()
                appendLog(VpnEngineLogLevel.INFO, "sing-box VPN engine stopped.")
                AppLogger.info(
                    message = "sing-box VPN engine stopped.",
                    type = LogType.VPN
                )
                VpnEngineCommandResult.Success
            } else {
                val message = closeFailure.message ?: "Unknown libbox close failure."
                _state.value = VpnEngineState(
                    status = VpnEngineStatus.ERROR,
                    selectedProxy = selectedProxy,
                    lastError = "sing-box failed to stop: $message"
                )
                appendLog(VpnEngineLogLevel.ERROR, "sing-box failed to stop: $message")
                AppLogger.error(
                    message = "sing-box failed to stop: $message",
                    type = LogType.VPN
                )
                VpnEngineCommandResult.Failure(
                    message = "sing-box failed to stop: $message",
                    cause = closeFailure
                )
            }
        }
    }

    private fun handleLibboxServiceStop() {
        activeVpnServiceBridge.closeTun()
        defaultNetworkMonitor.stop()
        val selectedProxy = _state.value.selectedProxy
        val wasRunning = _state.value.status == VpnEngineStatus.RUNNING
        if (wasRunning) {
            val message = "sing-box service stopped unexpectedly."
            _counters.update {
                it.copy(failedConnections = it.failedConnections + 1)
            }
            _state.value = VpnEngineState(
                status = VpnEngineStatus.ERROR,
                selectedProxy = selectedProxy,
                lastError = message
            )
            appendLog(VpnEngineLogLevel.ERROR, message)
            AppLogger.error(message = message, type = LogType.VPN)
            activeVpnServiceBridge.failClosedFromEngine(message)
        } else {
            _state.value = VpnEngineState(status = VpnEngineStatus.STOPPED)
            appendLog(VpnEngineLogLevel.INFO, "sing-box service stopped.")
        }
    }

    private fun closeCommandServerLocked(): Throwable? {
        statusClient.stop()
        statsStore.stopSession()
        val server = commandServer
        commandServer = null
        var closeFailure: Throwable? = null

        if (server != null) {
            runCatching {
                server.closeService()
            }.onFailure { throwable ->
                closeFailure = throwable
                runCatching {
                    server.setError("android: close service: ${throwable.message}")
                }
            }
            runCatching {
                server.close()
            }.onFailure { throwable ->
                if (closeFailure == null) {
                    closeFailure = throwable
                }
            }
        }

        activeVpnServiceBridge.closeTun()
        defaultNetworkMonitor.stop()
        return closeFailure
    }

    private fun appendLog(level: VpnEngineLogLevel, message: String) {
        val entry = VpnEngineLog(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = message
        )
        _logs.update { current ->
            (current + entry).takeLast(MAX_LOG_ENTRIES)
        }
    }

    private fun Throwable.toSafeMessage(routeSelection: VpnRouteSelection): String {
        val rawMessage = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
        return routeSelection.sensitiveValues().fold(rawMessage) { masked, secret ->
            masked.replace(secret, MASKED_SECRET)
        }
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
        const val MASKED_SECRET = "***"
    }
}

private fun VpnRouteSelection.logLabel(): String {
    return when (this) {
        VpnRouteSelection.Direct -> "Direct Connection"
        is VpnRouteSelection.Proxy -> "${proxy.type} ${proxy.host}:${proxy.port}"
    }
}
