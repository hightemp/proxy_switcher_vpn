package com.hightemp.proxy_switcher_vpn.proxy

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyCheckParsersTest {
    @Test
    fun cloudflareTraceGivesIpAndCountry() {
        val body = """
            fl=123abc
            h=www.cloudflare.com
            ip=203.0.113.7
            ts=1760000000.1
            loc=DE
            warp=off
        """.trimIndent()

        assertEquals(ExitInfo("203.0.113.7", "DE"), ProxyCheckParsers.parseCloudflareTrace(body))
    }

    @Test
    fun cloudflareTraceWithoutIpIsRejected() {
        assertNull(ProxyCheckParsers.parseCloudflareTrace("loc=DE\n"))
    }

    @Test
    fun unknownCloudflareCountryIsDropped() {
        assertEquals(
            ExitInfo("203.0.113.7", null),
            ProxyCheckParsers.parseCloudflareTrace("ip=203.0.113.7\nloc=XX\n")
        )
        assertEquals(
            ExitInfo("203.0.113.7", null),
            ProxyCheckParsers.parseCloudflareTrace("ip=203.0.113.7\nloc=T1\n")
        )
    }

    @Test
    fun ipinfoJsonGivesIpAndCountry() {
        val body = """{"ip":"198.51.100.4","city":"Paris","country":"fr","org":"AS0 Example"}"""

        assertEquals(ExitInfo("198.51.100.4", "FR"), ProxyCheckParsers.parseIpinfo(body))
    }

    @Test
    fun invalidIpinfoJsonIsRejected() {
        assertNull(ProxyCheckParsers.parseIpinfo("not json"))
        assertNull(ProxyCheckParsers.parseIpinfo("""{"country":"FR"}"""))
    }

    @Test
    fun countryCodeBecomesFlagEmoji() {
        assertEquals("🇩🇪", ProxyCheckParsers.countryFlag("DE"))
        assertEquals("🇯🇵", ProxyCheckParsers.countryFlag("jp"))
        assertNull(ProxyCheckParsers.countryFlag("D1"))
        assertNull(ProxyCheckParsers.countryFlag("DEU"))
        assertNull(ProxyCheckParsers.countryFlag(null))
    }

    @Test
    fun speedIsBitsPerSecond() {
        assertEquals(80_000_000L, ProxyCheckParsers.bitsPerSecond(bytes = 10_000_000L, millis = 1_000L))
        assertEquals(0L, ProxyCheckParsers.bitsPerSecond(bytes = 10L, millis = 0L))
    }

    @Test
    fun megabitsAreFormattedWithOneDecimal() {
        assertEquals("54.3", ProxyCheckParsers.formatMegabits(54_321_000L))
        assertEquals("0.8", ProxyCheckParsers.formatMegabits(800_000L))
        assertEquals("54,3", ProxyCheckParsers.formatMegabits(54_321_000L, java.util.Locale.forLanguageTag("ru")))
    }

    @Test
    fun httpResponseHeadAndChunkedBodyAreParsed() {
        val raw = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "7\r\nip=1.2.\r\n" +
            "a\r\n3.4\nloc=DE\r\n" +
            "0\r\n\r\n"

        val response = ProxyCheckParsers.readHttpResponse(ByteArrayInputStream(raw.toByteArray()), maxBodyBytes = 1024)

        assertEquals(200, response.statusCode)
        assertEquals("ip=1.2.3.4\nloc=DE", response.body)
    }

    @Test
    fun httpResponseWithContentLengthIsParsed() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello-extra"

        val response = ProxyCheckParsers.readHttpResponse(ByteArrayInputStream(raw.toByteArray()), maxBodyBytes = 1024)

        assertEquals(200, response.statusCode)
        assertEquals("hello", response.body)
    }

    @Test
    fun httpResponseBodyIsLimited() {
        val raw = "HTTP/1.1 200 OK\r\n\r\n" + "x".repeat(100)

        val response = ProxyCheckParsers.readHttpResponse(ByteArrayInputStream(raw.toByteArray()), maxBodyBytes = 10)

        assertEquals("x".repeat(10), response.body)
    }
}
