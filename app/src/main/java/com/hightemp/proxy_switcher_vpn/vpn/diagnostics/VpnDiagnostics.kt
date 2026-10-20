package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.AlwaysOnVpnStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionStatus
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitRecord
import com.hightemp.proxy_switcher_vpn.vpn.background.VpnStopReason
import com.hightemp.proxy_switcher_vpn.vpn.background.VpnStopRecord
import com.hightemp.proxy_switcher_vpn.vpn.background.formatDiagnosticTime
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import com.hightemp.proxy_switcher_vpn.vpn.udp.UdpPolicy
import java.util.Locale
import java.util.TimeZone

enum class StatsAvailability {
    AVAILABLE,
    NOT_SUPPORTED
}

enum class DiagnosticSeverity {
    OK,
    INFO,
    WARNING,
    ERROR
}

enum class VpnPermissionDiagnosticStatus {
    UNKNOWN,
    REQUIRED,
    REQUESTING,
    GRANTED,
    DENIED
}

data class DiagnosticField(
    val label: String,
    val value: String,
    val severity: DiagnosticSeverity
)

data class VpnDiagnostics(
    val vpnPermission: DiagnosticField = DiagnosticField(
        label = "VPN permission",
        value = "unknown",
        severity = DiagnosticSeverity.WARNING
    ),
    val foregroundService: DiagnosticField = DiagnosticField(
        label = "Foreground service",
        value = "stopped",
        severity = DiagnosticSeverity.INFO
    ),
    val singBoxCore: DiagnosticField = DiagnosticField(
        label = "sing-box/libbox",
        value = "libbox_stopped",
        severity = DiagnosticSeverity.INFO
    ),
    val tunInterface: DiagnosticField = DiagnosticField(
        label = "TUN",
        value = "not_created",
        severity = DiagnosticSeverity.INFO
    ),
    val dns: DiagnosticField = DiagnosticField(
        label = "DNS",
        value = "proxy_safe_doh_private_dns_blocked",
        severity = DiagnosticSeverity.OK
    ),
    val ipv4Route: DiagnosticField = DiagnosticField(
        label = "IPv4",
        value = "enabled",
        severity = DiagnosticSeverity.OK
    ),
    val ipv6: DiagnosticField = DiagnosticField(
        label = "IPv6",
        value = "unsupported_disabled",
        severity = DiagnosticSeverity.WARNING
    ),
    val udp: DiagnosticField = DiagnosticField(
        label = "UDP",
        value = UdpPolicy.mvpDefault().diagnosticSummary,
        severity = DiagnosticSeverity.WARNING
    ),
    val selectedProxy: DiagnosticField = DiagnosticField(
        label = "Selected route",
        value = "none",
        severity = DiagnosticSeverity.WARNING
    ),
    val batteryOptimization: DiagnosticField = batteryOptimizationField(BackgroundRestrictionStatus()),
    val backgroundRestriction: DiagnosticField = backgroundRestrictionField(BackgroundRestrictionStatus()),
    val alwaysOnVpn: DiagnosticField = alwaysOnField(AlwaysOnVpnStatus()),
    val lastVpnStop: DiagnosticField = lastStopField(null),
    val lastProcessExit: DiagnosticField = lastProcessExitField(null),
    val processMemory: DiagnosticField = DiagnosticField("Process memory (RSS)", "n/a", DiagnosticSeverity.INFO),
    val udpPolicy: UdpPolicy = UdpPolicy.mvpDefault(),
    val counters: VpnEngineCounters = VpnEngineCounters(),
    val trafficStatsAvailability: StatsAvailability = StatsAvailability.NOT_SUPPORTED,
    val activeConnectionStatsAvailability: StatsAvailability = StatsAvailability.NOT_SUPPORTED,
    val lastError: String? = null,
    val maskedConfigPreview: String? = null
) {
    val udp443Status: String =
        if (udpPolicy.blocksUdp443) "blocked" else "not_blocked"

    val nonDnsUdpStatus: String =
        if (udpPolicy.allowsDirectNonDnsUdp) "bypass" else "blocked"

    val trafficStatsStatus: String =
        if (trafficStatsAvailability == StatsAvailability.AVAILABLE) {
            "available"
        } else {
            "not_supported"
        }

    val activeConnectionStatsStatus: String =
        if (activeConnectionStatsAvailability == StatsAvailability.AVAILABLE) {
            "available"
        } else {
            "not_supported"
        }

    companion object {
        fun fromStats(
            stats: VpnStats,
            udpPolicy: UdpPolicy = UdpPolicy.mvpDefault(),
            maskedConfigPreview: String? = null
        ): VpnDiagnostics {
            return VpnDiagnostics(
                udp = DiagnosticField(
                    label = "UDP",
                    value = udpPolicy.diagnosticSummary,
                    severity = DiagnosticSeverity.WARNING
                ),
                udpPolicy = udpPolicy,
                counters = stats.toEngineCounters(),
                trafficStatsAvailability = if (stats.trafficStatsAvailable) {
                    StatsAvailability.AVAILABLE
                } else {
                    StatsAvailability.NOT_SUPPORTED
                },
                activeConnectionStatsAvailability = if (stats.activeConnectionsAvailable) {
                    StatsAvailability.AVAILABLE
                } else {
                    StatsAvailability.NOT_SUPPORTED
                },
                lastError = stats.lastError,
                processMemory = processMemoryField(stats),
                maskedConfigPreview = maskedConfigPreview
            )
        }

        fun permissionField(status: VpnPermissionDiagnosticStatus): DiagnosticField {
            return when (status) {
                VpnPermissionDiagnosticStatus.GRANTED -> DiagnosticField(
                    label = "VPN permission",
                    value = "granted",
                    severity = DiagnosticSeverity.OK
                )
                VpnPermissionDiagnosticStatus.REQUESTING -> DiagnosticField(
                    label = "VPN permission",
                    value = "requesting",
                    severity = DiagnosticSeverity.INFO
                )
                VpnPermissionDiagnosticStatus.REQUIRED -> DiagnosticField(
                    label = "VPN permission",
                    value = "required",
                    severity = DiagnosticSeverity.WARNING
                )
                VpnPermissionDiagnosticStatus.DENIED -> DiagnosticField(
                    label = "VPN permission",
                    value = "denied",
                    severity = DiagnosticSeverity.ERROR
                )
                VpnPermissionDiagnosticStatus.UNKNOWN -> DiagnosticField(
                    label = "VPN permission",
                    value = "unknown",
                    severity = DiagnosticSeverity.WARNING
                )
            }
        }

        fun foregroundServiceField(status: VpnServiceStatus): DiagnosticField {
            return DiagnosticField(
                label = "Foreground service",
                value = status.name.lowercase(),
                severity = when (status) {
                    VpnServiceStatus.RUNNING -> DiagnosticSeverity.OK
                    VpnServiceStatus.STARTING,
                    VpnServiceStatus.STOPPING -> DiagnosticSeverity.INFO
                    VpnServiceStatus.STOPPED -> DiagnosticSeverity.INFO
                    VpnServiceStatus.ERROR -> DiagnosticSeverity.ERROR
                }
            )
        }

        fun singBoxField(status: VpnServiceStatus): DiagnosticField {
            return DiagnosticField(
                label = "sing-box/libbox",
                value = "libbox_${status.name.lowercase()}",
                severity = when (status) {
                    VpnServiceStatus.RUNNING -> DiagnosticSeverity.OK
                    VpnServiceStatus.STARTING,
                    VpnServiceStatus.STOPPING -> DiagnosticSeverity.INFO
                    VpnServiceStatus.STOPPED -> DiagnosticSeverity.INFO
                    VpnServiceStatus.ERROR -> DiagnosticSeverity.ERROR
                }
            )
        }

        fun tunField(status: VpnServiceStatus): DiagnosticField {
            return DiagnosticField(
                label = "TUN",
                value = when (status) {
                    VpnServiceStatus.RUNNING -> "active"
                    VpnServiceStatus.STARTING -> "creating"
                    VpnServiceStatus.STOPPING -> "closing"
                    VpnServiceStatus.STOPPED -> "not_created"
                    VpnServiceStatus.ERROR -> "error"
                },
                severity = when (status) {
                    VpnServiceStatus.RUNNING -> DiagnosticSeverity.OK
                    VpnServiceStatus.STARTING,
                    VpnServiceStatus.STOPPING -> DiagnosticSeverity.INFO
                    VpnServiceStatus.STOPPED -> DiagnosticSeverity.INFO
                    VpnServiceStatus.ERROR -> DiagnosticSeverity.ERROR
                }
            )
        }

        fun selectedProxyField(proxy: ProxyEntity?): DiagnosticField {
            return if (proxy == null) {
                DiagnosticField(
                    label = "Selected route",
                    value = "none",
                    severity = DiagnosticSeverity.WARNING
                )
            } else {
                DiagnosticField(
                    label = "Selected route",
                    value = "${proxy.label ?: proxy.host}:${proxy.port} (${proxy.type})",
                    severity = if (proxy.isEnabled) {
                        DiagnosticSeverity.OK
                    } else {
                        DiagnosticSeverity.WARNING
                    }
                )
            }
        }

        fun processMemoryField(stats: VpnStats): DiagnosticField {
            val memory = stats.processMemoryBytes
                ?: return DiagnosticField("Process memory (RSS)", "n/a", DiagnosticSeverity.INFO)
            val goroutines = stats.goroutines?.let { ", $it goroutines" }.orEmpty()
            return DiagnosticField(
                label = "Process memory (RSS)",
                value = String.format(Locale.US, "%.1f MiB", memory / (1024.0 * 1024.0)) + goroutines,
                severity = DiagnosticSeverity.INFO
            )
        }

        fun batteryOptimizationField(status: BackgroundRestrictionStatus): DiagnosticField {
            return when (status.batteryOptimizationIgnored) {
                true -> DiagnosticField("Battery optimization", "unrestricted", DiagnosticSeverity.OK)
                false -> DiagnosticField("Battery optimization", "optimized", DiagnosticSeverity.WARNING)
                null -> DiagnosticField("Battery optimization", "unknown", DiagnosticSeverity.INFO)
            }
        }

        fun backgroundRestrictionField(status: BackgroundRestrictionStatus): DiagnosticField {
            return when (status.backgroundRestricted) {
                true -> DiagnosticField("Background activity", "restricted", DiagnosticSeverity.ERROR)
                false -> DiagnosticField("Background activity", "allowed", DiagnosticSeverity.OK)
                null -> DiagnosticField("Background activity", "unknown", DiagnosticSeverity.INFO)
            }
        }

        fun alwaysOnField(status: AlwaysOnVpnStatus): DiagnosticField {
            val value = when (status.alwaysOn) {
                true -> if (status.lockdown == true) "on, lockdown" else "on"
                false -> "off"
                null -> "unknown until VPN runs"
            }
            return DiagnosticField(
                label = "Always-on VPN",
                value = value,
                severity = if (status.alwaysOn == true) DiagnosticSeverity.OK else DiagnosticSeverity.INFO
            )
        }

        fun lastStopField(
            record: VpnStopRecord?,
            timeZone: TimeZone = TimeZone.getDefault()
        ): DiagnosticField {
            if (record == null) {
                return DiagnosticField("Last VPN stop", "none", DiagnosticSeverity.INFO)
            }
            val message = record.message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            return DiagnosticField(
                label = "Last VPN stop",
                value = "${formatDiagnosticTime(record.timestampMillis, timeZone)} · " +
                    record.reason.label + message,
                severity = when (record.reason) {
                    VpnStopReason.USER -> DiagnosticSeverity.INFO
                    VpnStopReason.PERMISSION_REVOKED,
                    VpnStopReason.SERVICE_DESTROYED -> DiagnosticSeverity.WARNING
                    VpnStopReason.FAIL_CLOSED -> DiagnosticSeverity.ERROR
                }
            )
        }

        fun lastProcessExitField(
            record: ProcessExitRecord?,
            timeZone: TimeZone = TimeZone.getDefault()
        ): DiagnosticField {
            if (record == null) {
                return DiagnosticField("Last app exit", "none", DiagnosticSeverity.INFO)
            }
            return DiagnosticField(
                label = "Last app exit",
                value = record.summary(timeZone),
                severity = when {
                    record.killedWhileServiceInForeground || record.isCrash -> DiagnosticSeverity.ERROR
                    else -> DiagnosticSeverity.INFO
                }
            )
        }

        fun selectedRouteField(routeSelection: VpnRouteSelection?): DiagnosticField {
            return when (routeSelection) {
                VpnRouteSelection.Direct -> DiagnosticField(
                    label = "Selected route",
                    value = "Direct Connection",
                    severity = DiagnosticSeverity.OK
                )
                is VpnRouteSelection.Proxy -> selectedProxyField(routeSelection.proxy)
                null -> DiagnosticField(
                    label = "Selected route",
                    value = "none",
                    severity = DiagnosticSeverity.WARNING
                )
            }
        }
    }
}
