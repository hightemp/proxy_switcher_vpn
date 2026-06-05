package com.hightemp.proxy_switcher_vpn

import android.app.Application
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.LibboxSetup
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProxySwitcherVpnApp : Application() {
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
    }
}
