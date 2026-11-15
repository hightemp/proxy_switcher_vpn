package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
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
    fun groupRoundTripsAndIsOptional() {
        val grouped = ProxyEntity(host = "192.0.2.1", port = 1080, type = ProxyType.SOCKS5, groupName = "Work")
        val plain = ProxyEntity(host = "192.0.2.2", port = 8080, type = ProxyType.HTTP)

        val text = ProxyTransfer.export(listOf(grouped, plain))

        assertEquals(1, Regex("\"group\"").findAll(text).count())
        assertEquals(listOf("Work", null), ProxyTransfer.import(text).proxies.map { it.groupName })
    }

    @Test
    fun invalidOrEmptyInputReturnsUsefulError() {
        assertEquals(UserText.of(R.string.import_error_empty), ProxyTransfer.import("  ").errorMessage)

        val invalid = ProxyTransfer.import("""{"host":"not-an-array"}""")
        assertEquals(UserText.of(R.string.import_error_not_array), invalid.errorMessage)

        val noValidRows = ProxyTransfer.import("""[{"host":"","port":0}]""")
        assertEquals(UserText.of(R.string.import_error_no_proxies), noValidRows.errorMessage)
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

    private val sshProxy = ProxyEntity(
        id = 9L,
        host = "ssh.example",
        port = 2222,
        type = ProxyType.SSH,
        username = "tester",
        password = "ssh-pass",
        label = "Jump",
        privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nkey-body\n-----END OPENSSH PRIVATE KEY-----",
        privateKeyPassphrase = "key-pass",
        hostKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICB8BYL3UC44wKFmebFbQCgtyM5cVbO/fjpf4CKx82rh"
    )

    @Test
    fun sshProxyRoundTripsWithSecrets() {
        val result = ProxyTransfer.import(ProxyTransfer.export(listOf(sshProxy)))

        assertTrue(result.isSuccess)
        assertEquals(sshProxy.copy(id = 0L), result.proxies.single())
    }

    @Test
    fun exportWithoutSecretsKeepsHostKeyAndDropsPasswordsAndKeys() {
        val http = ProxyEntity(host = "proxy.example", port = 8080, type = ProxyType.HTTP, username = "u", password = "http-pass")

        val text = ProxyTransfer.export(listOf(sshProxy, http), includeSecrets = false)
        val imported = ProxyTransfer.import(text).proxies

        listOf("ssh-pass", "key-body", "key-pass", "http-pass").forEach { secret ->
            assertTrue(secret, !text.contains(secret))
        }
        assertEquals(sshProxy.hostKey, imported[0].hostKey)
        assertEquals("tester", imported[0].username)
        assertNull(imported[0].password)
        assertNull(imported[0].privateKey)
        assertNull(imported[1].password)
    }

    @Test
    fun sshFieldsAreDroppedForOtherProtocols() {
        val text = ProxyTransfer.export(listOf(sshProxy)).replace("\"SSH\"", "\"SOCKS5\"")

        val proxy = ProxyTransfer.import(text).proxies.single()

        assertEquals(ProxyType.SOCKS5, proxy.type)
        assertNull(proxy.privateKey)
        assertNull(proxy.privateKeyPassphrase)
        assertNull(proxy.hostKey)
    }
}
