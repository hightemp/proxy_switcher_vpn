package com.hightemp.proxy_switcher_vpn.vpn.background

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnKeepAlivePolicyTest {
    @Test
    fun restoresEnabledVpnWhenServiceIsGone() {
        assertEquals(
            VpnKeepAliveAction.RESTORE,
            VpnKeepAlivePolicy.decide(vpnEnabled = true, serviceActive = false, permissionGranted = true)
        )
    }

    @Test
    fun keepsCheckingWhileVpnRuns() {
        assertEquals(
            VpnKeepAliveAction.RESCHEDULE,
            VpnKeepAlivePolicy.decide(vpnEnabled = true, serviceActive = true, permissionGranted = true)
        )
    }

    @Test
    fun stopsWhenUserOrFailClosedDisabledVpn() {
        assertEquals(
            VpnKeepAliveAction.CANCEL,
            VpnKeepAlivePolicy.decide(vpnEnabled = false, serviceActive = false, permissionGranted = true)
        )
    }

    @Test
    fun stopsWhenVpnPermissionWasRevoked() {
        assertEquals(
            VpnKeepAliveAction.CANCEL,
            VpnKeepAlivePolicy.decide(vpnEnabled = true, serviceActive = false, permissionGranted = false)
        )
    }
}
