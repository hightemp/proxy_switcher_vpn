package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyCheckRepository
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.proxy.ExitInfo
import com.hightemp.proxy_switcher_vpn.proxy.ParsedProxyString
import com.hightemp.proxy_switcher_vpn.proxy.ProxyFieldOrder
import com.hightemp.proxy_switcher_vpn.proxy.ProxyInspector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyLineImport
import com.hightemp.proxy_switcher_vpn.proxy.ProxyLineImportOptions
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTestResult
import com.hightemp.proxy_switcher_vpn.proxy.SpeedTestResult
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
import kotlinx.coroutines.flow.map
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
    fun proxyTestRecordsLatencyAndExitInfo() = runTest {
        val checkDao = FakeProxyCheckDao()
        val inspector = FakeInspector(ExitInfo("203.0.113.5", "DE"))
        val viewModel = newViewModel(checkDao = checkDao, inspector = inspector)
        backgroundScope.launch { viewModel.proxyChecks.collect() }
        val proxy = ProxyEntity(id = 3L, host = "proxy.example", port = 1080, type = ProxyType.SOCKS5)

        viewModel.onProxyTestRequested(proxy)
        advanceUntilIdle()

        val check = viewModel.proxyChecks.value.getValue(3L)
        assertEquals(true, check.success)
        assertEquals(42L, check.latencyMillis)
        assertEquals("203.0.113.5", check.exitIp)
        assertEquals("DE", check.countryCode)
        assertEquals(ProxyTestStatus.SUCCESS, viewModel.proxyTestResults.value.getValue(3L).status)
    }

    @Test
    fun testAllChecksEveryProxyAndReportsProgress() = runTest {
        val proxyDao = FakeProxyDao()
        val checkDao = FakeProxyCheckDao()
        val viewModel = newViewModel(proxyDao = proxyDao, checkDao = checkDao)
        (1L..6L).forEach { id ->
            proxyDao.insertProxy(ProxyEntity(id = id, host = "p$id.example", port = 1080, type = ProxyType.HTTP))
        }

        viewModel.onTestAllProxies()
        advanceUntilIdle()

        assertEquals((1L..6L).toSet(), checkDao.snapshot().keys)
        assertEquals(BulkTestProgress(done = 6, total = 6, running = false), viewModel.bulkTestProgress.value)
    }

    @Test
    fun speedTestStoresDownloadSpeed() = runTest {
        val checkDao = FakeProxyCheckDao()
        val viewModel = newViewModel(checkDao = checkDao)
        val proxy = ProxyEntity(id = 4L, host = "proxy.example", port = 1080, type = ProxyType.SOCKS5)

        viewModel.onProxySpeedTestRequested(proxy)
        advanceUntilIdle()

        assertEquals(54_000_000L, checkDao.snapshot().getValue(4L).downloadBitsPerSecond)
        assertEquals(null, viewModel.speedTestStates.value[4L])
    }

    @Test
    fun importProxyLinesAddsNewAndUpdatesExisting() = runTest {
        val proxyDao = FakeProxyDao()
        val existing = ProxyEntity(host = "192.0.2.1", port = 1080, type = ProxyType.HTTP, username = "u", password = "old", label = "Main")
        proxyDao.insertProxy(existing)
        val viewModel = newViewModel(proxyDao = proxyDao)
        val preview = ProxyLineImport.preview(
            text = "socks5://u:new@192.0.2.1:1080\nhttp://192.0.2.2:3128\nnot a proxy",
            options = ProxyLineImportOptions(protocol = null, fieldOrder = ProxyFieldOrder.HOST_FIRST),
            existing = proxyDao.snapshot()
        )
        var result: ProxyLineImportResult? = null

        viewModel.onImportProxyLines(preview.lines) { result = it }
        advanceUntilIdle()

        assertEquals(ProxyLineImportResult(added = 1, updated = 1, failedLines = emptyList()), result)
        val saved = proxyDao.snapshot().associateBy { it.host }
        assertEquals(existing.copy(id = 1L, type = ProxyType.SOCKS5, password = "new"), saved["192.0.2.1"])
        assertEquals(ProxyType.HTTP, saved.getValue("192.0.2.2").type)
        assertEquals(2, saved.size)
        assertNull(viewModel.lineImportProgress.value)
    }

    @Test
    fun importProxyLinesDetectsProtocolAndReportsFailures() = runTest {
        val proxyDao = FakeProxyDao()
        val viewModel = newViewModel(
            proxyDao = proxyDao,
            proxyTester = FakeReachabilityTester(unreachableHosts = setOf("192.0.2.99"))
        )
        val preview = ProxyLineImport.preview(
            text = "192.0.2.10:8080\n192.0.2.99:8080",
            options = ProxyLineImportOptions(protocol = null, fieldOrder = ProxyFieldOrder.HOST_FIRST),
            existing = emptyList()
        )
        var result: ProxyLineImportResult? = null

        viewModel.onImportProxyLines(preview.lines) { result = it }
        advanceUntilIdle()

        assertEquals(1, result?.added)
        assertEquals(listOf(2), result?.failedLines?.map { it.lineNumber })
        assertEquals(listOf("192.0.2.10" to ProxyType.HTTPS), proxyDao.snapshot().map { it.host to it.type })
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
        processExitHistory: ProcessExitHistory = FakeProcessExitHistory(),
        checkDao: FakeProxyCheckDao = FakeProxyCheckDao(),
        inspector: ProxyInspector = FakeInspector(ExitInfo("192.0.2.1", "US")),
        proxyTester: ProxyReachabilityTester = FakeReachabilityTester()
    ): VpnViewModel {
        val dataStore = FakePreferencesDataStore()
        return VpnViewModel(
            proxyRepository = ProxyRepository(proxyDao),
            settingsRepository = SettingsRepository(dataStore),
            proxyTester = proxyTester,
            statsStore = VpnStatsStore(),
            diagnosticsRepository = VpnDiagnosticsRepository(),
            stopHistory = VpnStopHistory(dataStore),
            restrictionChecker = restrictionChecker,
            processExitHistory = processExitHistory,
            proxyCheckRepository = ProxyCheckRepository(checkDao),
            proxyInspector = inspector
        )
    }

    private class FakeInspector(private val exitInfo: ExitInfo?) : ProxyInspector {
        override suspend fun exitInfo(proxy: ProxyEntity): ExitInfo? = exitInfo

        override suspend fun downloadSpeed(proxy: ProxyEntity, bytes: Long): SpeedTestResult =
            SpeedTestResult.Success(bitsPerSecond = 54_000_000L, bytes = bytes, millis = 1_000L)
    }

    private class FakeProxyCheckDao : ProxyCheckDao {
        private val checks = MutableStateFlow<Map<Long, ProxyCheckEntity>>(emptyMap())

        override fun getAllChecks(): Flow<List<ProxyCheckEntity>> = checks.map { it.values.toList() }

        override suspend fun getCheck(proxyId: Long): ProxyCheckEntity? = checks.value[proxyId]

        override suspend fun upsertCheck(check: ProxyCheckEntity) {
            checks.value = checks.value + (check.proxyId to check)
        }

        override suspend fun deleteCheck(proxyId: Long) {
            checks.value = checks.value - proxyId
        }

        fun snapshot(): Map<Long, ProxyCheckEntity> = checks.value
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

    private class FakeReachabilityTester(
        private val unreachableHosts: Set<String> = emptySet()
    ) : ProxyReachabilityTester {
        override suspend fun test(
            proxy: ProxyEntity,
            targetHost: String,
            targetPort: Int,
            timeoutMillis: Int
        ): ProxyTestResult {
            return ProxyTestResult(
                success = proxy.host !in unreachableHosts,
                message = UserText.of(R.string.proxy_test_succeeded),
                latencyMillis = 42L
            )
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

        override suspend fun insertProxies(proxies: List<ProxyEntity>): List<Long> {
            return proxies.map { insertProxy(it) }
        }

        override suspend fun updateProxies(proxies: List<ProxyEntity>) {
            proxies.forEach { updateProxy(it) }
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
