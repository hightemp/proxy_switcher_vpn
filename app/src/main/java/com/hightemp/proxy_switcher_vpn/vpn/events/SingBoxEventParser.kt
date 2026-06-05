package com.hightemp.proxy_switcher_vpn.vpn.events

class SingBoxEventParser(
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun parseLogLine(line: String): VpnEvent? {
        parseDnsLogLine(line)?.let { return it }
        return parseUdpRejectLogLine(line)
    }

    private fun parseDnsLogLine(line: String): VpnEvent.Dns? {
        val dnsMessage = line.substringAfter(
            delimiter = DNS_PREFIX,
            missingDelimiterValue = ""
        ).trim()
        if (dnsMessage.isBlank()) return null

        val queryMatch = DNS_QUERY_PATTERNS
            .firstNotNullOfOrNull { pattern -> pattern.find(dnsMessage) }

        val status = if (dnsMessage.contains("fail", ignoreCase = true)) {
            DnsEventStatus.FAILURE
        } else {
            DnsEventStatus.QUERY
        }

        return VpnEvent.Dns(
            timestampMillis = nowMillis(),
            status = status,
            domain = queryMatch?.groupValues?.getOrNull(1)?.trimEnd('.'),
            queryType = queryMatch?.groupValues?.getOrNull(2)?.uppercase(),
            server = DNS_SERVER_PATTERN.find(dnsMessage)?.groupValues?.getOrNull(1)
        )
    }

    private fun parseUdpRejectLogLine(line: String): VpnEvent.UdpBlocked? {
        val isUdpReject =
            line.contains("reject udp", ignoreCase = true) ||
                line.contains("network=udp", ignoreCase = true) &&
                line.contains("reject", ignoreCase = true)
        if (!isUdpReject) return null

        return VpnEvent.UdpBlocked(
            timestampMillis = nowMillis(),
            destination = UDP_DESTINATION_PATTERN.find(line)?.groupValues?.getOrNull(1),
            port = UDP_PORT_PATTERN.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        )
    }

    private companion object {
        const val DNS_PREFIX = "dns:"

        val DNS_QUERY_PATTERNS = listOf(
            Regex("""exchange\s+([^\s]+)\s+IN\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE),
            Regex(
                """exchange\s+failed\s+(?:for\s+)?([^\s]+)\s+IN\s+([A-Z0-9]+)""",
                RegexOption.IGNORE_CASE
            )
        )
        val DNS_SERVER_PATTERN = Regex("""server[ =]([^\s,]+)""", RegexOption.IGNORE_CASE)
        val UDP_DESTINATION_PATTERN = Regex("""\bto\s+([^\s]+)""", RegexOption.IGNORE_CASE)
        val UDP_PORT_PATTERN = Regex("""\bport[= ](\d+)""", RegexOption.IGNORE_CASE)
    }
}
