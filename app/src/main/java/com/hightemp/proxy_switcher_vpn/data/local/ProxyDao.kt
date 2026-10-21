package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY id DESC")
    fun getAllProxies(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies WHERE id = :id")
    suspend fun getProxyById(id: Long): ProxyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyEntity): Long

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Delete
    suspend fun deleteProxy(proxy: ProxyEntity)

    @Insert
    suspend fun insertProxies(proxies: List<ProxyEntity>): List<Long>

    @Update
    suspend fun updateProxies(proxies: List<ProxyEntity>)

    /** Saves a bulk import in one transaction, so the list refreshes once. */
    @Transaction
    suspend fun saveImported(newProxies: List<ProxyEntity>, updatedProxies: List<ProxyEntity>) {
        insertProxies(newProxies)
        updateProxies(updatedProxies)
    }
}
