package com.hightemp.proxy_switcher_vpn.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VpnRuntimeStateTest {
    @Test
    fun failedStoppedUsesErrorStateAndLastError() {
        VpnRuntimeState.markFailedStopped("Selected upstream proxy failed.")

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.ERROR, snapshot.status)
        assertFalse(snapshot.isForegroundServiceActive)
        assertFalse(snapshot.isRunning)
        assertEquals("Selected upstream proxy failed.", snapshot.lastError)
    }

    @Test
    fun startingClearsPreviousError() {
        VpnRuntimeState.markFailedStopped("Previous failure.")

        VpnRuntimeState.markStarting("Starting VPN foreground service.")

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.STARTING, snapshot.status)
        assertNull(snapshot.lastError)
    }

    @Test
    fun stoppedClearsPreviousError() {
        VpnRuntimeState.markFailedStopped("Previous failure.")

        VpnRuntimeState.markStopped("VPN foreground service stopped.")

        val snapshot = VpnRuntimeState.state.value

        assertEquals(VpnServiceStatus.STOPPED, snapshot.status)
        assertNull(snapshot.lastError)
    }
}
