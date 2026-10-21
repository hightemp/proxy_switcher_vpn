package com.hightemp.proxy_switcher_vpn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProxyEntity::class, ProxyCheckEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao

    abstract fun proxyCheckDao(): ProxyCheckDao

    companion object {
        /** Adds stored proxy check results. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_PROXY_CHECKS_V2)
            }
        }

        /** Adds the optional proxy group. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ADD_PROXY_GROUP_V3)
            }
        }

        const val ADD_PROXY_GROUP_V3 = "ALTER TABLE `proxies` ADD COLUMN `groupName` TEXT"

        /** Matches `app/schemas/.../2.json`; Room validates it when the database opens. */
        const val CREATE_PROXY_CHECKS_V2 = "CREATE TABLE IF NOT EXISTS `proxy_checks` (" +
            "`proxyId` INTEGER NOT NULL, `checkedAtMillis` INTEGER, `success` INTEGER, " +
            "`latencyMillis` INTEGER, `exitIp` TEXT, `countryCode` TEXT, " +
            "`downloadBitsPerSecond` INTEGER, `speedCheckedAtMillis` INTEGER, PRIMARY KEY(`proxyId`))"
    }
}
