package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private const val MAX_INPUT_LENGTH = 8 * 1024

/**
 * One proxy record parsed from a pasted string. [type] is null when the string
 * does not name a protocol and it has to be detected over the network.
 */
data class ParsedProxyString(
    val host: String,
    val port: Int,
    val type: ProxyType?,
    val username: String? = null,
    val password: String? = null
) {
    override fun toString(): String {
        return "ParsedProxyString(" +
            "host='$host', " +
            "port=$port, " +
            "type=$type, " +
            "username=${username?.let { "'***'" }}, " +
            "password=${password?.let { "'***'" }}" +
            ")"
    }
}

sealed interface ProxyStringParseResult {
    data class Success(val proxy: ParsedProxyString) : ProxyStringParseResult

    /** A `a:b:c:d` record that is valid as both host:port:user:pass and user:pass:host:port. */
    data class Ambiguous(
        val hostFirst: ParsedProxyString,
        val credentialsFirst: ParsedProxyString
    ) : ProxyStringParseResult

    data class Failure(val message: String) : ProxyStringParseResult
}

/**
 * Parses a single proxy record in the text formats accepted by proxy-vouch,
 * limited to what this app supports (SOCKS5/HTTP/HTTPS, IPv4/DNS hosts).
 *
 * Error messages never include the input or credentials.
 */
object ProxyStringParser {
    fun parse(input: String): ProxyStringParseResult {
        val text = input.trim()
        return try {
            when {
                text.isEmpty() -> fail("Proxy string is empty.")
                text.length > MAX_INPUT_LENGTH -> fail("Proxy string is too long.")
                text.any { it.isISOControl() && it != '\t' } ->
                    fail("Proxy string must not contain control characters.")
                else -> parseCsvRow(text) ?: parseTokens(text)
            }
        } catch (error: ParseException) {
            ProxyStringParseResult.Failure(error.message.orEmpty())
        }
    }

    private fun parseCsvRow(text: String): ProxyStringParseResult? {
        if (text.contains("://")) return null
        val delimiter = listOf('\t', ';', ',').firstOrNull { text.contains(it) } ?: return null
        val fields = text.split(delimiter).map { it.trim() }
        if (fields.size !in 2..5 || !fields[1].all { it.isDigit() }) return null

        val protocol = when (fields.size) {
            3, 5 -> protocolToken(fields.last())
                ?: fail("Unsupported protocol in the last column.")
            else -> null
        }
        val username = if (fields.size >= 4) fields[2].ifEmpty { null } else null
        val password = if (fields.size >= 4) fields[3].ifEmpty { null } else null
        return success(buildProxy(fields[0], fields[1], username, password, protocol))
    }

    private fun parseTokens(text: String): ProxyStringParseResult {
        val tokens = text.split(Regex("\\s+"))
        return when (tokens.size) {
            1 -> parseRecord(tokens[0], tokenProtocol = null)
            2 -> {
                val leading = protocolToken(tokens[0])
                val trailing = protocolToken(tokens[1])
                when {
                    leading != null -> parseRecord(tokens[1], leading)
                    trailing != null -> parseRecord(tokens[0], trailing)
                    isPortText(tokens[1]) -> success(buildProxy(tokens[0], tokens[1], null, null, null))
                    else -> fail(FORMAT_ERROR)
                }
            }
            3 -> {
                val protocol = protocolToken(tokens[2])
                if (protocol == null || !isPortText(tokens[1])) fail(FORMAT_ERROR)
                success(buildProxy(tokens[0], tokens[1], null, null, protocol))
            }
            4, 5 -> {
                val protocol = if (tokens.size == 5) {
                    protocolToken(tokens[4]) ?: fail(FORMAT_ERROR)
                } else {
                    null
                }
                if (!isPortText(tokens[1])) fail(FORMAT_ERROR)
                success(buildProxy(tokens[0], tokens[1], tokens[2], tokens[3], protocol))
            }
            else -> fail(FORMAT_ERROR)
        }
    }

    private fun parseRecord(record: String, tokenProtocol: ProxyType?): ProxyStringParseResult {
        if (record.contains("://")) {
            val proxy = parseUri(record)
            if (tokenProtocol != null && tokenProtocol != proxy.type) {
                fail("Protocol conflict: the URI scheme and the protocol name differ.")
            }
            return success(proxy)
        }
        if (record.startsWith("[")) fail(IPV6_ERROR)

        val atCount = record.count { it == '@' }
        if (atCount > 1) fail(FORMAT_ERROR)
        if (atCount == 1) {
            val userInfo = record.substringBefore('@')
            val (username, password) = splitUserInfo(userInfo)
            val (host, port) = splitHostPort(record.substringAfter('@'))
            return success(buildProxy(host, port, username, password, tokenProtocol))
        }

        val parts = record.split(':')
        return when (parts.size) {
            2 -> success(buildProxy(parts[0], parts[1], null, null, tokenProtocol))
            4 -> resolveFourFields(parts, tokenProtocol)
            else -> if (parts.size == 1) fail(PORT_REQUIRED_ERROR) else fail(FORMAT_ERROR)
        }
    }

    private fun resolveFourFields(parts: List<String>, protocol: ProxyType?): ProxyStringParseResult {
        val hostFirst = tryBuild(parts[0], parts[1], parts[2], parts[3], protocol)
            ?.takeIf { isDottedHost(parts[0]) }
        val credentialsFirst = tryBuild(parts[2], parts[3], parts[0], parts[1], protocol)
            ?.takeIf { isDottedHost(parts[2]) }
        return when {
            hostFirst != null && credentialsFirst != null ->
                ProxyStringParseResult.Ambiguous(hostFirst, credentialsFirst)
            hostFirst != null -> success(hostFirst)
            credentialsFirst != null -> success(credentialsFirst)
            else -> fail(
                "Unrecognized format: expected host:port:username:password " +
                    "or username:password:host:port."
            )
        }
    }

