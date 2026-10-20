package com.hightemp.proxy_switcher_vpn

import android.app.Application
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitHistory
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitRecord
import com.hightemp.proxy_switcher_vpn.vpn.background.VpnStopHistory
import com.hightemp.proxy_switcher_vpn.vpn.engine.LibboxSetup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class ProxySwitcherVpnApp : Application() {
    @Inject lateinit var processExitHistory: ProcessExitHistory
    @Inject lateinit var stopHistory: VpnStopHistory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            LibboxSetup.ensureInitialized(this)
        }.onFailure {
            AppLogger.error(
                message = "sing-box core setup failed during app startup.",
                type = LogType.VPN
            )
        }
        logPreviousProcessExit()
    }

    /** Logs the previous process exit once, so system kills show up in Logs. */
    private fun logPreviousProcessExit() {
        appScope.launch {
            runCatching {
                val exit = ProcessExitRecord.unseenLatest(
                    records = processExitHistory.recentExits(),
                    lastSeenMillis = stopHistory.lastSeenProcessExitMillis.first()
                ) ?: return@launch
                stopHistory.markProcessExitSeen(exit.timestampMillis)
                val message = "Previous app process exit: ${exit.summary()}."
                if (exit.killedWhileServiceInForeground || exit.isCrash) {
                    AppLogger.warning(message = message, type = LogType.VPN)
                } else {
                    AppLogger.info(message = message, type = LogType.VPN)
                }
            }
        }
    }
}
