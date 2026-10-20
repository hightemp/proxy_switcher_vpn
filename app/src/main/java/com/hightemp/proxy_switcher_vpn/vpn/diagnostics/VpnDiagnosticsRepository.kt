package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundHealth
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.singbox.SingBoxConfigGenerator
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class VpnDiagnosticsRepository @Inject constructor() {
    private val configGenerator = SingBoxConfigGenerator()

    fun diagnostics(
        permissionStatus: Flow<VpnPermissionDiagnosticStatus>,
        runtimeState: Flow<VpnRuntimeSnapshot>,
        selectedRoute: Flow<VpnRouteSelection?>,
        stats: Flow<VpnStats>,
        backgroundHealth: Flow<BackgroundHealth> = flowOf(BackgroundHealth())
    ): Flow<VpnDiagnostics> {
        return combine(
            permissionStatus,
            runtimeState,
            selectedRoute,
            stats,
            backgroundHealth
        ) { permission, runtime, routeSelection, vpnStats, background ->
            buildDiagnostics(
                permission = permission,
                runtime = runtime,
                routeSelection = routeSelection,
                stats = vpnStats,
                backgroundHealth = background
            )
        }
    }

    fun buildDiagnostics(
        permission: VpnPermissionDiagnosticStatus,
        runtime: VpnRuntimeSnapshot,
        routeSelection: VpnRouteSelection?,
        stats: VpnStats,
        backgroundHealth: BackgroundHealth = BackgroundHealth()
    ): VpnDiagnostics {
        val generatedConfig = when (routeSelection) {
            VpnRouteSelection.Direct -> runCatching { configGenerator.generateDirect() }.getOrNull()
            is VpnRouteSelection.Proxy -> runCatching {
                configGenerator.generate(routeSelection.proxy)
            }.getOrNull()
            null -> null
        }
        val udpPolicy = generatedConfig?.udpPolicy ?: VpnDiagnostics().udpPolicy

        return VpnDiagnostics.fromStats(
            stats = stats,
            udpPolicy = udpPolicy,
            maskedConfigPreview = generatedConfig?.maskedPreview
        ).copy(
            vpnPermission = VpnDiagnostics.permissionField(permission),
            foregroundService = VpnDiagnostics.foregroundServiceField(runtime.status),
            singBoxCore = VpnDiagnostics.singBoxField(runtime.status),
            tunInterface = VpnDiagnostics.tunField(runtime.status),
            selectedProxy = VpnDiagnostics.selectedRouteField(routeSelection),
            batteryOptimization = VpnDiagnostics.batteryOptimizationField(
                backgroundHealth.restrictions
            ),
            backgroundRestriction = VpnDiagnostics.backgroundRestrictionField(
                backgroundHealth.restrictions
            ),
            alwaysOnVpn = VpnDiagnostics.alwaysOnField(backgroundHealth.alwaysOn),
            lastVpnStop = VpnDiagnostics.lastStopField(backgroundHealth.lastStop),
            lastProcessExit = VpnDiagnostics.lastProcessExitField(backgroundHealth.lastProcessExit),
            udp = DiagnosticField(
                label = UserText.of(R.string.diag_udp),
                value = UserText.raw(udpPolicy.diagnosticSummary),
                severity = DiagnosticSeverity.WARNING
            ),
            lastError = stats.lastError?.let(UserText::raw) ?: runtime.lastError
        )
    }
}
