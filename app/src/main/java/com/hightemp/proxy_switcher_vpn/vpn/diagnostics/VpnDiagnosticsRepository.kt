package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.singbox.SingBoxConfigGenerator
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class VpnDiagnosticsRepository @Inject constructor() {
    private val configGenerator = SingBoxConfigGenerator()

    fun diagnostics(
        permissionStatus: Flow<VpnPermissionDiagnosticStatus>,
        runtimeState: Flow<VpnRuntimeSnapshot>,
        selectedRoute: Flow<VpnRouteSelection?>,
        stats: Flow<VpnStats>
    ): Flow<VpnDiagnostics> {
        return combine(
            permissionStatus,
            runtimeState,
            selectedRoute,
            stats
        ) { permission, runtime, routeSelection, vpnStats ->
            buildDiagnostics(
                permission = permission,
                runtime = runtime,
                routeSelection = routeSelection,
                stats = vpnStats
            )
        }
    }

    fun buildDiagnostics(
        permission: VpnPermissionDiagnosticStatus,
        runtime: VpnRuntimeSnapshot,
        routeSelection: VpnRouteSelection?,
        stats: VpnStats
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
            udp = DiagnosticField(
                label = "UDP",
                value = udpPolicy.diagnosticSummary,
                severity = DiagnosticSeverity.WARNING
            ),
            lastError = stats.lastError ?: runtime.lastError
        )
    }
}
