package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import java.util.Locale

enum class ProxySort {
    NEWEST,
    NAME,

    /** Fastest working proxies first; failed, then unchecked ones last. */
    LATENCY,
    TYPE_COUNTRY
}

sealed interface ProxyGroupFilter {
    data object All : ProxyGroupFilter

    data object Ungrouped : ProxyGroupFilter

    data class Named(val name: String) : ProxyGroupFilter

    /** Stable text form for settings storage. */
    fun encode(): String = when (this) {
        All -> ALL
        Ungrouped -> UNGROUPED
        is Named -> GROUP_PREFIX + name
    }

    companion object {
        private const val ALL = "all"
        private const val UNGROUPED = "ungrouped"
        private const val GROUP_PREFIX = "group:"

        fun decode(value: String?): ProxyGroupFilter = when {
            value == UNGROUPED -> Ungrouped
            value != null && value.startsWith(GROUP_PREFIX) -> Named(value.removePrefix(GROUP_PREFIX))
            else -> All
        }
    }
}

/** Search, group filter and sort for the proxy list; pure so it can be tested. */
object ProxyListQuery {
    fun apply(
        proxies: List<ProxyEntity>,
        checks: Map<Long, ProxyCheckEntity>,
        query: String,
        sort: ProxySort,
        group: ProxyGroupFilter
    ): List<ProxyEntity> {
        val effectiveGroup = group.takeUnless { it is ProxyGroupFilter.Named && it.name !in groups(proxies) }
            ?: ProxyGroupFilter.All
        val needle = query.trim().lowercase(Locale.ROOT)
        return proxies
            .filter { proxy -> matchesGroup(proxy, effectiveGroup) }
            .filter { proxy -> needle.isEmpty() || searchFields(proxy, checks[proxy.id]).any { it.contains(needle) } }
            .sortedWith(comparator(sort, checks))
    }

    /** Distinct non-blank group names, sorted ignoring case. */
    fun groups(proxies: List<ProxyEntity>): List<String> {
        return proxies.mapNotNull { it.groupName?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun displayName(proxy: ProxyEntity): String {
        return proxy.label?.takeIf { it.isNotBlank() } ?: "${proxy.host}:${proxy.port}"
    }

    private fun matchesGroup(proxy: ProxyEntity, group: ProxyGroupFilter): Boolean {
        val name = proxy.groupName?.trim()?.takeIf(String::isNotEmpty)
        return when (group) {
            ProxyGroupFilter.All -> true
            ProxyGroupFilter.Ungrouped -> name == null
            is ProxyGroupFilter.Named -> name == group.name
        }
    }

    /** Never includes username or password. */
    private fun searchFields(proxy: ProxyEntity, check: ProxyCheckEntity?): List<String> {
        return listOfNotNull(
            proxy.label,
            "${proxy.host}:${proxy.port}",
            proxy.groupName,
            check?.countryCode,
            check?.exitIp
        ).map { it.lowercase(Locale.ROOT) }
    }

    private fun comparator(sort: ProxySort, checks: Map<Long, ProxyCheckEntity>): Comparator<ProxyEntity> {
        val newest = compareByDescending<ProxyEntity> { it.id }
        val byName = compareBy<ProxyEntity, String>(String.CASE_INSENSITIVE_ORDER) { displayName(it) }
        return when (sort) {
            ProxySort.NEWEST -> newest
            ProxySort.NAME -> byName.then(newest)
            ProxySort.LATENCY -> compareBy<ProxyEntity> { latencyRank(checks[it.id]) }
                .thenBy { checks[it.id]?.latencyMillis ?: Long.MAX_VALUE }
                .then(byName)
            ProxySort.TYPE_COUNTRY -> compareBy<ProxyEntity> { it.type.ordinal }
                .thenBy(nullsLast()) { checks[it.id]?.countryCode }
                .then(byName)
        }
    }

    private fun latencyRank(check: ProxyCheckEntity?): Int = when {
        check?.success == true && check.latencyMillis != null -> 0
        check?.success != null -> 1
        else -> 2
    }
}
