package com.hightemp.proxy_switcher_vpn.vpn.diagnostics

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.english
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
    val label: UserText,
    val value: UserText,
    val severity: DiagnosticSeverity
)

data class VpnDiagnostics(
    val vpnPermission: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_vpn_permission),
        value = UserText.raw("unknown"),
        severity = DiagnosticSeverity.WARNING
    ),
    val foregroundService: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_foreground_service),
        value = UserText.raw("stopped"),
        severity = DiagnosticSeverity.INFO
    ),
    val singBoxCore: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_singbox),
        value = UserText.raw("libbox_stopped"),
        severity = DiagnosticSeverity.INFO
    ),
    val tunInterface: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_tun),
        value = UserText.raw("not_created"),
        severity = DiagnosticSeverity.INFO
    ),
    val dns: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_dns),
        value = UserText.raw("proxy_safe_doh_private_dns_blocked"),
        severity = DiagnosticSeverity.OK
    ),
    val ipv4Route: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_ipv4),
        value = UserText.raw("enabled"),
        severity = DiagnosticSeverity.OK
    ),
    val ipv6: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_ipv6),
        value = UserText.raw("unsupported_disabled"),
        severity = DiagnosticSeverity.WARNING
    ),
    val udp: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_udp),
        value = UserText.raw(UdpPolicy.mvpDefault().diagnosticSummary),
        severity = DiagnosticSeverity.WARNING
    ),
    val selectedProxy: DiagnosticField = DiagnosticField(
        label = UserText.of(R.string.diag_selected_route),
        value = UserText.of(R.string.diag_none),
        severity = DiagnosticSeverity.WARNING
    ),
    val batteryOptimization: DiagnosticField = batteryOptimizationField(BackgroundRestrictionStatus()),
    val backgroundRestriction: DiagnosticField = backgroundRestrictionField(BackgroundRestrictionStatus()),
    val alwaysOnVpn: DiagnosticField = alwaysOnField(AlwaysOnVpnStatus()),
    val lastVpnStop: DiagnosticField = lastStopField(null),
    val lastProcessExit: DiagnosticField = lastProcessExitField(null),
    val processMemory: DiagnosticField = DiagnosticField(
        UserText.of(R.string.diag_process_memory),
        UserText.of(R.string.diag_not_available),
        DiagnosticSeverity.INFO
    ),
    val udpPolicy: UdpPolicy = UdpPolicy.mvpDefault(),
    val counters: VpnEngineCounters = VpnEngineCounters(),
    val trafficStatsAvailability: StatsAvailability = StatsAvailability.NOT_SUPPORTED,
    val activeConnectionStatsAvailability: StatsAvailability = StatsAvailability.NOT_SUPPORTED,
    val lastError: UserText? = null,
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

    /** English label/value pairs for exported bug reports; not redacted here. */
    fun exportFields(): List<Pair<String, String>> {
        val fields = listOf(
            vpnPermission, foregroundService, singBoxCore, tunInterface, processMemory,
            batteryOptimization, backgroundRestriction, alwaysOnVpn, lastVpnStop, lastProcessExit,
            dns, ipv4Route, ipv6, udp, selectedProxy
        ).map { field -> field.label.english() to field.value.english() }
        return fields + listOf(
            "UDP/443" to udp443Status,
            "Non-DNS UDP" to nonDnsUdpStatus,
            "Counters" to with(counters) {
                "in=$bytesIn B, out=$bytesOut B, active=$activeConnections, total=$totalConnections, " +
                    "failed=$failedConnections, dns=$dnsQueries, blockedUdp=$blockedUdp, bypassedUdp=$bypassedUdp"
            },
            "Traffic stats" to trafficStatsStatus,
            "Last error" to (lastError?.english() ?: "none"),
            "Config preview" to (maskedConfigPreview?.let { "\n$it" } ?: "none")
        )
    }

    companion object {
        fun fromStats(
            stats: VpnStats,
            udpPolicy: UdpPolicy = UdpPolicy.mvpDefault(),
            maskedConfigPreview: String? = null
        ): VpnDiagnostics {
            return VpnDiagnostics(
                udp = DiagnosticField(
                    label = UserText.of(R.string.diag_udp),
                    value = UserText.raw(udpPolicy.diagnosticSummary),
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
                lastError = stats.lastError?.let(UserText::raw),
                processMemory = processMemoryField(stats),
                maskedConfigPreview = maskedConfigPreview
            )
        }

        fun permissionField(status: VpnPermissionDiagnosticStatus): DiagnosticField {
            return when (status) {
                VpnPermissionDiagnosticStatus.GRANTED -> DiagnosticField(
                    label = UserText.of(R.string.diag_vpn_permission),
                    value = UserText.raw("granted"),
                    severity = DiagnosticSeverity.OK
                )
                VpnPermissionDiagnosticStatus.REQUESTING -> DiagnosticField(
                    label = UserText.of(R.string.diag_vpn_permission),
                    value = UserText.raw("requesting"),
                    severity = DiagnosticSeverity.INFO
                )
                VpnPermissionDiagnosticStatus.REQUIRED -> DiagnosticField(
                    label = UserText.of(R.string.diag_vpn_permission),
                    value = UserText.raw("required"),
                    severity = DiagnosticSeverity.WARNING
                )
                VpnPermissionDiagnosticStatus.DENIED -> DiagnosticField(
                    label = UserText.of(R.string.diag_vpn_permission),
                    value = UserText.raw("denied"),
                    severity = DiagnosticSeverity.ERROR
                )
                VpnPermissionDiagnosticStatus.UNKNOWN -> DiagnosticField(
                    label = UserText.of(R.string.diag_vpn_permission),
                    value = UserText.raw("unknown"),
                    severity = DiagnosticSeverity.WARNING
                )
            }
        }

        fun foregroundServiceField(status: VpnServiceStatus): DiagnosticField {
            return DiagnosticField(
                label = UserText.of(R.string.diag_foreground_service),
                value = UserText.raw(status.name.lowercase()),
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
                label = UserText.of(R.string.diag_singbox),
                value = UserText.raw("libbox_${status.name.lowercase()}"),
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
                label = UserText.of(R.string.diag_tun),
                value = UserText.raw(
                    when (status) {
                        VpnServiceStatus.RUNNING -> "active"
                        VpnServiceStatus.STARTING -> "creating"
                        VpnServiceStatus.STOPPING -> "closing"
                        VpnServiceStatus.STOPPED -> "not_created"
                        VpnServiceStatus.ERROR -> "error"
                    }
                ),
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
                    label = UserText.of(R.string.diag_selected_route),
                    value = UserText.of(R.string.diag_none),
                    severity = DiagnosticSeverity.WARNING
                )
            } else {
                DiagnosticField(
                    label = UserText.of(R.string.diag_selected_route),
                    value = UserText.raw("${proxy.label ?: proxy.host}:${proxy.port} (${proxy.type})"),
                    severity = if (proxy.isEnabled) {
                        DiagnosticSeverity.OK
                    } else {
                        DiagnosticSeverity.WARNING
                    }
                )
            }
        }

        fun processMemoryField(stats: VpnStats): DiagnosticField {
            val label = UserText.of(R.string.diag_process_memory)
            val memory = stats.processMemoryBytes
                ?: return DiagnosticField(label, UserText.of(R.string.diag_not_available), DiagnosticSeverity.INFO)
            val mebibytes = String.format(Locale.US, "%.1f", memory / (1024.0 * 1024.0))
            val value = stats.goroutines
                ?.let { UserText.of(R.string.diag_process_memory_goroutines, mebibytes, it) }
                ?: UserText.of(R.string.diag_process_memory_value, mebibytes)
            return DiagnosticField(label, value, DiagnosticSeverity.INFO)
        }

        fun batteryOptimizationField(status: BackgroundRestrictionStatus): DiagnosticField {
            val label = UserText.of(R.string.diag_battery_optimization)
            return when (status.batteryOptimizationIgnored) {
                true -> DiagnosticField(label, UserText.of(R.string.diag_unrestricted), DiagnosticSeverity.OK)
                false -> DiagnosticField(label, UserText.of(R.string.diag_optimized), DiagnosticSeverity.WARNING)
                null -> DiagnosticField(label, UserText.of(R.string.diag_unknown), DiagnosticSeverity.INFO)
            }
        }

        fun backgroundRestrictionField(status: BackgroundRestrictionStatus): DiagnosticField {
            val label = UserText.of(R.string.diag_background_activity)
            return when (status.backgroundRestricted) {
                true -> DiagnosticField(label, UserText.of(R.string.diag_restricted), DiagnosticSeverity.ERROR)
                false -> DiagnosticField(label, UserText.of(R.string.diag_allowed), DiagnosticSeverity.OK)
                null -> DiagnosticField(label, UserText.of(R.string.diag_unknown), DiagnosticSeverity.INFO)
            }
        }

        fun alwaysOnField(status: AlwaysOnVpnStatus): DiagnosticField {
            val value = when (status.alwaysOn) {
                true -> if (status.lockdown == true) R.string.diag_always_on_lockdown else R.string.diag_on
                false -> R.string.diag_off
                null -> R.string.diag_always_on_unknown
            }
            return DiagnosticField(
                label = UserText.of(R.string.settings_always_on),
                value = UserText.of(value),
                severity = if (status.alwaysOn == true) DiagnosticSeverity.OK else DiagnosticSeverity.INFO
            )
        }

        /** The stop message is stored in English for bug reports; the reason is translated. */
        fun lastStopField(
            record: VpnStopRecord?,
            timeZone: TimeZone = TimeZone.getDefault()
        ): DiagnosticField {
            val label = UserText.of(R.string.diag_last_vpn_stop)
            if (record == null) {
                return DiagnosticField(label, UserText.of(R.string.diag_none), DiagnosticSeverity.INFO)
            }
            val time = formatDiagnosticTime(record.timestampMillis, timeZone)
            val reason = UserText.of(record.reason.label)
            val value = if (record.message.isBlank()) {
                UserText.of(R.string.diag_time_and_text, time, reason)
            } else {
                UserText.of(R.string.diag_time_reason_message, time, reason, record.message)
            }
            return DiagnosticField(
                label = label,
                value = value,
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
            val label = UserText.of(R.string.diag_last_app_exit)
            if (record == null) {
                return DiagnosticField(label, UserText.of(R.string.diag_none), DiagnosticSeverity.INFO)
            }
            return DiagnosticField(
                label = label,
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
                    label = UserText.of(R.string.diag_selected_route),
                    value = UserText.of(R.string.route_direct),
                    severity = DiagnosticSeverity.OK
                )
                is VpnRouteSelection.Proxy -> selectedProxyField(routeSelection.proxy)
                null -> DiagnosticField(
                    label = UserText.of(R.string.diag_selected_route),
                    value = UserText.of(R.string.diag_none),
                    severity = DiagnosticSeverity.WARNING
                )
            }
        }
    }
}