    private fun parseUri(record: String): ParsedProxyString {
        val scheme = record.substringBefore("://")
        val type = protocolToken(scheme) ?: fail("Unsupported proxy scheme.")
        var authority = record.substringAfter("://").removeSuffix("/")
        if (authority.any { it == '/' || it == '?' || it == '#' }) {
            fail("Proxy URI must not contain a path, query, or fragment.")
        }

        var username: String? = null
        var password: String? = null
        if (authority.contains('@')) {
            val userInfo = authority.substringBeforeLast('@')
            authority = authority.substringAfterLast('@')
            val (rawUsername, rawPassword) = splitUserInfo(userInfo)
            username = rawUsername?.let(::percentDecode)
            password = rawPassword?.let(::percentDecode)
        }
        if (authority.startsWith("[")) fail(IPV6_ERROR)
        val (host, port) = splitHostPort(authority)
        return buildProxy(host, port, username, password, type)
    }

    private fun splitUserInfo(userInfo: String): Pair<String?, String?> {
        if (userInfo.any { it.isWhitespace() || it == '@' }) fail(FORMAT_ERROR)
        val username = userInfo.substringBefore(':')
        val password = if (userInfo.contains(':')) userInfo.substringAfter(':') else null
        return username.ifEmpty { null } to password?.ifEmpty { null }
    }

    private fun splitHostPort(value: String): Pair<String, String> {
        if (value.startsWith("[")) fail(IPV6_ERROR)
        val parts = value.split(':')
        return when (parts.size) {
            1 -> fail(PORT_REQUIRED_ERROR)
            2 -> parts[0] to parts[1]
            else -> fail(FORMAT_ERROR)
        }
    }

    private fun tryBuild(
        host: String,
        port: String,
        username: String,
        password: String,
        protocol: ProxyType?
    ): ParsedProxyString? {
        return try {
            buildProxy(host, port, username, password, protocol)
        } catch (_: ParseException) {
            null
        }
    }

    private fun buildProxy(
        hostText: String,
        portText: String,
        username: String?,
        password: String?,
        protocol: ProxyType?
    ): ParsedProxyString {
        val port = portText.takeIf(::isPortText)?.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: fail("Proxy port must be a number between 1 and 65535.")
        if (listOfNotNull(username, password).any { value -> value.any { it.isISOControl() } }) {
            fail("Credentials must not contain control characters.")
        }
        if (username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            fail("Username is required when password is set.")
        }
        return ParsedProxyString(
            host = normalizeHost(hostText),
            port = port,
            type = protocol,
            username = username?.ifEmpty { null },
            password = password?.ifEmpty { null }
        )
    }

    private fun normalizeHost(value: String): String {
        if (value.startsWith("[") || value.count { it == ':' } > 1) fail(IPV6_ERROR)
        if (value.isEmpty()) fail("Proxy host is required.")
        if (value.all { it.isDigit() || it == '.' }) {
            return value.takeIf(::isIpv4) ?: fail("Invalid IPv4 host.")
        }
        val ascii = try {
            IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: IllegalArgumentException) {
            fail("Invalid proxy host name.")
        }
        val labels = ascii.removeSuffix(".").split('.')
        val valid = ascii.length <= 253 && labels.all { label ->
            label.length in 1..63 &&
                !label.startsWith('-') &&
                !label.endsWith('-') &&
                label.all { it in 'a'..'z' || it.isDigit() || it == '-' }
        }
        return ascii.takeIf { valid } ?: fail("Invalid proxy host name.")
    }

    private fun isDottedHost(value: String): Boolean {
        return value.contains('.') && runCatching { normalizeHost(value) }.isSuccess
    }

    private fun isIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.length in 1..3 && octet.all { it.isDigit() } && octet.toInt() <= 255
        }
    }

    private fun isPortText(value: String): Boolean = value.isNotEmpty() && value.all { it in '0'..'9' }

    /** Returns the proxy type for a protocol name, or null when [value] is not a protocol name. */
    private fun protocolToken(value: String): ProxyType? {
        return when (value.lowercase()) {
            "http" -> ProxyType.HTTP
            "https" -> ProxyType.HTTPS
            "socks5", "socks5h" -> ProxyType.SOCKS5
            "socks4", "socks4a" -> fail("SOCKS4/SOCKS4a proxies are not supported.")
            "socks" -> fail("Ambiguous protocol 'socks': use socks5.")
            else -> null
        }
    }

    private fun percentDecode(value: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                val hex = value.substring(index + 1, minOf(index + 3, value.length))
                if (hex.length != 2 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                    fail("Invalid percent-encoding in credentials.")
                }
                bytes.write(hex.toInt(16))
                index += 3
            } else {
                bytes.write(char.toString().toByteArray(StandardCharsets.UTF_8))
                index++
            }
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            fail("Credentials must be valid UTF-8.")
        }
    }

    private fun success(proxy: ParsedProxyString) = ProxyStringParseResult.Success(proxy)

    private fun fail(message: String): Nothing = throw ParseException(message)

    private class ParseException(message: String) : Exception(message)

    private const val FORMAT_ERROR = "Unrecognized proxy string format."
    private const val PORT_REQUIRED_ERROR = "Proxy port is required."
    private const val IPV6_ERROR = "IPv6 upstream proxies are not supported."
}
