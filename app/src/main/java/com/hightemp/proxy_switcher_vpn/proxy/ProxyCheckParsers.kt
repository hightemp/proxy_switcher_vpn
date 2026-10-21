package com.hightemp.proxy_switcher_vpn.proxy

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Public address and country seen by a server reached through a proxy. */
data class ExitInfo(
    val ip: String,
    val countryCode: String?
)

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String
)

object ProxyCheckParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val COUNTRY_CODE = Regex("[A-Z]{2}")

    /** Parses `https://www.cloudflare.com/cdn-cgi/trace` (key=value lines). */
    fun parseCloudflareTrace(body: String): ExitInfo? {
        val values = body.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1).trim()
            }
            .toMap()
        val ip = values["ip"]?.takeIf { it.isNotBlank() } ?: return null
        return ExitInfo(ip, normalizeCountry(values["loc"]))
    }

    /** Parses `https://ipinfo.io/json`. */
    fun parseIpinfo(body: String): ExitInfo? {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val ip = obj["ip"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return ExitInfo(ip, normalizeCountry(obj["country"]?.jsonPrimitive?.contentOrNull))
    }

    /** Builds a flag emoji from regional indicator symbols, e.g. "DE" → 🇩🇪. */
    fun countryFlag(countryCode: String?): String? {
        val code = normalizeCountry(countryCode) ?: return null
        return code.map { char -> String(Character.toChars(REGIONAL_INDICATOR_A + (char - 'A'))) }
            .joinToString("")
    }

    fun bitsPerSecond(bytes: Long, millis: Long): Long {
        if (millis <= 0L) return 0L
        return bytes * 8L * 1_000L / millis
    }

    fun formatMegabits(bitsPerSecond: Long, locale: Locale = Locale.US): String {
        return String.format(locale, "%.1f", bitsPerSecond / 1_000_000.0)
    }

    /**
     * Reads an HTTP/1.1 response: status line, headers and up to
     * [maxBodyBytes] of the body (Content-Length, chunked, or until EOF).
     */
    fun readHttpResponse(input: InputStream, maxBodyBytes: Int): HttpResponse {
        val head = readHttpHead(input)
        val headers = head.headers
        val body = ByteArrayOutputStream()
        when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                readChunked(input, body, maxBodyBytes)
            headers["content-length"]?.toLongOrNull() != null ->
                copyLimited(input, body, minOf(headers.getValue("content-length").toLong(), maxBodyBytes.toLong()))
            else -> copyLimited(input, body, maxBodyBytes.toLong())
        }
        return head.copy(body = body.toString(Charsets.UTF_8.name()))
    }

    /** Reads the status line and headers, leaving the body in [input]. */
    fun readHttpHead(input: InputStream): HttpResponse {
        val statusLine = readLine(input)
        val statusCode = statusLine.split(" ", limit = 3).getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Invalid HTTP status line.")
        val headers = buildMap {
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    put(line.substring(0, separator).trim().lowercase(Locale.US), line.substring(separator + 1).trim())
                }
            }
        }
        return HttpResponse(statusCode, headers, body = "")
    }

    private fun readChunked(input: InputStream, body: ByteArrayOutputStream, maxBodyBytes: Int) {
        while (body.size() < maxBodyBytes) {
            val size = readLine(input).substringBefore(';').trim().toIntOrNull(16)
                ?: throw IOException("Invalid chunk size.")
            if (size == 0) return
            copyLimited(input, body, minOf(size, maxBodyBytes - body.size()).toLong())
            readLine(input)
        }
    }

    private fun copyLimited(input: InputStream, output: ByteArrayOutputStream, limit: Long) {
        val buffer = ByteArray(8_192)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) return
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (true) {
            val value = input.read()
            if (value == -1) {
                if (line.isEmpty()) throw EOFException()
                break
            }
            if (value == '\n'.code) break
            if (value != '\r'.code) line.append(value.toChar())
            if (line.length > MAX_LINE_LENGTH) throw IOException("HTTP line is too long.")
        }
        return line.toString()
    }

    private fun normalizeCountry(value: String?): String? {
        val code = value?.trim()?.uppercase(Locale.US) ?: return null
        return code.takeIf { COUNTRY_CODE.matches(it) && it != "XX" }
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val MAX_LINE_LENGTH = 8_192
}
