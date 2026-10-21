package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Last check results of one proxy; exit IP is the proxy's public address, not user data. */
@Entity(tableName = "proxy_checks")
data class ProxyCheckEntity(
    @PrimaryKey val proxyId: Long,
    val checkedAtMillis: Long? = null,
    val success: Boolean? = null,
    val latencyMillis: Long? = null,
    val exitIp: String? = null,
    val countryCode: String? = null,
    val downloadBitsPerSecond: Long? = null,
    val speedCheckedAtMillis: Long? = null
)
