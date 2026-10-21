package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyCheckRepository
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.proxy.ParsedProxyString
import com.hightemp.proxy_switcher_vpn.proxy.ProxyImportLine
import com.hightemp.proxy_switcher_vpn.proxy.ProxyImportStatus
import com.hightemp.proxy_switcher_vpn.proxy.ProxyInspector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyLineImport
import com.hightemp.proxy_switcher_vpn.proxy.ProxyProtocolDetector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.SpeedTestResult
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.ui.screens.ProxyGroupFilter
import com.hightemp.proxy_switcher_vpn.ui.screens.ProxySort
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

enum class ProxyDeleteCheck {
    ALLOWED,
    NEEDS_CONFIRMATION,
    BLOCKED_ACTIVE_VPN
}

data class BulkTestProgress(
    val done: Int,
    val total: Int,
    val running: Boolean
)

data class ProxyLineImportResult(
    val added: Int,
    val updated: Int,
    /** Lines whose protocol could not be detected; nothing was saved for them. */
    val failedLines: List<ProxyImportLine>
)

data class DeletedProxy(
    val proxy: ProxyEntity,
    val wasSelected: Boolean
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
    private val processExitHistory: ProcessExitHistory,
    private val proxyCheckRepository: ProxyCheckRepository,
    private val proxyInspector: ProxyInspector
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
    val proxyChecks: StateFlow<Map<Long, ProxyCheckEntity>> = proxyCheckRepository.getAllChecks()
        .map { checks -> checks.associateBy { it.proxyId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )
    private val _bulkTestProgress = MutableStateFlow<BulkTestProgress?>(null)
    val bulkTestProgress: StateFlow<BulkTestProgress?> = _bulkTestProgress.asStateFlow()
    private val _lineImportProgress = MutableStateFlow<BulkTestProgress?>(null)
    /** Protocol detection progress of a running line import. */
    val lineImportProgress: StateFlow<BulkTestProgress?> = _lineImportProgress.asStateFlow()
    private val _speedTestStates = MutableStateFlow<Map<Long, ProxyTestUiState>>(emptyMap())
    /** Running or failed speed tests; a finished test is shown from [proxyChecks]. */
    val speedTestStates: StateFlow<Map<Long, ProxyTestUiState>> = _speedTestStates.asStateFlow()
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

    /**
     * The proxy the running VPN uses cannot be deleted, and deleting the
     * selected proxy switches the route to Direct, so it needs confirmation.
     */
    fun proxyDeleteCheck(proxy: ProxyEntity): ProxyDeleteCheck {
        val selected = settings.value.selectedProxyId == proxy.id
        return when {
            selected && VpnRuntimeState.state.value.isForegroundServiceActive ->
                ProxyDeleteCheck.BLOCKED_ACTIVE_VPN
            selected -> ProxyDeleteCheck.NEEDS_CONFIRMATION
            else -> ProxyDeleteCheck.ALLOWED
        }
    }

    fun onProxyDeleted(
        proxy: ProxyEntity,
        onDeleted: (DeletedProxy) -> Unit = {}
    ) {
        if (proxyDeleteCheck(proxy) == ProxyDeleteCheck.BLOCKED_ACTIVE_VPN) return
        viewModelScope.launch {
            val wasSelected = settings.value.selectedProxyId == proxy.id
            if (wasSelected) {
                settingsRepository.setSelectedProxyId(null)
            }
            proxyRepository.deleteProxy(proxy)
            proxyCheckRepository.deleteCheck(proxy.id)
            _proxyTestResults.update { results -> results - proxy.id }
            onDeleted(DeletedProxy(proxy, wasSelected))
        }
    }

    /** Restores a deleted proxy with its id and, if it was selected, the selection. */
    fun onProxyDeleteUndone(deleted: DeletedProxy) {
        viewModelScope.launch {
            proxyRepository.insertProxy(deleted.proxy)
            if (deleted.wasSelected && settings.value.selectedProxyId == null) {
                settingsRepository.setSelectedProxyId(deleted.proxy.id)
            }
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
        viewModelScope.launch { checkProxy(proxy) }
    }

    /**
     * Checks [visible] proxies, or all when null: reachability, latency, exit
     * IP and country, 4 at a time.
     */
    fun onTestAllProxies(visible: List<ProxyEntity>? = null) {
        if (_bulkTestProgress.value?.running == true) return
        viewModelScope.launch {
            val proxies = visible ?: proxyRepository.getAllProxies().first()
            if (proxies.isEmpty()) return@launch
            _bulkTestProgress.value = BulkTestProgress(done = 0, total = proxies.size, running = true)
            val permits = Semaphore(BULK_TEST_PARALLELISM)
            proxies.map { proxy ->
                launch {
                    permits.withPermit { checkProxy(proxy) }
                    _bulkTestProgress.update { progress ->
                        progress?.let {
                            val done = it.done + 1
                            it.copy(done = done, running = done < it.total)
                        }
                    }
                }
            }.joinAll()
        }
    }

    fun onProxySpeedTestRequested(proxy: ProxyEntity) {
        if (proxy.id == 0L || _speedTestStates.value[proxy.id]?.status == ProxyTestStatus.TESTING) return
        _speedTestStates.update { states ->
            states + (proxy.id to ProxyTestUiState(ProxyTestStatus.TESTING, UserText.of(R.string.speed_test_running)))
        }
        viewModelScope.launch {
            when (val result = proxyInspector.downloadSpeed(proxy)) {
                is SpeedTestResult.Success -> {
                    proxyCheckRepository.recordSpeed(proxy.id, System.currentTimeMillis(), result.bitsPerSecond)
                    _speedTestStates.update { states -> states - proxy.id }
                }
                is SpeedTestResult.Failure -> _speedTestStates.update { states ->
                    states + (proxy.id to ProxyTestUiState(ProxyTestStatus.FAILURE, result.message))
                }
            }
        }
    }

    private suspend fun checkProxy(proxy: ProxyEntity) {
        _proxyTestResults.update { results ->
            results + (
                proxy.id to ProxyTestUiState(
                    status = ProxyTestStatus.TESTING,
                    message = UserText.of(R.string.proxy_testing_message)
                )
            )
        }
        val result = proxyTester.test(proxy)
        val exitInfo = if (result.success) proxyInspector.exitInfo(proxy) else null
        proxyCheckRepository.recordCheck(
            proxyId = proxy.id,
            checkedAtMillis = System.currentTimeMillis(),
            success = result.success,
            latencyMillis = result.latencyMillis,
            exitIp = exitInfo?.ip,
            countryCode = exitInfo?.countryCode
        )
        _proxyTestResults.update { results ->
            results + (
                proxy.id to ProxyTestUiState(
                    status = if (result.success) ProxyTestStatus.SUCCESS else ProxyTestStatus.FAILURE,
                    message = result.message
                )
            )
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

    /**
     * Saves the new and updated lines of a bulk import. Lines without a
     * protocol are probed first, 4 at a time; lines that no protocol answers
     * are returned as failed and not saved.
     */
    fun onImportProxyLines(
        lines: List<ProxyImportLine>,
        onResult: (ProxyLineImportResult) -> Unit
    ) {
        if (_lineImportProgress.value?.running == true) return
        val importable = lines.filter { line ->
            line.status == ProxyImportStatus.NEW || line.status == ProxyImportStatus.UPDATE
        }
        val toDetect = importable.filter { it.needsDetection }
        _lineImportProgress.value = BulkTestProgress(done = 0, total = toDetect.size, running = true)
        viewModelScope.launch {
            val permits = Semaphore(BULK_TEST_PARALLELISM)
            val detected = toDetect.map { line ->
                async {
                    val type = permits.withPermit { protocolDetector.detect(line.proxy!!) }
                    _lineImportProgress.update { progress -> progress?.copy(done = progress.done + 1) }
                    line.lineNumber to type
                }
            }.awaitAll().toMap()
            val existing = proxyRepository.getAllProxies().first().associateBy { it.id }
            val newProxies = mutableListOf<ProxyEntity>()
            val updatedProxies = mutableListOf<ProxyEntity>()
            val failedLines = mutableListOf<ProxyImportLine>()
            importable.forEach { line ->
                val parsed = line.proxy!!
                val type = parsed.type ?: detected[line.lineNumber]
                if (type == null) {
                    failedLines += line
                    return@forEach
                }
                val current = line.existingId?.let(existing::get)
                if (current != null) {
                    updatedProxies += ProxyLineImport.updatedProxy(current, parsed.copy(type = type), line.group)
                } else {
                    newProxies += ProxyLineImport.newProxy(parsed, type, line.group)
                }
            }
            proxyRepository.saveImported(newProxies, updatedProxies)
            _lineImportProgress.value = null
            onResult(ProxyLineImportResult(newProxies.size, updatedProxies.size, failedLines))
        }
    }

    fun onProxyListViewChanged(sort: ProxySort, group: ProxyGroupFilter) {
        viewModelScope.launch {
            settingsRepository.setProxyListView(sort.name, group.encode())
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

    private companion object {
        const val BULK_TEST_PARALLELISM = 4
    }
}
