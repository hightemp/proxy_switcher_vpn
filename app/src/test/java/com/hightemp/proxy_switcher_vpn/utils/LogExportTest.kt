package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportTest {
    private val proxies = listOf(
        ProxyEntity(id = 1L, host = "proxy.example", port = 1080, type = ProxyType.SOCKS5, username = "alice", password = "s3cret!", label = "Home Office"),
        ProxyEntity(id = 2L, host = "203.0.113.50", port = 8080, type = ProxyType.HTTP)
    )

    @Test
    fun credentialsAreAlwaysHidden() {
        val redactor = LogRedactor(proxies, hideAddresses = false)

        val text = redactor.redact("auth alice:s3cret! failed for proxy.example:1080")

        assertEquals("auth ***:*** failed for proxy.example:1080", text)
    }

    @Test
    fun proxyHostsAndLabelsBecomeStableTags() {
        val redactor = LogRedactor(proxies, hideAddresses = true)

        assertEquals(
            "VPN start requested for Proxy proxy-1 (SOCKS5 proxy-1:1080).",
            redactor.redact("VPN start requested for Proxy Home Office (SOCKS5 proxy.example:1080).")
        )
        assertEquals("monitor proxy-2:8080 ok", redactor.redact("monitor 203.0.113.50:8080 ok"))
    }

    @Test
    fun publicIpsAndDomainsAreTaggedConsistently() {
        val redactor = LogRedactor(proxies, hideAddresses = true)

        val first = redactor.redact("DNS query for www.example.com A via 198.51.100.7.")
        val second = redactor.redact("connect 198.51.100.7:443 for WWW.example.com and api.example.org")

        assertEquals("DNS query for domain-1 A via ip-1.", first)
        assertEquals("connect ip-1:443 for domain-1 and domain-2", second)
    }

    @Test
    fun privateAddressesClassNamesAndFilesStayReadable() {
        val redactor = LogRedactor(proxies, hideAddresses = true)
        val line = "tun 172.19.0.1/30 dns 10.0.2.3 lo 127.0.0.1 routes 0.0.0.0/1 128.0.0.0/1; " +
            "java.net.SocketTimeoutException at ProxyVpnService.kt in sing-box.log, config.json v1.12.4"

        assertEquals(line, redactor.redact(line))
    }

    @Test
    fun addressesStayWhenSwitchIsOff() {
        val redactor = LogRedactor(proxies, hideAddresses = false)
        val line = "DNS query for www.example.com via 198.51.100.7 using Home Office"

        assertEquals(line, redactor.redact(line))
    }

    @Test
    fun reportHasHeaderDiagnosticsAndLogs() {
        val header = LogExportHeader(
            appVersion = "0.3.1 (301)",
            androidVersion = "15 (API 35)",
            device = "Google sdk_gphone64_x86_64",
            language = "ru",
            generatedAtMillis = 0L
        )
        val entries = listOf(
            LogEntry(1_000L, VpnEngineLogLevel.INFO, LogType.VPN, "VPN start requested for Home Office."),
            LogEntry(2_000L, VpnEngineLogLevel.ERROR, LogType.DNS, "DNS query failed for secret.example.net.")
        )

        val report = LogExport.build(
            header = header,
            diagnostics = listOf("Selected route" to "Home Office", "IPv6" to "unsupported_disabled"),
            entries = entries,
            redactor = LogRedactor(proxies, hideAddresses = true),
            timeZone = TimeZone.getTimeZone("UTC")
        )

        assertTrue(report, report.startsWith("Proxy Switcher VPN log export\n"))
        assertTrue(report, report.contains("App version: 0.3.1 (301)"))
        assertTrue(report, report.contains("Addresses and domains: hidden"))
        assertTrue(report, report.contains("Selected route: proxy-1"))
        assertTrue(report, report.contains("1970-01-01 00:00:01.000 INFO VPN VPN start requested for proxy-1."))
        assertTrue(report, report.contains("1970-01-01 00:00:02.000 ERROR DNS DNS query failed for domain-1."))
        assertFalse(report, report.contains("secret.example.net"))
        assertFalse(report, report.contains("Home Office"))
    }

    @Test
    fun fileNameUsesTimestamp() {
        assertEquals(
            "proxy-switcher-vpn-logs-19700101-000000.txt",
            LogExport.fileName(0L, TimeZone.getTimeZone("UTC"))
        )
    }
}
