package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import java.util.Locale

/** Field order for `a:b:c:d` lines that are valid both ways. */
enum class ProxyFieldOrder {
    HOST_FIRST,
    CREDENTIALS_FIRST
}

/**
 * [protocol] is used for lines without one; null means network auto-detection.
 * A non-blank [group] is assigned to every new and updated proxy.
 */
data class ProxyLineImportOptions(
    val protocol: ProxyType?,
    val fieldOrder: ProxyFieldOrder,
    val group: String? = null
)

enum class ProxyImportStatus {
    NEW,
    UPDATE,
    UNCHANGED,

    /** A later line in the same batch has the same host, port and username. */
    DUPLICATE,
    ERROR
}

data class ProxyImportLine(
    val lineNumber: Int,
    val raw: String,
    val status: ProxyImportStatus,
    val proxy: ParsedProxyString? = null,
    val existingId: Long? = null,
    val ambiguous: Boolean = false,
    val message: UserText? = null,
    val group: String? = null
) {
    val needsDetection: Boolean
        get() = proxy?.type == null && (status == ProxyImportStatus.NEW || status == ProxyImportStatus.UPDATE)

    override fun toString(): String {
        return "ProxyImportLine(lineNumber=$lineNumber, status=$status, proxy=$proxy, " +
            "existingId=$existingId, ambiguous=$ambiguous, group=$group)"
    }
}

data class ProxyImportPreview(
    val lines: List<ProxyImportLine>,
    val droppedCount: Int = 0
) {
    val hasAmbiguous: Boolean get() = lines.any { it.ambiguous }
    val importable: List<ProxyImportLine>
        get() = lines.filter { it.status == ProxyImportStatus.NEW || it.status == ProxyImportStatus.UPDATE }
    val needsDetectionCount: Int get() = lines.count { it.needsDetection }

    fun count(status: ProxyImportStatus): Int = lines.count { it.status == status }
}

/**
 * Plans a bulk import of proxy strings, one record per line. Pure: protocol
 * detection and saving happen in the caller.
 */
object ProxyLineImport {
    const val MAX_RECORDS = 1000

    /** Upper bound for pasted or loaded text, about 1000 long lines. */
    const val MAX_TEXT_LENGTH = 1024 * 1024

    fun preview(
        text: String,
        options: ProxyLineImportOptions,
        existing: List<ProxyEntity>
    ): ProxyImportPreview {
        val records = text.lineSequence()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("//") }
            .toList()
        val existingByKey = existing.associateBy { key(it.host, it.port, it.username) }
        val parsed = records.take(MAX_RECORDS).map { (lineNumber, line) ->
            parseLine(lineNumber, line, options, existingByKey)
        }
        val lastIndexByKey = parsed.withIndex()
            .filter { it.value.proxy != null }
            .associate { (index, line) -> line.proxy!!.key() to index }
        val lines = parsed.mapIndexed { index, line ->
            val proxy = line.proxy ?: return@mapIndexed line
            if (lastIndexByKey[proxy.key()] != index) line.copy(status = ProxyImportStatus.DUPLICATE) else line
        }
        return ProxyImportPreview(lines, droppedCount = (records.size - MAX_RECORDS).coerceAtLeast(0))
    }

    /** Applies a line to an existing proxy: new password, protocol and group if given; the rest is kept. */
    fun updatedProxy(existing: ProxyEntity, line: ParsedProxyString, group: String? = null): ProxyEntity {
        return existing.copy(
            type = line.type ?: existing.type,
            password = line.password,
            groupName = group ?: existing.groupName
        )
    }

    fun newProxy(line: ParsedProxyString, type: ProxyType, group: String? = null): ProxyEntity {
        return ProxyEntity(
            host = line.host,
            port = line.port,
            type = type,
            username = line.username,
            password = line.password,
            groupName = group
        )
    }

    /** `TYPE  host:port` without credentials. */
    fun displayText(proxy: ParsedProxyString): String {
        val address = "${proxy.host}:${proxy.port}"
        return proxy.type?.let { "${it.name}  $address" } ?: address
    }

    private fun parseLine(
        lineNumber: Int,
        line: String,
        options: ProxyLineImportOptions,
        existingByKey: Map<String, ProxyEntity>
    ): ProxyImportLine {
        val (parsed, ambiguous) = when (val result = ProxyStringParser.parse(line)) {
            is ProxyStringParseResult.Failure ->
                return ProxyImportLine(lineNumber, line, ProxyImportStatus.ERROR, message = result.message)
            is ProxyStringParseResult.Success -> result.proxy to false
            is ProxyStringParseResult.Ambiguous -> when (options.fieldOrder) {
                ProxyFieldOrder.HOST_FIRST -> result.hostFirst
                ProxyFieldOrder.CREDENTIALS_FIRST -> result.credentialsFirst
            } to true
        }
        val existing = existingByKey[parsed.key()]
        val proxy = parsed.copy(type = parsed.type ?: options.protocol ?: existing?.type)
        val group = options.group?.trim()?.takeIf { it.isNotEmpty() }
        val status = when {
            existing == null -> ProxyImportStatus.NEW
            existing == updatedProxy(existing, proxy, group) -> ProxyImportStatus.UNCHANGED
            else -> ProxyImportStatus.UPDATE
        }
        return ProxyImportLine(lineNumber, line, status, proxy, existing?.id, ambiguous, group = group)
    }

    private fun ParsedProxyString.key(): String = key(host, port, username)

    private fun key(host: String, port: Int, username: String?): String {
        return "${host.lowercase(Locale.US)}:$port:${username.orEmpty()}"
    }
}
