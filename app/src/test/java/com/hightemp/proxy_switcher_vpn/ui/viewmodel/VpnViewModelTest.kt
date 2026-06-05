package com.hightemp.proxy_switcher_vpn.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.hightemp.proxy_switcher_vpn.data.local.ProxyDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTestResult
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class VpnViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        VpnRuntimeState.markStopped("Test reset.")
    }

    @Test
    fun startIsDisabledWithoutSelectedProxy() = runTest {
        val viewModel = newViewModel()

        assertFalse(viewModel.canStartVpn.value)
        assertFalse(viewModel.canStartVpnNow())

        viewModel.onStartVpnBlockedNoSelectedProxy()

        assertEquals(
            "Select a valid proxy before starting VPN.",
            viewModel.uiState.value.lastError
        )
    }

    @Test
    fun permissionCancellationLeavesRuntimeStopped() = runTest {
        val viewModel = newViewModel()
        VpnRuntimeState.markStarting("Starting VPN foreground service.")

        viewModel.onVpnPermissionResult(granted = false)

        assertEquals(VpnPermissionStatus.DENIED, viewModel.uiState.value.permissionStatus)
        assertEquals(
            "VPN permission was not granted.",
            viewModel.uiState.value.lastError
        )
        assertEquals(VpnServiceStatus.STOPPED, VpnRuntimeState.state.value.status)
        assertFalse(VpnRuntimeState.state.value.isForegroundServiceActive)
        assertFalse(viewModel.stats.value.isRunning)
    }

    private fun newViewModel(): VpnViewModel {
        return VpnViewModel(
            proxyRepository = ProxyRepository(FakeProxyDao()),
            settingsRepository = SettingsRepository(FakePreferencesDataStore()),
            proxyTester = FakeReachabilityTester(),
            diagnosticsRepository = VpnDiagnosticsRepository()
        )
    }

    private class FakeReachabilityTester : ProxyReachabilityTester {
        override suspend fun test(
            proxy: ProxyEntity,
            targetHost: String,
            targetPort: Int,
            timeoutMillis: Int
        ): ProxyTestResult {
            return ProxyTestResult(success = true, message = "Proxy test succeeded.")
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
