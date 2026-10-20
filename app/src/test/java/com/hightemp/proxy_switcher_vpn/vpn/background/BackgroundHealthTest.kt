package com.hightemp.proxy_switcher_vpn.vpn.background

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

        assertEquals("optimized", optimized.value)
        assertEquals(DiagnosticSeverity.WARNING, optimized.severity)
        assertEquals("unrestricted", unrestricted.value)
        assertEquals(DiagnosticSeverity.OK, unrestricted.severity)
        assertEquals("unknown", unknown.value)
    }

    @Test
    fun backgroundRestrictionFieldIsErrorWhenRestricted() {
        val restricted = VpnDiagnostics.backgroundRestrictionField(
            BackgroundRestrictionStatus(backgroundRestricted = true)
        )
        val allowed = VpnDiagnostics.backgroundRestrictionField(
            BackgroundRestrictionStatus(backgroundRestricted = false)
        )

        assertEquals("restricted", restricted.value)
        assertEquals(DiagnosticSeverity.ERROR, restricted.severity)
        assertEquals("allowed", allowed.value)
        assertEquals(DiagnosticSeverity.OK, allowed.severity)
    }

    @Test
    fun alwaysOnFieldReportsLockdownAndUnknown() {
        assertEquals(
            "on, lockdown",
            VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = true, lockdown = true)).value
        )
        assertEquals(
            "on",
            VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = true, lockdown = false)).value
        )
        val off = VpnDiagnostics.alwaysOnField(AlwaysOnVpnStatus(alwaysOn = false, lockdown = false))
        assertEquals("off", off.value)
        assertEquals(DiagnosticSeverity.INFO, off.severity)
        assertEquals(
            "unknown until VPN runs",
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
            "2025-10-18 11:47 · fail-closed: Selected upstream proxy did not recover.",
            field.value
        )
        assertEquals(DiagnosticSeverity.ERROR, field.severity)
        assertEquals("none", VpnDiagnostics.lastStopField(null, utc).value)
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
        assertEquals("1970-01-01 00:00 · stopped by user: VPN service stopped.", field.value)
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

        assertEquals("optimized", diagnostics.batteryOptimization.value)
        assertEquals("allowed", diagnostics.backgroundRestriction.value)
        assertEquals("on", diagnostics.alwaysOnVpn.value)
        assertTrue(diagnostics.lastVpnStop.value.contains("permission revoked"))
        assertTrue(diagnostics.lastProcessExit.value.contains("killed by system"))
        assertEquals(DiagnosticSeverity.ERROR, diagnostics.lastProcessExit.severity)
    }
}
