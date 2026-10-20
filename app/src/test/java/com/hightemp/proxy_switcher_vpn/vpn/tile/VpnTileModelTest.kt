package com.hightemp.proxy_switcher_vpn.vpn.tile

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTileModelTest {
    private val route = UserText.raw("Primary:1080 (SOCKS5)")

    @Test
    fun runningVpnIsActiveAndShowsRoute() {
        val model = VpnTileModel.from(
            VpnRuntimeSnapshot(status = VpnServiceStatus.RUNNING, isForegroundServiceActive = true),
            route
        )

        assertTrue(model.active)
        assertEquals(route, model.subtitle)
    }

    @Test
    fun startingVpnIsActiveAndShowsStatus() {
        val model = VpnTileModel.from(
            VpnRuntimeSnapshot(status = VpnServiceStatus.STARTING, isForegroundServiceActive = true),
            route
        )

        assertTrue(model.active)
        assertEquals(UserText.of(R.string.status_starting), model.subtitle)
    }

    @Test
    fun stoppedVpnIsInactive() {
        val model = VpnTileModel.from(VpnRuntimeSnapshot(), route)

        assertFalse(model.active)
        assertEquals(UserText.of(R.string.tile_off), model.subtitle)
    }

    @Test
    fun failedVpnIsInactiveAndShowsError() {
        val model = VpnTileModel.from(
            VpnRuntimeSnapshot(status = VpnServiceStatus.ERROR, isForegroundServiceActive = false),
            route
        )

        assertFalse(model.active)
        assertEquals(UserText.of(R.string.status_error), model.subtitle)
    }

    @Test
    fun clickStopsActiveVpn() {
        assertEquals(
            VpnTileAction.STOP,
            VpnTileAction.forClick(vpnActive = true, permissionGranted = true)
        )
        assertEquals(
            VpnTileAction.STOP,
            VpnTileAction.forClick(vpnActive = true, permissionGranted = false)
        )
    }

    @Test
    fun clickStartsVpnWhenPermissionIsGranted() {
        assertEquals(
            VpnTileAction.START,
            VpnTileAction.forClick(vpnActive = false, permissionGranted = true)
        )
    }

    @Test
    fun clickOpensAppWhenPermissionIsMissing() {
        assertEquals(
            VpnTileAction.OPEN_APP,
            VpnTileAction.forClick(vpnActive = false, permissionGranted = false)
        )
    }
}
