package com.hightemp.proxy_switcher_vpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

data class VpnRuntimeSnapshot(
    val status: VpnServiceStatus = VpnServiceStatus.STOPPED,
    val isForegroundServiceActive: Boolean = false,
    val startedAtMillis: Long? = null,
    val statusMessage: String? = null,
    val lastError: String? = null
) {
    val isRunning: Boolean = status == VpnServiceStatus.RUNNING
}

object VpnRuntimeState {
    private val _state = MutableStateFlow(VpnRuntimeSnapshot())
    val state: StateFlow<VpnRuntimeSnapshot> = _state.asStateFlow()

    fun markStarting(message: String) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.STARTING,
            isForegroundServiceActive = true,
            statusMessage = message,
            lastError = null
        )
    }

    fun markRunning(message: String) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.RUNNING,
            isForegroundServiceActive = true,
            startedAtMillis = System.currentTimeMillis(),
            statusMessage = message,
            lastError = null
        )
    }

    fun markStopping(message: String) {
        _state.value = _state.value.copy(
            status = VpnServiceStatus.STOPPING,
            statusMessage = message
        )
    }

    fun markStopped(message: String) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.STOPPED,
            isForegroundServiceActive = false,
            statusMessage = message,
            lastError = null
        )
    }

    fun markFailedStopped(message: String) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.ERROR,
            isForegroundServiceActive = false,
            statusMessage = message,
            lastError = message
        )
    }
}
