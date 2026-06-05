package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProxyEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao
}
