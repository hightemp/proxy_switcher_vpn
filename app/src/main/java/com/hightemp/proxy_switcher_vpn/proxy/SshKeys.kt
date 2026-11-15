package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An SSH server public key in OpenSSH `authorized_keys` form
 * (`ssh-ed25519 AAAA...`). [blob] is the wire-format key.
 */
class SshHostKey(val type: String, val blob: ByteArray) {
    @OptIn(ExperimentalEncodingApi::class)
    fun toOpenSsh(): String = "$type ${Base64.encode(blob)}"

    /** `SHA256:...` as printed by `ssh-keygen -l`. */
    @OptIn(ExperimentalEncodingApi::class)
    val fingerprint: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.encode(digest).trimEnd('=')
        }

    /**
     * Host key algorithms that make the client negotiate this key. sing-box
     * compares the key byte for byte but picks the algorithm itself, so the
     * generated config pins it to the confirmed key.
     */
    val algorithms: List<String>
        get() = if (type == SshKeys.TYPE_RSA) listOf("rsa-sha2-512", "rsa-sha2-256") else listOf(type)

    override fun equals(other: Any?): Boolean {
        return other is SshHostKey && other.type == type && other.blob.contentEquals(blob)
    }

    override fun hashCode(): Int = 31 * type.hashCode() + blob.contentHashCode()

    override fun toString(): String = "SshHostKey($type $fingerprint)"
}

object SshKeys {
    const val DEFAULT_PORT = 22
    const val TYPE_RSA = "ssh-rsa"
    const val MAX_PRIVATE_KEY_LENGTH = 16 * 1024

    private val HOST_KEY_TYPES = setOf(
        "ssh-ed25519",
        TYPE_RSA,
        "ecdsa-sha2-nistp256",
        "ecdsa-sha2-nistp384",
        "ecdsa-sha2-nistp521"
    )
    private val PRIVATE_KEY_BLOCK = Regex(
        "-----BEGIN ([A-Z0-9 ]*)PRIVATE KEY-----.+-----END \\1PRIVATE KEY-----",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * Parses a host key from a `.pub`/`authorized_keys` line or a
     * `known_hosts` line. Returns null when no supported key is found or the
     * key data does not match its declared type.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun parseHostKey(text: String): SshHostKey? {
        val tokens = text.trim().split(Regex("\\s+"))
        val index = tokens.indexOfFirst { it in HOST_KEY_TYPES }
        if (index < 0 || index + 1 >= tokens.size) return null
        val type = tokens[index]
        val blob = runCatching { Base64.decode(tokens[index + 1]) }.getOrNull() ?: return null
        return SshHostKey(type, blob).takeIf { blobType(blob) == type }
    }

    /** Builds a host key from the type and wire-format key reported by the SSH client. */
    fun hostKey(blob: ByteArray): SshHostKey? {
        val type = blobType(blob) ?: return null
        return SshHostKey(type, blob).takeIf { type in HOST_KEY_TYPES }
    }

    /** Trims the pasted key and normalizes line endings; blank becomes null. */
    fun normalizePrivateKey(text: String?): String? {
        return text?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim()?.ifBlank { null }
    }

    /**
     * Returns why [privateKey] cannot be used, or null when it looks like an
     * OpenSSH or PEM private key. The key itself is never part of the message.
     */
    fun privateKeyProblem(privateKey: String): UserText? {
        return when {
            privateKey.length > MAX_PRIVATE_KEY_LENGTH -> UserText.of(R.string.ssh_key_error_too_long)
            privateKey.startsWith("PuTTY-User-Key-File") -> UserText.of(R.string.ssh_key_error_putty)
            !PRIVATE_KEY_BLOCK.containsMatchIn(privateKey) -> UserText.of(R.string.ssh_key_error_format)
            else -> null
        }
    }

    /** Reads the leading SSH string of a wire-format key: its algorithm name. */
    private fun blobType(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val length = ((blob[0].toInt() and 0xff) shl 24) or
            ((blob[1].toInt() and 0xff) shl 16) or
            ((blob[2].toInt() and 0xff) shl 8) or
            (blob[3].toInt() and 0xff)
        if (length <= 0 || length > 64 || 4 + length > blob.size) return null
        return String(blob, 4, length, Charsets.US_ASCII)
    }
}
