package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyCheckDao {
    @Query("SELECT * FROM proxy_checks")
    fun getAllChecks(): Flow<List<ProxyCheckEntity>>

    @Query("SELECT * FROM proxy_checks WHERE proxyId = :proxyId")
    suspend fun getCheck(proxyId: Long): ProxyCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: ProxyCheckEntity)

    @Query("DELETE FROM proxy_checks WHERE proxyId = :proxyId")
    suspend fun deleteCheck(proxyId: Long)
}
