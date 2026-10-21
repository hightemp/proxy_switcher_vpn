package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.data.local.ProxyCheckEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyListQueryTest {
    private val alpha = ProxyEntity(id = 1L, host = "192.0.2.10", port = 1080, type = ProxyType.SOCKS5, label = "Alpha", groupName = "Work")
    private val beta = ProxyEntity(id = 2L, host = "beta.example", port = 8080, type = ProxyType.HTTP, username = "secret-user", password = "secret-pass")
    private val gamma = ProxyEntity(id = 3L, host = "198.51.100.7", port = 443, type = ProxyType.HTTPS, label = "gamma", groupName = "Home")
    private val delta = ProxyEntity(id = 4L, host = "203.0.113.4", port = 3128, type = ProxyType.HTTP, groupName = "Work")
    private val proxies = listOf(delta, gamma, beta, alpha)
    private val checks = mapOf(
        1L to ProxyCheckEntity(proxyId = 1L, checkedAtMillis = 1L, success = true, latencyMillis = 300L, exitIp = "203.0.113.200", countryCode = "DE"),
        2L to ProxyCheckEntity(proxyId = 2L, checkedAtMillis = 1L, success = false),
        3L to ProxyCheckEntity(proxyId = 3L, checkedAtMillis = 1L, success = true, latencyMillis = 40L, countryCode = "NL")
    )

    private fun ids(
        query: String = "",
        sort: ProxySort = ProxySort.NEWEST,
        group: ProxyGroupFilter = ProxyGroupFilter.All
    ): List<Long> = ProxyListQuery.apply(proxies, checks, query, sort, group).map { it.id }

    @Test
    fun newestFirstIsTheDefault() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids())
    }

    @Test
    fun sortByNameUsesLabelOrAddressIgnoringCase() {
        // "203.0.113.4:3128" < "Alpha" < "beta.example:8080" < "gamma"
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(sort = ProxySort.NAME))
    }

    @Test
    fun sortByLatencyPutsFailedAndUncheckedLast() {
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(sort = ProxySort.LATENCY))
    }

    @Test
    fun sortByTypeThenCountry() {
        // SOCKS5, HTTP (beta, delta: no country, by name), HTTPS
        assertEquals(listOf(1L, 4L, 2L, 3L), ids(sort = ProxySort.TYPE_COUNTRY))
    }

    @Test
    fun searchMatchesLabelHostGroupCountryAndExitIp() {
        assertEquals(listOf(1L), ids(query = "alp"))
        assertEquals(listOf(2L), ids(query = "BETA.example:80"))
        assertEquals(listOf(4L, 1L), ids(query = "work"))
        assertEquals(listOf(3L), ids(query = "nl"))
        assertEquals(listOf(1L), ids(query = "113.200"))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(query = "  "))
    }

    @Test
    fun searchNeverMatchesCredentials() {
        assertEquals(emptyList<Long>(), ids(query = "secret"))
    }

    @Test
    fun groupFilterSelectsGroupOrUngrouped() {
        assertEquals(listOf(4L, 1L), ids(group = ProxyGroupFilter.Named("Work")))
        assertEquals(listOf(2L), ids(group = ProxyGroupFilter.Ungrouped))
        assertEquals(listOf(4L), ids(query = "113.4", group = ProxyGroupFilter.Named("Work")))
    }

    @Test
    fun missingGroupFallsBackToAll() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(group = ProxyGroupFilter.Named("Gone")))
    }

    @Test
    fun groupsAreDistinctAndSorted() {
        val withBlank = proxies + ProxyEntity(id = 5L, host = "192.0.2.5", port = 1, type = ProxyType.HTTP, groupName = " ")
        assertEquals(listOf("Home", "Work"), ProxyListQuery.groups(withBlank))
    }

    @Test
    fun groupFilterRoundTripsThroughText() {
        listOf(ProxyGroupFilter.All, ProxyGroupFilter.Ungrouped, ProxyGroupFilter.Named("a:b"))
            .forEach { filter -> assertEquals(filter, ProxyGroupFilter.decode(filter.encode())) }
        assertEquals(ProxyGroupFilter.All, ProxyGroupFilter.decode(null))
        assertEquals(ProxyGroupFilter.All, ProxyGroupFilter.decode("garbage"))
    }
}
