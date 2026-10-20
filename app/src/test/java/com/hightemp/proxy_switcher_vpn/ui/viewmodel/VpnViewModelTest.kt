package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.proxy.ParsedProxyString
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTestResult
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionChecker
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitHistory
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitRecord
import com.hightemp.proxy_switcher_vpn.vpn.background.VpnStopHistory
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnosticsRepository
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStatsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class VpnViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        VpnRuntimeState.markStopped(UserText.raw("Test reset."))
    }

    @After
    fun tearDown() {
        VpnRuntimeState.markStopped(UserText.raw("Test reset."))
    }

    @Test
    fun directRouteIsStartableByDefault() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.canStartVpn.collect() }
        advanceUntilIdle()

        assertEquals("Direct Connection", viewModel.selectedRoute.value?.displayLabel)
        assertTrue(viewModel.canStartVpn.value)
        assertTrue(viewModel.canStartVpnNow())
    }

    @Test
    fun startIsDisabledWhenStoredProxyIdIsUnavailable() = runTest {
        val viewModel = newViewModel()

        viewModel.onProxySelected(
            ProxyEntity(
                id = 99L,
                host = "missing.example",
                port = 1080,
                type = ProxyType.SOCKS5
            )
        )
        backgroundScope.launch { viewModel.canStartVpn.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.canStartVpn.value)
        assertFalse(viewModel.canStartVpnNow())

        viewModel.onStartVpnBlockedNoSelectedProxy()

        assertEquals(
            UserText.of(R.string.error_no_valid_route),
            viewModel.uiState.value.lastError
        )
    }

    @Test
    fun permissionCancellationLeavesRuntimeStopped() = runTest {
        val viewModel = newViewModel()
        VpnRuntimeState.markStarting(UserText.raw("Starting VPN foreground service."))

        viewModel.onVpnPermissionResult(granted = false)

        assertEquals(VpnPermissionStatus.DENIED, viewModel.uiState.value.permissionStatus)
        assertEquals(
            UserText.of(R.string.permission_not_granted),
            viewModel.uiState.value.lastError
        )
        assertEquals(VpnServiceStatus.STOPPED, VpnRuntimeState.state.value.status)
        assertFalse(VpnRuntimeState.state.value.isForegroundServiceActive)
        assertFalse(viewModel.stats.value.isRunning)
    }

    @Test
    fun importProxiesFromReferenceTextInsertsFreshRows() = runTest {
        val proxyDao = FakeProxyDao()
        val viewModel = newViewModel(proxyDao = proxyDao)
        var importedCount = -1
        var errorMessage: UserText? = UserText.raw("unset")

        viewModel.importProxiesFromText(
            """
                [
                  {
                    "host": "legacy.example",
                    "port": 1080,
                    "type": "SOCKS5",
                    "username": "user",
                    "password": "password",
                    "label": "Legacy",
                    "isEnabled": true
                  }
                ]
            """.trimIndent()
        ) { count, error ->
            importedCount = count
            errorMessage = error
        }
        advanceUntilIdle()

        assertEquals(1, importedCount)
        assertNull(errorMessage)
        val imported = proxyDao.snapshot().single()
        assertEquals(1L, imported.id)
        assertEquals("legacy.example", imported.host)
        assertEquals(1080, imported.port)
        assertEquals(UserText.Plural(R.plurals.proxy_list_imported, 1), viewModel.uiState.value.message)
    }

    @Test
    fun invalidProxyImportDoesNotInsertRows() = runTest {
        val proxyDao = FakeProxyDao()
        val viewModel = newViewModel(proxyDao = proxyDao)
        var importedCount = -1
        var errorMessage: UserText? = null

        viewModel.importProxiesFromText("not-json") { count, error ->
            importedCount = count
            errorMessage = error
        }
        advanceUntilIdle()

        assertEquals(0, importedCount)
        assertEquals(emptyList<ProxyEntity>(), proxyDao.snapshot())
        assertTrue(errorMessage != null)
    }

    @Test
    fun detectProxyProtocolReportsFirstWorkingType() = runTest {
        val viewModel = newViewModel()
        var detected: ProxyType? = null

        viewModel.detectProxyProtocol(
            ParsedProxyString(host = "proxy.example", port = 1080, type = null)
        ) { type ->
            detected = type
        }
        advanceUntilIdle()

        assertEquals(ProxyType.HTTPS, detected)
    }

    @Test
    fun deletedProxyCanBeRestoredWithItsSelection() = runTest {
        val proxyDao = FakeProxyDao()
        val viewModel = newViewModel(proxyDao = proxyDao)
        backgroundScope.launch { viewModel.settings.collect() }
        backgroundScope.launch { viewModel.proxyList.collect() }
        val proxy = ProxyEntity(id = 7L, host = "proxy.example", port = 1080, type = ProxyType.SOCKS5)
        proxyDao.insertProxy(proxy)
        viewModel.onProxySelected(proxy)
        advanceUntilIdle()
        var deleted: DeletedProxy? = null

        viewModel.onProxyDeleted(proxy) { deleted = it }
        advanceUntilIdle()

        assertEquals(emptyList<ProxyEntity>(), proxyDao.snapshot())
        assertNull(viewModel.settings.value.selectedProxyId)
        assertEquals(DeletedProxy(proxy, wasSelected = true), deleted)

        viewModel.onProxyDeleteUndone(deleted!!)
        advanceUntilIdle()

        assertEquals(listOf(proxy), proxyDao.snapshot())
        assertEquals(7L, viewModel.settings.value.selectedProxyId)
    }

    @Test
    fun deletingSelectedProxyNeedsConfirmationAndActiveVpnProxyIsBlocked() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.settings.collect() }
        val selected = ProxyEntity(id = 1L, host = "a.example", port = 1080, type = ProxyType.SOCKS5)
        val other = ProxyEntity(id = 2L, host = "b.example", port = 1080, type = ProxyType.SOCKS5)
        viewModel.onProxySelected(selected)
        advanceUntilIdle()

        assertEquals(ProxyDeleteCheck.ALLOWED, viewModel.proxyDeleteCheck(other))
        assertEquals(ProxyDeleteCheck.NEEDS_CONFIRMATION, viewModel.proxyDeleteCheck(selected))

        VpnRuntimeState.markRunning(UserText.raw("running"))

        assertEquals(ProxyDeleteCheck.BLOCKED_ACTIVE_VPN, viewModel.proxyDeleteCheck(selected))
        assertEquals(ProxyDeleteCheck.ALLOWED, viewModel.proxyDeleteCheck(other))
    }

    @Test
    fun refreshBackgroundHealthReadsRestrictionsAndLastExit() = runTest {
        val checker = FakeRestrictionChecker(
            BackgroundRestrictionStatus(batteryOptimizationIgnored = false)
        )
        val exits = FakeProcessExitHistory(
            listOf(
                ProcessExitRecord(
                    timestampMillis = 100L,
                    reason = ProcessExitRecord.REASON_SIGNALED,
                    status = 9,
                    importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
                )
            )
        )
        val viewModel = newViewModel(restrictionChecker = checker, processExitHistory = exits)
        val collector = launch { viewModel.diagnostics.collect {} }

        viewModel.refreshBackgroundHealth()
        advanceUntilIdle()

        assertTrue(viewModel.backgroundRestrictions.value.needsAttention)
        assertEquals(UserText.of(R.string.diag_optimized), viewModel.diagnostics.value.batteryOptimization.value)
        assertEquals(
            UserText.of(R.string.exit_signaled, 9),
            (viewModel.diagnostics.value.lastProcessExit.value as UserText.Res).args[1]
        )

        checker.status = BackgroundRestrictionStatus(
            batteryOptimizationIgnored = true,
            backgroundRestricted = false
        )
        viewModel.refreshBackgroundHealth()
        advanceUntilIdle()

        assertFalse(viewModel.backgroundRestrictions.value.needsAttention)
        assertEquals(UserText.of(R.string.diag_unrestricted), viewModel.diagnostics.value.batteryOptimization.value)
        collector.cancel()
    }

    private fun newViewModel(
        proxyDao: ProxyDao = FakeProxyDao(),
        restrictionChecker: BackgroundRestrictionChecker = FakeRestrictionChecker(),
        processExitHistory: ProcessExitHistory = FakeProcessExitHistory()
    ): VpnViewModel {
        val dataStore = FakePreferencesDataStore()
        return VpnViewModel(
            proxyRepository = ProxyRepository(proxyDao),
            settingsRepository = SettingsRepository(dataStore),
            proxyTester = FakeReachabilityTester(),
            statsStore = VpnStatsStore(),
            diagnosticsRepository = VpnDiagnosticsRepository(),
            stopHistory = VpnStopHistory(dataStore),
            restrictionChecker = restrictionChecker,
            processExitHistory = processExitHistory
        )
    }

    private class FakeRestrictionChecker(
        var status: BackgroundRestrictionStatus = BackgroundRestrictionStatus()
    ) : BackgroundRestrictionChecker {
        override fun current(): BackgroundRestrictionStatus = status
    }

    private class FakeProcessExitHistory(
        private val records: List<ProcessExitRecord> = emptyList()
    ) : ProcessExitHistory {
        override fun recentExits(): List<ProcessExitRecord> = records
    }

    private class FakeReachabilityTester : ProxyReachabilityTester {
        override suspend fun test(
            proxy: ProxyEntity,
            targetHost: String,
            targetPort: Int,
            timeoutMillis: Int
        ): ProxyTestResult {
            return ProxyTestResult(success = true, message = UserText.of(R.string.proxy_test_succeeded))
        }
    }

    private class FakeProxyDao : ProxyDao {
        private val proxies = MutableStateFlow<List<ProxyEntity>>(emptyList())

        override fun getAllProxies(): Flow<List<ProxyEntity>> = proxies

        override suspend fun getProxyById(id: Long): ProxyEntity? {
            return proxies.value.firstOrNull { proxy -> proxy.id == id }
        }

        override suspend fun insertProxy(proxy: ProxyEntity): Long {
            val id = proxy.id.takeIf { it != 0L }
                ?: ((proxies.value.maxOfOrNull { it.id } ?: 0L) + 1L)
            proxies.value = listOf(proxy.copy(id = id)) + proxies.value
            return id
        }

        override suspend fun updateProxy(proxy: ProxyEntity) {
            proxies.value = proxies.value.map { existing ->
                if (existing.id == proxy.id) proxy else existing
            }
        }

        override suspend fun deleteProxy(proxy: ProxyEntity) {
            proxies.value = proxies.value.filterNot { existing -> existing.id == proxy.id }
        }

        fun snapshot(): List<ProxyEntity> = proxies.value
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
