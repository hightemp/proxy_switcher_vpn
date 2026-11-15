package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SshKeysTest {
    @Test
    fun parsesPublicKeyLinesAndMatchesSshKeygenFingerprints() {
        val ed25519 = SshKeys.parseHostKey("$ED25519 root@server")!!
        val rsa = SshKeys.parseHostKey(RSA)!!
        val ecdsa = SshKeys.parseHostKey("  $ECDSA\n")!!

        assertEquals("ssh-ed25519", ed25519.type)
        assertEquals("SHA256:HtzUvIsLblnPke66dLQb9Y/18aEnmLNU/L8/O4inqbY", ed25519.fingerprint)
        assertEquals("SHA256:k5IP2OwfVY2LMNKecXPOYkJbvhJGY+KDLvnWq0EcBD0", rsa.fingerprint)
        assertEquals("SHA256:FNHWUoYuxWBqsLydUSxt1uOT0WerBTHkQdYaPx9i344", ecdsa.fingerprint)
        assertEquals(ED25519, ed25519.toOpenSsh())
        assertEquals(ECDSA, ecdsa.toOpenSsh())
    }

    @Test
    fun parsesKnownHostsLine() {
        val key = SshKeys.parseHostKey("[ssh.example]:2222,192.0.2.5 $ED25519")

        assertEquals(SshKeys.parseHostKey(ED25519), key)
    }

    @Test
    fun algorithmsPinTheKeyType() {
        assertEquals(listOf("ssh-ed25519"), SshKeys.parseHostKey(ED25519)!!.algorithms)
        assertEquals(listOf("rsa-sha2-512", "rsa-sha2-256"), SshKeys.parseHostKey(RSA)!!.algorithms)
        assertEquals(listOf("ecdsa-sha2-nistp256"), SshKeys.parseHostKey(ECDSA)!!.algorithms)
    }

    @Test
    fun rejectsUnsupportedOrInconsistentKeys() {
        assertNull(SshKeys.parseHostKey(""))
        assertNull(SshKeys.parseHostKey("ssh-ed25519"))
        assertNull(SshKeys.parseHostKey("ssh-ed25519 not*base64"))
        assertNull(SshKeys.parseHostKey("ssh-dss AAAAB3NzaC1kc3MAAACBAP"))
        // Declared type does not match the key data.
        assertNull(SshKeys.parseHostKey("ssh-rsa " + ED25519.substringAfter(' ')))
    }

    @Test
    fun hostKeyFromWireFormat() {
        val parsed = SshKeys.parseHostKey(RSA)!!

        assertEquals(parsed, SshKeys.hostKey(parsed.blob))
        assertNull(SshKeys.hostKey(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun privateKeyChecks() {
        val openSsh = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----"
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"

        assertNull(SshKeys.privateKeyProblem(openSsh))
        assertNull(SshKeys.privateKeyProblem(pem))
        assertEquals(
            UserText.of(R.string.ssh_key_error_putty),
            SshKeys.privateKeyProblem("PuTTY-User-Key-File-3: ssh-ed25519\nEncryption: none")
        )
        assertEquals(UserText.of(R.string.ssh_key_error_format), SshKeys.privateKeyProblem("ssh-ed25519 AAAA"))
        assertEquals(
            UserText.of(R.string.ssh_key_error_format),
            SshKeys.privateKeyProblem("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----")
        )
        assertEquals(
            UserText.of(R.string.ssh_key_error_too_long),
            SshKeys.privateKeyProblem(openSsh + "A".repeat(SshKeys.MAX_PRIVATE_KEY_LENGTH))
        )
    }

    @Test
    fun normalizesPastedPrivateKey() {
        assertEquals("a\nb\nc", SshKeys.normalizePrivateKey("  a\r\nb\rc \n"))
        assertNull(SshKeys.normalizePrivateKey(" \n "))
        assertNull(SshKeys.normalizePrivateKey(null))
    }

    @Test
    fun toStringShowsOnlyTypeAndFingerprint() {
        assertEquals(
            "SshHostKey(ssh-ed25519 SHA256:HtzUvIsLblnPke66dLQb9Y/18aEnmLNU/L8/O4inqbY)",
            SshKeys.parseHostKey(ED25519).toString()
        )
    }

    private companion object {
        const val ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICB8BYL3UC44wKFmebFbQCgtyM5cVbO/fjpf4CKx82rh"
        const val ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBFNwT/2/tH1HM/0" +
            "UHGoK8ecg4FCTE4nDtkD+mQst7TWjoMy5JCcHgp5jfbvaWEw7Q0DFv5/UjRziRVE5Mg0eugw="
        const val RSA = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCgjZZr7uV9r/YGQdfAtCqpSNf8JJpRfB7UqKFUO5lGgdxbwR+hD9Yu" +
            "ysoScDhVNaB/bgEGx+r3gAX8uTiG6V/ex0u+qKqU+tmoPgjBo59uxhtNWDZwgNbV84bhVNiQArWxp/AaEohJGq4TSphMOYmkm4MpU6" +
            "YiFAZMC6dCVZ3UUbgkGIUD4EbIhG+qnetVEUVRiONhRGvDasj4IhrJtc/w0GJNtiD4PlEAWaaP8+b9Ayk/w2sxwlxNaXj26aiqkzLZ" +
            "J2G6wsvX1Kiy446/7ifL7QraYWA3TUVAzDyyt43x9BfEuh8+/qHQ8YPDXgnjEh+omX/sGg7XP2bPb8o7QB2p"
    }
}
