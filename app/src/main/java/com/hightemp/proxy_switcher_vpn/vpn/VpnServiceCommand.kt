package com.hightemp.proxy_switcher_vpn.vpn

/**
 * What a [ProxyVpnService] start request asks for. The system starts the
 * service with [SYSTEM_VPN_SERVICE_ACTION] for Always-on VPN (after boot or
 * after the app process died) and with no intent for a sticky restart.
 */
enum class VpnServiceCommand {
    START,
    STOP,
    SWITCH_ROUTE,
    ALWAYS_ON_START,
    STICKY_RESTART;

    companion object {
        /** Value of `android.net.VpnService.SERVICE_INTERFACE`. */
        const val SYSTEM_VPN_SERVICE_ACTION = "android.net.VpnService"

        fun fromAction(action: String?): VpnServiceCommand {
            return when (action) {
                ProxyVpnService.ACTION_START -> START
                ProxyVpnService.ACTION_STOP -> STOP
                ProxyVpnService.ACTION_SWITCH_ROUTE -> SWITCH_ROUTE
                SYSTEM_VPN_SERVICE_ACTION -> ALWAYS_ON_START
                else -> STICKY_RESTART
            }
        }
    }
}
