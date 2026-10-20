package com.hightemp.proxy_switcher_vpn.service

import com.hightemp.proxy_switcher_vpn.i18n.UserText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VpnRuntimeStateTest {
    @Test
    fun failedStoppedUsesErrorStateAndLastError() {
        VpnRuntimeState.markFailedStopped(UserText.raw("Selected upstream proxy failed."))

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.ERROR, snapshot.status)
        assertFalse(snapshot.isForegroundServiceActive)
        assertFalse(snapshot.isRunning)
        assertEquals(UserText.raw("Selected upstream proxy failed."), snapshot.lastError)
    }

    @Test
    fun startingClearsPreviousError() {
        VpnRuntimeState.markFailedStopped(UserText.raw("Previous failure."))

        VpnRuntimeState.markStarting(UserText.raw("Starting VPN foreground service."))

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.STARTING, snapshot.status)
        assertNull(snapshot.lastError)
    }

    @Test
    fun stoppedClearsPreviousError() {
        VpnRuntimeState.markFailedStopped(UserText.raw("Previous failure."))

        VpnRuntimeState.markStopped(UserText.raw("VPN foreground service stopped."))

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.STOPPED, snapshot.status)
        assertNull(snapshot.lastError)
    }
}
