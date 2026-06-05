package com.hightemp.proxy_switcher_vpn.vpn.engine

import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.SystemProxyStatus

class LibboxCommandServerHandler(
    private val onServiceStop: () -> Unit,
    private val onServiceReload: () -> Unit,
    private val onDebugMessage: (String) -> Unit
) : CommandServerHandler {
    override fun connectSSHAgent(): Int {
        error("SSH agent is not supported.")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus {
        return SystemProxyStatus().apply {
            available = false
            enabled = false
        }
    }

    override fun serviceReload() {
        onServiceReload()
    }

    override fun serviceStop() {
        onServiceStop()
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit

    override fun triggerNativeCrash() {
        error("Native crash trigger is not supported.")
    }

    override fun writeDebugMessage(message: String) {
        onDebugMessage(message)
    }
}
