package com.hightemp.proxy_switcher_vpn.service

import com.hightemp.proxy_switcher_vpn.i18n.UserText
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
    val statusMessage: UserText? = null,
    val lastError: UserText? = null
) {
    val isRunning: Boolean = status == VpnServiceStatus.RUNNING
}

object VpnRuntimeState {
    private val _state = MutableStateFlow(VpnRuntimeSnapshot())
    val state: StateFlow<VpnRuntimeSnapshot> = _state.asStateFlow()

    fun markStarting(message: UserText) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.STARTING,
            isForegroundServiceActive = true,
            statusMessage = message,
            lastError = null
        )
    }

    fun markRunning(message: UserText) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.RUNNING,
            isForegroundServiceActive = true,
            startedAtMillis = System.currentTimeMillis(),
            statusMessage = message,
            lastError = null
        )
    }

    fun markStopping(message: UserText) {
        _state.value = _state.value.copy(
            status = VpnServiceStatus.STOPPING,
            statusMessage = message
        )
    }

    fun markStopped(message: UserText) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.STOPPED,
            isForegroundServiceActive = false,
            statusMessage = message,
            lastError = null
        )
    }

    fun markFailedStopped(message: UserText) {
        _state.value = VpnRuntimeSnapshot(
            status = VpnServiceStatus.ERROR,
            isForegroundServiceActive = false,
            statusMessage = message,
            lastError = message
        )
    }
}
