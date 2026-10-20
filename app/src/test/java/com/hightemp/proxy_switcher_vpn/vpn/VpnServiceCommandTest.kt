package com.hightemp.proxy_switcher_vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceCommandTest {
    @Test
    fun appActionsMapToCommands() {
        assertEquals(VpnServiceCommand.START, VpnServiceCommand.fromAction(ProxyVpnService.ACTION_START))
        assertEquals(VpnServiceCommand.STOP, VpnServiceCommand.fromAction(ProxyVpnService.ACTION_STOP))
        assertEquals(
            VpnServiceCommand.SWITCH_ROUTE,
            VpnServiceCommand.fromAction(ProxyVpnService.ACTION_SWITCH_ROUTE)
        )
    }

    @Test
    fun systemVpnServiceActionIsAlwaysOnStart() {
        assertEquals(
            VpnServiceCommand.ALWAYS_ON_START,
            VpnServiceCommand.fromAction("android.net.VpnService")
        )
    }

    @Test
    fun missingOrUnknownActionIsStickyRestart() {
        assertEquals(VpnServiceCommand.STICKY_RESTART, VpnServiceCommand.fromAction(null))
        assertEquals(VpnServiceCommand.STICKY_RESTART, VpnServiceCommand.fromAction("other"))
    }
}
