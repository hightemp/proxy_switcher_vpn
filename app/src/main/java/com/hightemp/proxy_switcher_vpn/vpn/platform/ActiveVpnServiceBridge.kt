package com.hightemp.proxy_switcher_vpn.vpn.platform

import io.nekohasekai.libbox.TunOptions
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveVpnServiceBridge @Inject constructor() {
    @Volatile
    private var activeService: ActiveVpnService? = null

    fun attach(service: ActiveVpnService) {
        activeService = service
    }

    fun detach(service: ActiveVpnService) {
        if (activeService === service) {
            activeService = null
        }
    }

    fun protectSocket(fd: Int) {
        val service = activeService ?: error("VPN service is not active.")
        if (!service.protectSocket(fd)) {
            error("Failed to protect libbox outbound socket.")
        }
    }

    fun protectSocketIfActive(socket: Socket): Boolean {
        return activeService?.protectSocket(socket) ?: false
    }

    fun openTun(options: TunOptions): Int {
        val service = activeService ?: error("VPN service is not active.")
        return service.openTun(options)
    }

    fun closeTun() {
        activeService?.closeTun()
    }

    fun failClosedFromEngine(message: String) {
        activeService?.failClosedFromEngine(message)
    }
}

interface ActiveVpnService {
    fun protectSocket(fd: Int): Boolean

    fun protectSocket(socket: Socket): Boolean

    fun openTun(options: TunOptions): Int

    fun closeTun()

    fun failClosedFromEngine(message: String)
}
