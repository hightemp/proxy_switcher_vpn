package com.hightemp.proxy_switcher_vpn.data.repository

import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ProxyCheckRepository @Inject constructor(
    private val proxyCheckDao: ProxyCheckDao
) {
    fun getAllChecks(): Flow<List<ProxyCheckEntity>> = proxyCheckDao.getAllChecks()

    /** Stores a reachability check and keeps the last speed result. */
    suspend fun recordCheck(
        proxyId: Long,
        checkedAtMillis: Long,
        success: Boolean,
        latencyMillis: Long?,
        exitIp: String?,
        countryCode: String?
    ) {
        val previous = proxyCheckDao.getCheck(proxyId) ?: ProxyCheckEntity(proxyId)
        proxyCheckDao.upsertCheck(
            previous.copy(
                checkedAtMillis = checkedAtMillis,
                success = success,
                latencyMillis = latencyMillis,
                exitIp = exitIp ?: previous.exitIp.takeIf { success },
                countryCode = countryCode ?: previous.countryCode.takeIf { success }
            )
        )
    }

    suspend fun recordSpeed(proxyId: Long, checkedAtMillis: Long, downloadBitsPerSecond: Long) {
        val previous = proxyCheckDao.getCheck(proxyId) ?: ProxyCheckEntity(proxyId)
        proxyCheckDao.upsertCheck(
            previous.copy(downloadBitsPerSecond = downloadBitsPerSecond, speedCheckedAtMillis = checkedAtMillis)
        )
    }

    suspend fun deleteCheck(proxyId: Long) = proxyCheckDao.deleteCheck(proxyId)
}
