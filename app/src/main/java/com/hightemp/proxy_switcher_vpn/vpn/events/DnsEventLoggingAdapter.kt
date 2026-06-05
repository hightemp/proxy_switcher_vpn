package com.hightemp.proxy_switcher_vpn.vpn.events

import com.hightemp.proxy_switcher_vpn.data.settings.AppSettings
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCounters

class DnsEventLoggingAdapter(
    private val logger: AppLogger = AppLogger
) {
    fun handle(
        event: VpnEvent,
        settings: AppSettings,
        counters: VpnEngineCounters
    ): VpnEngineCounters {
        return when (event) {
            is VpnEvent.Dns -> handleDns(event, settings, counters)
            else -> counters
        }
    }

    private fun handleDns(
        event: VpnEvent.Dns,
        settings: AppSettings,
        counters: VpnEngineCounters
    ): VpnEngineCounters {
        val updatedCounters = counters.copy(dnsQueries = counters.dnsQueries + 1)
        val message = dnsMessage(event, settings)
        when (event.status) {
            DnsEventStatus.QUERY -> logger.info(
                message = message,
                timestampMillis = event.timestampMillis,
                type = LogType.DNS
            )
            DnsEventStatus.FAILURE -> logger.warning(
                message = message,
                timestampMillis = event.timestampMillis,
                type = LogType.DNS
            )
        }
        return updatedCounters
    }

    private fun dnsMessage(
        event: VpnEvent.Dns,
        settings: AppSettings
    ): String {
        val action = when (event.status) {
            DnsEventStatus.QUERY -> "DNS query"
            DnsEventStatus.FAILURE -> "DNS query failed"
        }

        if (!settings.domainDestinationLoggingEnabled) {
            return "$action. Detailed domain logging is disabled."
        }

        val domain = event.domain ?: "unknown-domain"
        val queryType = event.queryType?.let { " $it" }.orEmpty()
        val server = event.server?.let { " via $it" }.orEmpty()
        return "$action for $domain$queryType$server."
    }
}
