package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.utils.ProxyTransfer
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnostics
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnosticsRepository
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnPermissionDiagnosticStatus
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.routing.isValidForStart
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStatsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VpnPermissionStatus {
    UNKNOWN,
    REQUIRED,
    REQUESTING,
    GRANTED,
    DENIED
}

data class VpnPermissionUiState(
    val permissionStatus: VpnPermissionStatus = VpnPermissionStatus.UNKNOWN,
    val lastPermissionGranted: Boolean? = null,
    val message: String = "VPN permission has not been checked.",
    val lastError: String? = null
)

enum class ProxyTestStatus {
    TESTING,
    SUCCESS,
    FAILURE
}

data class ProxyTestUiState(
    val status: ProxyTestStatus,
    val message: String
)

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val proxyRepository: ProxyRepository,
    private val settingsRepository: SettingsRepository,
    private val proxyTester: ProxyReachabilityTester,
    private val statsStore: VpnStatsStore,
    diagnosticsRepository: VpnDiagnosticsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(VpnPermissionUiState())
    private val _proxyTestResults = MutableStateFlow<Map<Long, ProxyTestUiState>>(emptyMap())
    val uiState: StateFlow<VpnPermissionUiState> = _uiState.asStateFlow()
    val proxyTestResults: StateFlow<Map<Long, ProxyTestUiState>> =
        _proxyTestResults.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )
    val proxyList: StateFlow<List<ProxyEntity>> = proxyRepository.getAllProxies().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
    val selectedProxy: StateFlow<ProxyEntity?> = combine(settings, proxyList) { settings, proxies ->
        settings.selectedProxyId?.let { selectedId ->
            proxies.firstOrNull { proxy -> proxy.id == selectedId }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )
    val selectedRoute: StateFlow<VpnRouteSelection?> = combine(settings, proxyList) { settings, proxies ->
        val selectedProxyId = settings.selectedProxyId
        if (selectedProxyId == null) {
            VpnRouteSelection.Direct
        } else {
            proxies.firstOrNull { proxy -> proxy.id == selectedProxyId }
                ?.let(VpnRouteSelection::Proxy)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VpnRouteSelection.Direct
    )
    val canStartVpn: StateFlow<Boolean> = selectedRoute
        .combine(_uiState) { proxy, uiState ->
            proxy.isValidForStart() &&
                uiState.permissionStatus != VpnPermissionStatus.REQUESTING
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
    val stats: StateFlow<VpnStats> = statsStore.stats
    val diagnostics: StateFlow<VpnDiagnostics> = diagnosticsRepository.diagnostics(
        permissionStatus = _uiState.map { state ->
            state.permissionStatus.toDiagnosticStatus()
        },
        runtimeState = VpnRuntimeState.state,
        selectedRoute = selectedRoute,
        stats = stats
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VpnDiagnostics()
    )

    fun onStartVpnClicked() {
        _uiState.update {
            it.copy(
                permissionStatus = VpnPermissionStatus.UNKNOWN,
                message = "Checking VPN permission...",
                lastError = null
            )
        }
    }

    fun onVpnPermissionRequired() {
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.REQUESTING,
            message = "Waiting for VPN permission result."
        )
    }

    fun onVpnPermissionAlreadyGranted() {
        statsStore.startSession()
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.GRANTED,
            lastPermissionGranted = true,
            message = "VPN permission is granted. Starting VPN."
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.value = if (granted) {
            statsStore.startSession()
            VpnPermissionUiState(
                permissionStatus = VpnPermissionStatus.GRANTED,
                lastPermissionGranted = true,
                message = "VPN permission granted. Starting VPN."
            )
        } else {
            statsStore.stopSession()
            VpnRuntimeState.markStopped("VPN permission was not granted.")
            VpnPermissionUiState(
                permissionStatus = VpnPermissionStatus.DENIED,
                lastPermissionGranted = false,
                message = "VPN permission was not granted.",
                lastError = "VPN permission was not granted."
            )
        }
    }

    fun onVpnPermissionRequestUnavailable() {
        statsStore.stopSession()
        VpnRuntimeState.markStopped("VPN permission is required before the VPN can start.")
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.REQUIRED,
            lastPermissionGranted = false,
            message = "VPN permission is required before the VPN can start.",
            lastError = "VPN permission is required before the VPN can start."
        )
    }

    fun onStartVpnBlockedNoSelectedProxy() {
        _uiState.update {
            it.copy(
                message = "Select Direct or a valid enabled proxy before starting VPN.",
                lastError = "Select Direct or a valid enabled proxy before starting VPN."
            )
        }
    }

    fun onStopVpnClicked() {
        statsStore.stopSession()
        _uiState.update {
            it.copy(
                message = "VPN stop requested.",
                lastError = null
            )
        }
    }

    fun canStartVpnNow(): Boolean =
        selectedRoute.value.isValidForStart() &&
            !VpnRuntimeState.state.value.isForegroundServiceActive

    fun onDirectSelected() {
        viewModelScope.launch {
            settingsRepository.setSelectedProxyId(null)
        }
    }

    fun onProxySelected(proxy: ProxyEntity) {
        viewModelScope.launch {
            settingsRepository.setSelectedProxyId(proxy.id)
        }
    }

    fun onProxyDeleted(proxy: ProxyEntity) {
        viewModelScope.launch {
            if (settings.value.selectedProxyId == proxy.id) {
                settingsRepository.setSelectedProxyId(null)
            }
            proxyRepository.deleteProxy(proxy)
            _proxyTestResults.update { results -> results - proxy.id }
        }
    }

    fun onProxySaved(proxy: ProxyEntity) {
        viewModelScope.launch {
            if (proxy.id == 0L) {
                proxyRepository.insertProxy(proxy)
            } else {
                proxyRepository.updateProxy(proxy)
            }
            _uiState.update {
                it.copy(
                    message = "Proxy saved.",
                    lastError = null
                )
            }
        }
    }

    fun onProxyTestRequested(proxy: ProxyEntity) {
        if (proxy.id == 0L) return

        _proxyTestResults.update { results ->
            results + (
                proxy.id to ProxyTestUiState(
                    status = ProxyTestStatus.TESTING,
                    message = "Testing proxy..."
                )
            )
        }
        viewModelScope.launch {
            val result = proxyTester.test(proxy)
            _proxyTestResults.update { results ->
                results + (
                    proxy.id to ProxyTestUiState(
                        status = if (result.success) {
                            ProxyTestStatus.SUCCESS
                        } else {
                            ProxyTestStatus.FAILURE
                        },
                        message = result.message
                    )
                )
            }
        }
    }

    fun exportProxiesToText(): String = ProxyTransfer.export(proxyList.value)

    fun importProxiesFromText(
        text: String,
        onResult: (importedCount: Int, errorMessage: String?) -> Unit
    ) {
        val result = ProxyTransfer.import(text)
        if (!result.isSuccess) {
            onResult(0, result.errorMessage)
            return
        }
        viewModelScope.launch {
            result.proxies.forEach { proxy ->
                proxyRepository.insertProxy(proxy.copy(id = 0L))
            }
            _uiState.update {
                it.copy(
                    message = "Imported ${result.proxies.size} proxies.",
                    lastError = null
                )
            }
            onResult(result.proxies.size, null)
        }
    }

    fun onProxyEditorUnavailable() {
        _uiState.update {
            it.copy(
                message = "Proxy editor is not available yet.",
                lastError = null
            )
        }
    }

    fun onPrivacyDisclosureAccepted(accepted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrivacyDisclosureAccepted(accepted)
        }
    }

    fun onDomainDestinationLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDomainDestinationLoggingEnabled(enabled)
        }
    }

    fun onInfiniteReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setInfiniteReconnectEnabled(enabled)
        }
    }

    private fun VpnPermissionStatus.toDiagnosticStatus(): VpnPermissionDiagnosticStatus {
        return when (this) {
            VpnPermissionStatus.UNKNOWN -> VpnPermissionDiagnosticStatus.UNKNOWN
            VpnPermissionStatus.REQUIRED -> VpnPermissionDiagnosticStatus.REQUIRED
            VpnPermissionStatus.REQUESTING -> VpnPermissionDiagnosticStatus.REQUESTING
            VpnPermissionStatus.GRANTED -> VpnPermissionDiagnosticStatus.GRANTED
            VpnPermissionStatus.DENIED -> VpnPermissionDiagnosticStatus.DENIED
        }
    }
}
