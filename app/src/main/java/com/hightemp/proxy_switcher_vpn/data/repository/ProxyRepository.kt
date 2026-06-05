package com.hightemp.proxy_switcher_vpn.data.repository

import com.hightemp.proxy_switcher_vpn.data.local.ProxyDao
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProxyRepository @Inject constructor(
    private val proxyDao: ProxyDao
) {
    fun getAllProxies(): Flow<List<ProxyEntity>> = proxyDao.getAllProxies()

    suspend fun getProxyById(id: Long): ProxyEntity? = proxyDao.getProxyById(id)

    suspend fun insertProxy(proxy: ProxyEntity): Long = proxyDao.insertProxy(proxy)

    suspend fun updateProxy(proxy: ProxyEntity) = proxyDao.updateProxy(proxy)

    suspend fun deleteProxy(proxy: ProxyEntity) = proxyDao.deleteProxy(proxy)
}
