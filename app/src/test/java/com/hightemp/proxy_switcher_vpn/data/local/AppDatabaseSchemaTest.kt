package com.hightemp.proxy_switcher_vpn.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDatabaseSchemaTest {
    /** The hand-written v1 → v2 migration must create exactly the table Room expects. */
    @Test
    fun migrationSqlMatchesExportedSchema() {
        val relative = "schemas/com.hightemp.proxy_switcher_vpn.data.local.AppDatabase/2.json"
        val schema = generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) { it.parentFile }
            .flatMap { dir -> sequenceOf(dir.resolve(relative), dir.resolve("app/$relative")) }
            .first { it.isFile }
            .readText()
        val createSql = Regex("\"tableName\": \"proxy_checks\",\\s*\"createSql\": \"([^\"]+)\"")
            .find(schema)!!.groupValues[1]
            .replace("`\${TABLE_NAME}`", "`proxy_checks`")

        assertEquals(createSql, AppDatabase.CREATE_PROXY_CHECKS_V2)
    }
}
