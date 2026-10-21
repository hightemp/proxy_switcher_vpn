package com.hightemp.proxy_switcher_vpn.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseSchemaTest {
    /** The hand-written v1 → v2 migration must create exactly the table Room expects. */
    @Test
    fun migrationSqlMatchesExportedSchema() {
        val schema = schema(version = 2)
        val createSql = Regex("\"tableName\": \"proxy_checks\",\\s*\"createSql\": \"([^\"]+)\"")
            .find(schema)!!.groupValues[1]
            .replace("`\${TABLE_NAME}`", "`proxy_checks`")

        assertEquals(createSql, AppDatabase.CREATE_PROXY_CHECKS_V2)
    }

    /** v2 → v3 adds a nullable TEXT column; v3 `proxies` must be v2 plus exactly that column. */
    @Test
    fun groupColumnMigrationMatchesExportedSchema() {
        val v2 = proxiesCreateSql(schema(version = 2))
        val v3 = proxiesCreateSql(schema(version = 3))

        assertEquals(v2.removeSuffix(")") + ", `groupName` TEXT)", v3)
        assertEquals("ALTER TABLE `proxies` ADD COLUMN `groupName` TEXT", AppDatabase.ADD_PROXY_GROUP_V3)
    }

    private fun proxiesCreateSql(schema: String): String {
        return Regex("\"tableName\": \"proxies\",\\s*\"createSql\": \"([^\"]+)\"")
            .find(schema)!!.groupValues[1]
    }

    private fun schema(version: Int): String {
        val relative = "schemas/com.hightemp.proxy_switcher_vpn.data.local.AppDatabase/$version.json"
        return generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) { it.parentFile }
            .flatMap { dir -> sequenceOf(dir.resolve(relative), dir.resolve("app/$relative")) }
            .first { it.isFile }
            .readText()
    }
}
