package com.hightemp.proxy_switcher_vpn.vpn.background

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.DiagnosticSeverity
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnostics
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnDiagnosticsRepository
import com.hightemp.proxy_switcher_vpn.vpn.diagnostics.VpnPermissionDiagnosticStatus
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStats
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundHealthTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun optimizedOrRestrictedAppNeedsAttention() {
        assertTrue(BackgroundRestrictionStatus(batteryOptimizationIgnored = false).needsAttention)
        assertTrue(
            BackgroundRestrictionStatus(
                batteryOptimizationIgnored = true,
                backgroundRestricted = true
            ).needsAttention
        )
        assertFalse(
            BackgroundRestrictionStatus(
                batteryOptimizationIgnored = true,
                backgroundRestricted = false
            ).needsAttention
        )
        assertFalse(BackgroundRestrictionStatus().needsAttention)
    }

    @Test
    fun batteryOptimizationFieldWarnsWhenOptimized() {
        val optimized = VpnDiagnostics.batteryOptimizationField(
            BackgroundRestrictionStatus(batteryOptimizationIgnored = false)
        )
        val unrestricted = VpnDiagnostics.batteryOptimizationField(
            BackgroundRestrictionStatus(batteryOptimizationIgnored = true)
        )
        val unknown = VpnDiagnostics.batteryOptimizationField(BackgroundRestrictionStatus())

        assertEquals(UserText.of(R.string.diag_optimized), optimized.value)
        assertEquals(DiagnosticSeverity.WARNING, optimized.severity)
        assertEquals(UserText.of(R.string.diag_unrestricted), unrestricted.value)
        assertEquals(DiagnosticSeverity.OK, unrestricted.severity)
        assertEquals(UserText.of(R.string.diag_unknown), unknown.value)
    }

    @Test
    fun backgroundRestrictionFieldIsErrorWhenRestricted() {
        val restricted = VpnDiagnostics.backgroundRestrictionField(
            BackgroundRestrictionStatus(backgroundRestricted = true)
        )
        val allowed = VpnDiagnostics.backgroundRestrictionField(
            BackgroundRestrictionStatus(backgroundRestricted = false)
        )

        assertEquals(UserText.of(R.string.diag_restricted), restricted.value)
        assertEquals(DiagnosticSeverity.ERROR, restricted.severity)
        assertEquals(UserText.of(R.string.diag_allowed), allowed.value)
        assertEquals(DiagnosticSeverity.OK, allowed.severity)
    }

    @Test
    fun alwaysOnFieldReportsLockdownAndUnknown() {
        assertEquals(
            UserText.of(R.string.diag_always_on_lockdown),
            VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = true, lockdown = true)).value
        )
        assertEquals(
            UserText.of(R.string.diag_on),
            VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = true, lockdown = false)).value
        )
        val off = VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = false, lockdown = false))
        assertEquals(UserText.of(R.string.diag_off), off.value)
        assertEquals(DiagnosticSeverity.INFO, off.severity)
        assertEquals(
            UserText.of(R.string.diag_always_on_unknown),
            VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus()).value
        )
    }

    @Test
    fun lastStopFieldShowsTimeReasonAndMessage() {
        val field = VpnDiagnostics.lastStopField(
            VpnStopRecord(
                timestampMillis = 1_760_788_067_000L,
                reason = VpnStopReason.FAIL_CLOSED,
                message = "Selected upstream proxy did not recover."
            ),
            timeZone = utc
        )

        assertEquals(
            UserText.of(
                R.string.diag_time_reason_message,
                "2025-10-18 11:47",
                UserText.of(R.string.stop_reason_fail_closed),
                "Selected upstream proxy did not recover."
            ),
            field.value
        )
        assertEquals(DiagnosticSeverity.ERROR, field.severity)
        assertEquals(UserText.of(R.string.diag_none), VpnDiagnostics.lastStopField(null, utc).value)
    }

    @Test
    fun userStopIsInformational() {
        val field = VpnDiagnostics.lastStopField(
            VpnStopRecord(
                timestampMillis = 0L,
                reason = VpnStopReason.USER,
                message = "VPN service stopped."
            ),
            timeZone = utc
        )

        assertEquals(DiagnosticSeverity.INFO, field.severity)
        assertEquals(
            UserText.of(
                R.string.diag_time_reason_message,
                "1970-01-01 00:00",
                UserText.of(R.string.stop_reason_user),
                "VPN service stopped."
            ),
            field.value
        )
    }

    @Test
    fun diagnosticsRepositoryIncludesBackgroundHealth() {
        val diagnostics = VpnDiagnosticsRepository().buildDiagnostics(
            permission = VpnPermissionDiagnosticStatus.GRANTED,
            runtime = VpnRuntimeSnapshot(),
            routeSelection = VpnRouteSelection.Direct,
            stats = VpnStats(),
            backgroundHealth = BackgroundHealth(
                restrictions = BackgroundRestrictionStatus(
                    batteryOptimizationIgnored = false,
                    backgroundRestricted = false
                ),
                alwaysOn = AlwaysOnVpnStatus(alwaysOn = true, lockdown = false),
                lastStop = VpnStopRecord(0L, VpnStopReason.PERMISSION_REVOKED, "VPN permission was revoked."),
                lastProcessExit = ProcessExitRecord(
                    timestampMillis = 0L,
                    reason = ProcessExitRecord.REASON_SIGNALED,
                    status = 9,
                    importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
                )
            )
        )

        assertEquals(UserText.of(R.string.diag_optimized), diagnostics.batteryOptimization.value)
        assertEquals(UserText.of(R.string.diag_allowed), diagnostics.backgroundRestriction.value)
        assertEquals(UserText.of(R.string.diag_on), diagnostics.alwaysOnVpn.value)
        assertEquals(
            UserText.of(R.string.stop_reason_permission_revoked),
            (diagnostics.lastVpnStop.value as UserText.Res).args[1]
        )
        assertEquals(
            UserText.of(R.string.exit_signaled, 9),
            (diagnostics.lastProcessExit.value as UserText.Res).args[1]
        )
        assertEquals(DiagnosticSeverity.ERROR, diagnostics.lastProcessExit.severity)
    }
}
