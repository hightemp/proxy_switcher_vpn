package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTransferTest {
    @Test
    fun importsReferenceProxySwitcherJson() {
        val referenceExport = """
            [
              {
                "host": "legacy.example",
                "port": 8443,
                "type": "HTTPS",
                "username": "legacy-user",
                "password": "legacy-pass",
                "label": "Legacy HTTPS",
                "isEnabled": false
              }
            ]
        """.trimIndent()

        val result = ProxyTransfer.import(referenceExport)

        assertTrue(result.isSuccess)
        val proxy = result.proxies.single()
        assertEquals(0L, proxy.id)
        assertEquals("legacy.example", proxy.host)
        assertEquals(8443, proxy.port)
        assertEquals(ProxyType.HTTPS, proxy.type)
        assertEquals("legacy-user", proxy.username)
        assertEquals("legacy-pass", proxy.password)
        assertEquals("Legacy HTTPS", proxy.label)
        assertEquals(false, proxy.isEnabled)
    }

    @Test
    fun exportsReferenceCompatibleJson() {
        val text = ProxyTransfer.export(
            listOf(
                ProxyEntity(
                    id = 42L,
                    host = "socks.example",
                    port = 1080,
                    type = ProxyType.SOCKS5,
                    username = "user",
                    password = "secret",
                    label = "SOCKS",
                    isEnabled = true
                )
            )
        )

        assertTrue(text.contains("\"host\": \"socks.example\""))
        assertTrue(text.contains("\"port\": 1080"))
        assertTrue(text.contains("\"type\": \"SOCKS5\""))
        assertTrue(text.contains("\"username\": \"user\""))
        assertTrue(text.contains("\"password\": \"secret\""))
        assertTrue(text.contains("\"label\": \"SOCKS\""))
        assertTrue(text.contains("\"isEnabled\": true"))

        val imported = ProxyTransfer.import(text).proxies.single()
        assertEquals(0L, imported.id)
        assertEquals("socks.example", imported.host)
        assertEquals(1080, imported.port)
        assertEquals(ProxyType.SOCKS5, imported.type)
        assertEquals("user", imported.username)
        assertEquals("secret", imported.password)
        assertEquals("SOCKS", imported.label)
    }

    @Test
    fun invalidOrEmptyInputReturnsUsefulError() {
        assertEquals("Input is empty", ProxyTransfer.import("  ").errorMessage)

        val invalid = ProxyTransfer.import("""{"host":"not-an-array"}""")
        assertEquals("Invalid format: expected JSON array", invalid.errorMessage)

        val noValidRows = ProxyTransfer.import("""[{"host":"","port":0}]""")
        assertEquals("No valid proxies found", noValidRows.errorMessage)
    }

    @Test
    fun missingOptionalFieldsUseReferenceDefaults() {
        val result = ProxyTransfer.import("""[{"host":"proxy.example","port":8080}]""")

        val proxy = result.proxies.single()
        assertEquals(ProxyType.HTTP, proxy.type)
        assertEquals(true, proxy.isEnabled)
        assertNull(proxy.username)
        assertNull(proxy.password)
        assertNull(proxy.label)
    }
}
