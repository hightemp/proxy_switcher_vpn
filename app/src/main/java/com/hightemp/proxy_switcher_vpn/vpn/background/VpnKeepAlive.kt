package com.hightemp.proxy_switcher_vpn.vpn.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.ProxyVpnService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class VpnKeepAliveAction {
    RESTORE,
    RESCHEDULE,
    CANCEL
}

object VpnKeepAlivePolicy {
    fun decide(
        vpnEnabled: Boolean,
        serviceActive: Boolean,
        permissionGranted: Boolean
    ): VpnKeepAliveAction {
        return when {
            !vpnEnabled || !permissionGranted -> VpnKeepAliveAction.CANCEL
            serviceActive -> VpnKeepAliveAction.RESCHEDULE
            else -> VpnKeepAliveAction.RESTORE
        }
    }
}

/**
 * Restores the VPN after the system killed the app process. Android neither
 * restarts the sticky service nor the Always-on VPN reliably after a SIGKILL,
 * but a pending alarm survives the process and starts it again.
 */
object VpnKeepAlive {
    private const val INTERVAL_MILLIS = 60 * 1000L
    private const val REQUEST_CODE = 2001

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
                pendingIntent(context)
            )
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, VpnKeepAliveReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

@AndroidEntryPoint
class VpnKeepAliveReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val vpnEnabled = runCatching {
                    settingsRepository.settings.first().vpnEnabled
                }.getOrDefault(false)
                val action = VpnKeepAlivePolicy.decide(
                    vpnEnabled = vpnEnabled,
                    serviceActive = VpnRuntimeState.state.value.isForegroundServiceActive,
                    permissionGranted = VpnService.prepare(appContext) == null
                )
                when (action) {
                    VpnKeepAliveAction.CANCEL -> VpnKeepAlive.cancel(appContext)
                    VpnKeepAliveAction.RESCHEDULE -> VpnKeepAlive.schedule(appContext)
                    VpnKeepAliveAction.RESTORE -> {
                        restore(appContext)
                        VpnKeepAlive.schedule(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun restore(context: Context) {
        AppLogger.warning(
            message = "VPN was enabled but its service was not running; restoring it.",
            type = LogType.VPN
        )
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_START)
            )
        }.onFailure { error ->
            AppLogger.error(
                message = "VPN restore was not allowed by Android: ${error.javaClass.simpleName}. " +
                    "Allow the app to run in background.",
                type = LogType.VPN
            )
        }
    }
}
