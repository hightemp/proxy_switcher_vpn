package com.hightemp.proxy_switcher_vpn.vpn.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.hightemp.proxy_switcher_vpn.MainActivity
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.i18n.AppLanguages
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.resolve
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.ProxyVpnService
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile that starts and stops the VPN with the selected route. */
@AndroidEntryPoint
class VpnTileService : TileService() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var proxyRepository: ProxyRepository

    private var listeningScope: CoroutineScope? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                VpnRuntimeState.state.collect { runtime -> render(runtime) }
            }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Changing the VPN from the lock screen requires unlocking first.
        if (isLocked) {
            unlockAndRun { handleClick() }
        } else {
            handleClick()
        }
    }

    private fun handleClick() {
        val action = VpnTileAction.forClick(
            vpnActive = VpnRuntimeState.state.value.isForegroundServiceActive,
            permissionGranted = VpnService.prepare(this) == null
        )
        AppLogger.info(message = "Quick Settings tile action: $action.", type = LogType.VPN)
        when (action) {
            VpnTileAction.STOP -> startService(
                Intent(this, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_STOP)
            )
            VpnTileAction.START -> ContextCompat.startForegroundService(
                this,
                Intent(this, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_START)
            )
            VpnTileAction.OPEN_APP -> openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun render(runtime: VpnRuntimeSnapshot) {
        val model = VpnTileModel.from(runtime, selectedRouteText())
        val tile = qsTile ?: return
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_vpn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = model.subtitle.resolve(this)
        }
        tile.updateTile()
    }

    private suspend fun selectedRouteText(): UserText? {
        return runCatching {
            val proxyId = settingsRepository.settings.first().selectedProxyId
                ?: return VpnRouteSelection.Direct.displayText
            proxyRepository.getProxyById(proxyId)
                ?.let { proxy -> VpnRouteSelection.Proxy(proxy).displayText }
        }.getOrNull()
    }

    companion object {
        /** Asks the system to refresh the tile; safe to call when it is not added. */
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, VpnTileService::class.java))
            }
        }
    }
}
