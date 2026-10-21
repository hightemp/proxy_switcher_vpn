package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LogExportHeader(
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    val language: String,
    val generatedAtMillis: Long
)

/**
 * Removes secrets from exported text. Saved proxy usernames and passwords are
 * always replaced; with [hideAddresses] saved proxy hosts and labels, public
 * IPv4 addresses and domain names become stable tags (`proxy-N`, `ip-N`,
 * `domain-N`). Keep one instance per report so tags stay consistent.
 */
class LogRedactor(
    proxies: List<ProxyEntity>,
    val hideAddresses: Boolean
) {
    private val replacements: List<Pair<Regex, String>>
    private val ipTags = mutableMapOf<String, String>()
    private val domainTags = mutableMapOf<String, String>()

    init {
        val values = mutableMapOf<String, String>()
        proxies.forEach { proxy ->
            listOfNotNull(proxy.username, proxy.password).forEach { values[it] = MASK }
        }
        if (hideAddresses) {
            proxies.forEachIndexed { index, proxy ->
                val tag = "proxy-${index + 1}"
                listOfNotNull(proxy.host, proxy.label).forEach { values.putIfAbsent(it, tag) }
            }
        }
        replacements = values.filterKeys { it.isNotBlank() }
            .entries
            .sortedByDescending { it.key.length }
            .map { (value, tag) -> literal(value) to tag }
    }

    fun redact(text: String): String {
        val masked = replacements.fold(text) { current, (pattern, tag) ->
            pattern.replace(current) { tag }
        }
        if (!hideAddresses) return masked
        val withoutIps = IPV4.replace(masked) { match ->
            val ip = match.value
            if (isPrivate(ip)) ip else ipTags.getOrPut(ip) { "ip-${ipTags.size + 1}" }
        }
        return DOMAIN.replace(withoutIps) { match ->
            val domain = match.value
            if (isNotDomain(domain)) {
                domain
            } else {
                domainTags.getOrPut(domain.lowercase(Locale.ROOT)) { "domain-${domainTags.size + 1}" }
            }
        }
    }

    private fun isPrivate(ip: String): Boolean {
        val octets = ip.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.any { it > 255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || a == 0 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }

    /** File names, class and package names look like domains; their last label decides. */
    private fun isNotDomain(value: String): Boolean {
        val labels = value.split('.')
        val last = labels.last()
        return last.any { it.isUpperCase() } ||
            last in FILE_EXTENSIONS ||
            labels.first().lowercase(Locale.ROOT) in PACKAGE_ROOTS
    }

    private companion object {
        const val MASK = "***"
        /** Not followed by a prefix length: CIDR routes in the config are not user data. */
        val IPV4 = Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w/]|\\.\\d)")
        val DOMAIN = Regex(
            "(?<![\\w.-])(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}(?![\\w-]|\\.[\\w-])"
        )
        val FILE_EXTENSIONS = setOf(
            "apk", "conf", "csv", "db", "go", "gradle", "html", "java", "json", "kt", "kts",
            "log", "md", "pb", "png", "so", "toml", "txt", "xml", "yaml", "yml"
        )
        val PACKAGE_ROOTS = setOf(
            "android", "androidx", "com", "dalvik", "io", "java", "javax", "kotlin", "kotlinx", "libcore", "org"
        )

        /** Matches [value] literally, but not inside a longer word. */
        fun literal(value: String): Regex {
            val start = if (value.first().isLetterOrDigit()) "(?<![A-Za-z0-9])" else ""
            val end = if (value.last().isLetterOrDigit()) "(?![A-Za-z0-9])" else ""
            return Regex(start + Regex.escape(value) + end)
        }
    }
}

/** Plain-text bug report: header, diagnostics and logs, all redacted. */
object LogExport {
    fun build(
        header: LogExportHeader,
        diagnostics: List<Pair<String, String>>,
        entries: List<LogEntry>,
        redactor: LogRedactor,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val timestamp = formatter("yyyy-MM-dd HH:mm:ss.SSS", timeZone)
        val generated = formatter("yyyy-MM-dd HH:mm:ss zzz", timeZone)
        return buildString {
            appendLine("Proxy Switcher VPN log export")
            appendLine("Generated: ${generated.format(Date(header.generatedAtMillis))}")
            appendLine("App version: ${header.appVersion}")
            appendLine("Android: ${header.androidVersion}")
            appendLine("Device: ${header.device}")
            appendLine("App language: ${header.language}")
            appendLine("Credentials: hidden")
            appendLine("Addresses and domains: ${if (redactor.hideAddresses) "hidden" else "shown"}")
            appendLine()
            appendLine("== Diagnostics ==")
            diagnostics.forEach { (label, value) ->
                appendLine(redactor.redact("$label: $value"))
            }
            appendLine()
            appendLine("== Logs (${entries.size} entries) ==")
            entries.forEach { entry ->
                val time = timestamp.format(Date(entry.timestampMillis))
                appendLine("$time ${entry.level.name} ${entry.type.name} ${redactor.redact(entry.message)}")
            }
        }
    }

    fun fileName(generatedAtMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        return "proxy-switcher-vpn-logs-${formatter("yyyyMMdd-HHmmss", timeZone).format(Date(generatedAtMillis))}.txt"
    }

    private fun formatter(pattern: String, timeZone: TimeZone): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.US).apply { this.timeZone = timeZone }
    }
}
