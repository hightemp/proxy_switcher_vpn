package com.hightemp.proxy_switcher_vpn.vpn.routing

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity

sealed interface VpnRouteSelection {
    val displayLabel: String

    data object Direct : VpnRouteSelection {
        override val displayLabel: String = "Direct Connection"
    }

    data class Proxy(val proxy: ProxyEntity) : VpnRouteSelection {
        override val displayLabel: String =
            "${proxy.label ?: proxy.host}:${proxy.port} (${proxy.type})"
    }
}

fun VpnRouteSelection?.isValidForStart(): Boolean {
    return when (this) {
        VpnRouteSelection.Direct -> true
        is VpnRouteSelection.Proxy -> proxy.isEnabled &&
            proxy.host.isNotBlank() &&
            proxy.port in 1..65535
        null -> false
    }
}

fun VpnRouteSelection.proxyOrNull(): ProxyEntity? {
    return (this as? VpnRouteSelection.Proxy)?.proxy
}

fun VpnRouteSelection.sensitiveValues(): List<String> {
    return proxyOrNull()
        ?.let { proxy -> listOfNotNull(proxy.username, proxy.password) }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
}
