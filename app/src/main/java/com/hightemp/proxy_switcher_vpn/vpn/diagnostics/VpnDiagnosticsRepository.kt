package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
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
        selectedProxy: Flow<ProxyEntity?>,
        stats: Flow<VpnStats>
    ): Flow<VpnDiagnostics> {
        return combine(
            permissionStatus,
            runtimeState,
            selectedProxy,
            stats
        ) { permission, runtime, proxy, vpnStats ->
            buildDiagnostics(
                permission = permission,
                runtime = runtime,
                proxy = proxy,
                stats = vpnStats
            )
        }
    }

    fun buildDiagnostics(
        permission: VpnPermissionDiagnosticStatus,
        runtime: VpnRuntimeSnapshot,
        proxy: ProxyEntity?,
        stats: VpnStats
    ): VpnDiagnostics {
        val generatedConfig = proxy?.let { selectedProxy ->
            runCatching { configGenerator.generate(selectedProxy) }.getOrNull()
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
            selectedProxy = VpnDiagnostics.selectedProxyField(proxy),
            udp = DiagnosticField(
                label = "UDP",
                value = udpPolicy.diagnosticSummary,
                severity = DiagnosticSeverity.WARNING
            ),
            lastError = stats.lastError ?: runtime.lastError
        )
    }
}
