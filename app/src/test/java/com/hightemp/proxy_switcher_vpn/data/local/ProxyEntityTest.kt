package com.hightemp.proxy_switcher_vpn.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProxyEntityTest {
    private val ssh = ProxyEntity(
        host = "ssh.example",
        port = 22,
        type = ProxyType.SSH,
        username = "tester",
        password = "ssh-pass",
        privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nkey-body\n-----END OPENSSH PRIVATE KEY-----",
        privateKeyPassphrase = "key-pass",
        hostKey = "ssh-ed25519 AAAA"
    )

    @Test
    fun sensitiveValuesCoverPasswordKeyAndPassphrase() {
        assertEquals(
            listOf("tester", "ssh-pass", ssh.privateKey, "key-pass"),
            ssh.sensitiveValues()
        )
    }

    @Test
    fun toStringHidesSecrets() {
        val text = ssh.toString()

        listOf("tester", "ssh-pass", "key-body", "key-pass").forEach { assertFalse(it, text.contains(it)) }
    }

    @Test
    fun sshFieldsAreDroppedOnlyForOtherTypes() {
        assertEquals(ssh, ssh.withoutStaleSshFields())
        assertEquals(
            ssh.copy(type = ProxyType.HTTP, privateKey = null, privateKeyPassphrase = null, hostKey = null),
            ssh.copy(type = ProxyType.HTTP).withoutStaleSshFields()
        )
    }
}
