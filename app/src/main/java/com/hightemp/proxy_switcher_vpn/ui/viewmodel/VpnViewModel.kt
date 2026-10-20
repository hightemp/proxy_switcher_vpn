package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.proxy.ParsedProxyString
import com.hightemp.proxy_switcher_vpn.proxy.ProxyProtocolDetector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.utils.ProxyTransfer
import com.hightemp.proxy_switcher_vpn.vpn.background.AlwaysOnVpnState
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundHealth
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionChecker
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitHistory
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitRecord
import com.hightemp.proxy_switcher_vpn.vpn.background.VpnStopHistory
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
    val message: UserText = UserText.of(R.string.permission_not_checked),
    val lastError: UserText? = null
)

enum class ProxyTestStatus {
    TESTING,
    SUCCESS,
    FAILURE
}

data class ProxyTestUiState(
    val status: ProxyTestStatus,
    val message: UserText
)

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val proxyRepository: ProxyRepository,
    private val settingsRepository: SettingsRepository,
    private val proxyTester: ProxyReachabilityTester,
    private val statsStore: VpnStatsStore,
    diagnosticsRepository: VpnDiagnosticsRepository,
    stopHistory: VpnStopHistory,
    private val restrictionChecker: BackgroundRestrictionChecker,
    private val processExitHistory: ProcessExitHistory
) : ViewModel() {
    private val protocolDetector = ProxyProtocolDetector(proxyTester)
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
    private val _backgroundRestrictions = MutableStateFlow(BackgroundRestrictionStatus())
    val backgroundRestrictions: StateFlow<BackgroundRestrictionStatus> =
        _backgroundRestrictions.asStateFlow()
    val alwaysOnStatus = AlwaysOnVpnState.status
    private val lastProcessExit = MutableStateFlow<ProcessExitRecord?>(null)
    private val backgroundHealth = combine(
        _backgroundRestrictions,
        AlwaysOnVpnState.status,
        stopHistory.lastStop,
        lastProcessExit
    ) { restrictions, alwaysOn, lastStop, lastExit ->
        BackgroundHealth(
            restrictions = restrictions,
            alwaysOn = alwaysOn,
            lastStop = lastStop,
            lastProcessExit = lastExit
        )
    }
    val diagnostics: StateFlow<VpnDiagnostics> = diagnosticsRepository.diagnostics(
        permissionStatus = _uiState.map { state ->
            state.permissionStatus.toDiagnosticStatus()
        },
        runtimeState = VpnRuntimeState.state,
        selectedRoute = selectedRoute,
        stats = stats,
        backgroundHealth = backgroundHealth
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VpnDiagnostics()
    )

    /** Re-reads power settings and exit history; called when the UI resumes. */
    fun refreshBackgroundHealth() {
        _backgroundRestrictions.value = restrictionChecker.current()
        lastProcessExit.value = processExitHistory.recentExits().maxByOrNull { it.timestampMillis }
    }

    fun onStartVpnClicked() {
        _uiState.update {
            it.copy(
                permissionStatus = VpnPermissionStatus.UNKNOWN,
                message = UserText.of(R.string.permission_checking),
                lastError = null
            )
        }
    }

    fun onVpnPermissionRequired() {
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.REQUESTING,
            message = UserText.of(R.string.permission_waiting)
        )
    }

    fun onVpnPermissionAlreadyGranted() {
        statsStore.startSession()
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.GRANTED,
            lastPermissionGranted = true,
            message = UserText.of(R.string.permission_already_granted)
        )
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.value = if (granted) {
            statsStore.startSession()
            VpnPermissionUiState(
                permissionStatus = VpnPermissionStatus.GRANTED,
                lastPermissionGranted = true,
                message = UserText.of(R.string.permission_granted_starting)
            )
        } else {
            statsStore.stopSession()
            VpnRuntimeState.markStopped(UserText.of(R.string.permission_not_granted))
            VpnPermissionUiState(
                permissionStatus = VpnPermissionStatus.DENIED,
                lastPermissionGranted = false,
                message = UserText.of(R.string.permission_not_granted),
                lastError = UserText.of(R.string.permission_not_granted)
            )
        }
    }

    fun onVpnPermissionRequestUnavailable() {
        statsStore.stopSession()
        VpnRuntimeState.markStopped(UserText.of(R.string.permission_required_message))
        _uiState.value = VpnPermissionUiState(
            permissionStatus = VpnPermissionStatus.REQUIRED,
            lastPermissionGranted = false,
            message = UserText.of(R.string.permission_required_message),
            lastError = UserText.of(R.string.permission_required_message)
        )
    }

    fun onStartVpnBlockedNoSelectedProxy() {
        _uiState.update {
            it.copy(
                message = UserText.of(R.string.error_no_valid_route),
                lastError = UserText.of(R.string.error_no_valid_route)
            )
        }
    }

    fun onStopVpnClicked() {
        statsStore.stopSession()
        _uiState.update {
            it.copy(
                message = UserText.of(R.string.status_stop_requested),
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
                    message = UserText.of(R.string.proxy_saved),
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
                    message = UserText.of(R.string.proxy_testing_message)
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

    fun detectProxyProtocol(
        proxy: ParsedProxyString,
        onResult: (detectedType: ProxyType?) -> Unit
    ) {
        viewModelScope.launch {
            onResult(protocolDetector.detect(proxy))
        }
    }

    fun exportProxiesToText(): String = ProxyTransfer.export(proxyList.value)

    fun importProxiesFromText(
        text: String,
        onResult: (importedCount: Int, errorMessage: UserText?) -> Unit
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
                    message = UserText.Plural(R.plurals.proxy_list_imported, result.proxies.size),
                    lastError = null
                )
            }
            onResult(result.proxies.size, null)
        }
    }

    fun onProxyEditorUnavailable() {
        _uiState.update {
            it.copy(
                message = UserText.of(R.string.proxy_editor_unavailable),
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
