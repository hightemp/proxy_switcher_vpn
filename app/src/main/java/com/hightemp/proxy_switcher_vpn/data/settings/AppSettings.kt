package com.hightemp.proxy_switcher_vpn.data.settings

data class AppSettings(
    val selectedProxyId: Long? = null,
    val domainDestinationLoggingEnabled: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false,
    val vpnEnabled: Boolean = false,
    val infiniteReconnectEnabled: Boolean = true,
    /** Proxy list view: sort and group filter names, decoded by the UI. */
    val proxyListSort: String? = null,
    val proxyListGroup: String? = null
)
