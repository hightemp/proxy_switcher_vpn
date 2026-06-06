package com.hightemp.proxy_switcher_vpn.vpn.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.routing.proxyOrNull

class FakeVpnEngine @Inject constructor() : VpnEngine {
    private val _state = MutableStateFlow(VpnEngineState())
    override val state: StateFlow<VpnEngineState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<VpnEngineLog>>(emptyList())
    override val logs: StateFlow<List<VpnEngineLog>> = _logs.asStateFlow()

    private val _counters = MutableStateFlow(VpnEngineCounters())
    override val counters: StateFlow<VpnEngineCounters> = _counters.asStateFlow()

    private var nextStartFailure: String? = null
    var lastStartRequest: VpnEngineStartRequest? = null
        private set

    fun failNextStart(message: String) {
        nextStartFailure = message
    }

    override suspend fun start(
        request: VpnEngineStartRequest
    ): VpnEngineCommandResult {
        lastStartRequest = request
        val selectedProxy = request.routeSelection.proxyOrNull()?.let(SelectedProxySummary::from)
        val startFailure = nextStartFailure
        nextStartFailure = null

        appendLog(
            VpnEngineLogLevel.INFO,
            "Starting VPN engine for ${request.routeSelection.logLabel()}."
        )
        _state.value = VpnEngineState(
            status = VpnEngineStatus.STARTING,
            selectedProxy = selectedProxy
        )

        if (startFailure != null) {
            _counters.update {
                it.copy(failedConnections = it.failedConnections + 1)
            }
            _state.value = VpnEngineState(
                status = VpnEngineStatus.ERROR,
                selectedProxy = selectedProxy,
                lastError = startFailure
            )
            appendLog(VpnEngineLogLevel.ERROR, startFailure)
            return VpnEngineCommandResult.Failure(startFailure)
        }

        _state.value = VpnEngineState(
            status = VpnEngineStatus.RUNNING,
            selectedProxy = selectedProxy
        )
        appendLog(VpnEngineLogLevel.INFO, "VPN engine running.")
        return VpnEngineCommandResult.Success
    }

    override suspend fun stop(): VpnEngineCommandResult {
        val currentProxy = _state.value.selectedProxy
        _state.value = VpnEngineState(
            status = VpnEngineStatus.STOPPING,
            selectedProxy = currentProxy
        )
        appendLog(VpnEngineLogLevel.INFO, "Stopping VPN engine.")
        _state.value = VpnEngineState(status = VpnEngineStatus.STOPPED)
        _counters.value = VpnEngineCounters()
        appendLog(VpnEngineLogLevel.INFO, "VPN engine stopped.")
        return VpnEngineCommandResult.Success
    }

    fun simulateRuntimeError(message: String) {
        val currentProxy = _state.value.selectedProxy
        _counters.update {
            it.copy(failedConnections = it.failedConnections + 1)
        }
        _state.value = VpnEngineState(
            status = VpnEngineStatus.ERROR,
            selectedProxy = currentProxy,
            lastError = message
        )
        appendLog(VpnEngineLogLevel.ERROR, message)
    }

    fun updateCounters(counters: VpnEngineCounters) {
        _counters.value = counters
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

    private companion object {
        const val MAX_LOG_ENTRIES = 100
    }
}

private fun VpnRouteSelection.logLabel(): String {
    return when (this) {
        VpnRouteSelection.Direct -> "Direct Connection"
        is VpnRouteSelection.Proxy -> "${proxy.type} ${proxy.host}:${proxy.port}"
    }
}
