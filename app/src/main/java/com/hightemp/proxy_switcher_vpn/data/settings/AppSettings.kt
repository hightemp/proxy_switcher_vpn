package com.hightemp.proxy_switcher_vpn.data.settings

data class AppSettings(
    val selectedProxyId: Long? = null,
    val domainDestinationLoggingEnabled: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false
)
