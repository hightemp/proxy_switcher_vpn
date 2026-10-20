package com.hightemp.proxy_switcher_vpn.vpn.tile

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeSnapshot
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus

/** What the Quick Settings tile shows for the current VPN runtime state. */
data class VpnTileModel(
    val active: Boolean,
    val subtitle: UserText
) {
    companion object {
        fun from(runtime: VpnRuntimeSnapshot, route: UserText?): VpnTileModel {
            val subtitle = when (runtime.status) {
                VpnServiceStatus.RUNNING -> route ?: UserText.of(R.string.status_running)
                VpnServiceStatus.STARTING -> UserText.of(R.string.status_starting)
                VpnServiceStatus.STOPPING -> UserText.of(R.string.status_stopping)
                VpnServiceStatus.ERROR -> UserText.of(R.string.status_error)
                VpnServiceStatus.STOPPED -> UserText.of(R.string.tile_off)
            }
            return VpnTileModel(active = runtime.isForegroundServiceActive, subtitle = subtitle)
        }
    }
}

enum class VpnTileAction {
    START,
    STOP,
    OPEN_APP;

    companion object {
        /** The VPN permission dialog needs an activity, so a missing permission opens the app. */
        fun forClick(vpnActive: Boolean, permissionGranted: Boolean): VpnTileAction {
            return when {
                vpnActive -> STOP
                permissionGranted -> START
                else -> OPEN_APP
            }
        }
    }
}
