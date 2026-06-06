package com.hightemp.proxy_switcher_vpn.vpn.engine

import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import kotlinx.coroutines.flow.StateFlow

data class VpnEngineStartRequest(
    val routeSelection: VpnRouteSelection,
    val generatedConfig: String
)

sealed interface VpnEngineCommandResult {
    data object Success : VpnEngineCommandResult
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : VpnEngineCommandResult
}

interface VpnEngine {
    val state: StateFlow<VpnEngineState>
    val logs: StateFlow<List<VpnEngineLog>>
    val counters: StateFlow<VpnEngineCounters>

    suspend fun start(request: VpnEngineStartRequest): VpnEngineCommandResult

    suspend fun stop(): VpnEngineCommandResult
}
