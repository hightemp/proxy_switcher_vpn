package com.hightemp.proxy_switcher_vpn.vpn.routing

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.i18n.UserText

sealed interface VpnRouteSelection {
    /** English label for logs. */
    val displayLabel: String

    /** Label shown to the user. */
    val displayText: UserText

    data object Direct : VpnRouteSelection {
        override val displayLabel: String = "Direct Connection"
        override val displayText: UserText = UserText.of(R.string.route_direct)
    }

    data class Proxy(val proxy: ProxyEntity) : VpnRouteSelection {
        override val displayLabel: String =
            "${proxy.label ?: proxy.host}:${proxy.port} (${proxy.type})"
        override val displayText: UserText = UserText.raw(displayLabel)
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
