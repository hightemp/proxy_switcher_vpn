package com.hightemp.proxy_switcher_vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.ui.screens.AddEditProxyScreen
import com.hightemp.proxy_switcher_vpn.ui.screens.HomeScreen
import com.hightemp.proxy_switcher_vpn.ui.screens.LogsScreen
import com.hightemp.proxy_switcher_vpn.ui.screens.ProxyListScreen
import com.hightemp.proxy_switcher_vpn.ui.screens.VpnDiagnosticsScreen
import com.hightemp.proxy_switcher_vpn.ui.theme.Proxy_switcher_vpnTheme
import com.hightemp.proxy_switcher_vpn.ui.viewmodel.VpnViewModel
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.vpn.ProxyVpnService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: VpnViewModel by viewModels()
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        viewModel.onVpnPermissionResult(granted)
        if (granted) {
            startProxyVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Proxy_switcher_vpnTheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val logs by AppLogger.logs.collectAsState()
                val runtimeState by VpnRuntimeState.state.collectAsState()
                val proxyList by viewModel.proxyList.collectAsState()
                val proxyTestResults by viewModel.proxyTestResults.collectAsState()
                val stats by viewModel.stats.collectAsState()
                val diagnostics by viewModel.diagnostics.collectAsState()
                val canStartVpn by viewModel.canStartVpn.collectAsState()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            uiState = uiState,
                            settings = settings,
                            runtimeState = runtimeState,
                            stats = stats,
                            proxies = proxyList,
                            selectedProxyId = settings.selectedProxyId,
                            diagnosticsSummary = "IPv4 only; DNS proxy-safe; UDP/443 blocked.",
                            lastError = uiState.lastError ?: runtimeState.lastError,
                            canStartVpn = canStartVpn,
                            onStartVpn = ::requestVpnPermission,
                            onStopVpn = ::stopProxyVpnService,
                            onDirectSelected = {
                                viewModel.onDirectSelected()
                                if (runtimeState.isForegroundServiceActive) {
                                    switchProxyVpnRoute(proxyId = null)
                                }
                            },
                            onProxySelected = { proxy ->
                                viewModel.onProxySelected(proxy)
                                if (runtimeState.isForegroundServiceActive) {
                                    switchProxyVpnRoute(proxyId = proxy.id)
                                }
                            },
                            onManageProxies = { navController.navigate("proxy_list") },
                            onViewLogs = { navController.navigate("logs") },
                            onViewDiagnostics = { navController.navigate("diagnostics") },
                            onPrivacyDisclosureAccepted =
                                viewModel::onPrivacyDisclosureAccepted,
                            onDomainDestinationLoggingEnabled =
                                viewModel::onDomainDestinationLoggingEnabled
                        )
                    }
                    composable("logs") {
                        LogsScreen(
                            logs = logs,
                            onBack = { navController.popBackStack() },
                            onClearLogs = AppLogger::clear
                        )
                    }
                    composable("diagnostics") {
                        VpnDiagnosticsScreen(
                            diagnostics = diagnostics,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("proxy_list") {
                        ProxyListScreen(
                            proxies = proxyList,
                            selectedProxyId = settings.selectedProxyId,
                            proxyTestResults = proxyTestResults,
                            onBack = { navController.popBackStack() },
                            onAddProxy = { navController.navigate("add_proxy") },
                            onEditProxy = { proxy ->
                                navController.navigate("edit_proxy/${proxy.id}")
                            },
                            onSelectProxy = { proxy ->
                                viewModel.onProxySelected(proxy)
                                if (runtimeState.isForegroundServiceActive) {
                                    switchProxyVpnRoute(proxyId = proxy.id)
                                }
                            },
                            onDeleteProxy = viewModel::onProxyDeleted,
                            onTestProxy = viewModel::onProxyTestRequested,
                            onExportProxies = viewModel::exportProxiesToText,
                            onImportProxies = viewModel::importProxiesFromText
                        )
                    }
                    composable("add_proxy") {
                        AddEditProxyScreen(
                            proxy = null,
                            isEdit = false,
                            onBack = { navController.popBackStack() },
                            onSave = { proxy ->
                                viewModel.onProxySaved(proxy)
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(
                        route = "edit_proxy/{proxyId}",
                        arguments = listOf(
                            navArgument("proxyId") { type = NavType.LongType }
                        )
                    ) { backStackEntry ->
                        val proxyId = backStackEntry.arguments?.getLong("proxyId")
                        val proxy = proxyList.firstOrNull { it.id == proxyId }
                        AddEditProxyScreen(
                            proxy = proxy,
                            isEdit = true,
                            onBack = { navController.popBackStack() },
                            onSave = { updatedProxy ->
                                viewModel.onProxySaved(updatedProxy)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestVpnPermission() {
        if (!viewModel.canStartVpnNow()) {
            viewModel.onStartVpnBlockedNoSelectedProxy()
            return
        }
        viewModel.onStartVpnClicked()
        val permissionIntent: Intent? = VpnService.prepare(this)
        if (permissionIntent == null) {
            viewModel.onVpnPermissionAlreadyGranted()
            startProxyVpnService()
        } else {
            viewModel.onVpnPermissionRequired()
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startProxyVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProxyVpnService() {
        viewModel.onStopVpnClicked()
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        startService(intent)
    }

    private fun switchProxyVpnRoute(proxyId: Long?) {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_SWITCH_ROUTE
            putExtra(
                ProxyVpnService.EXTRA_PROXY_ID,
                proxyId ?: ProxyVpnService.EXTRA_PROXY_ID_DIRECT
            )
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
