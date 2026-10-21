package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLineImportTest {
    private val auto = ProxyLineImportOptions(protocol = null, fieldOrder = ProxyFieldOrder.HOST_FIRST)

    @Test
    fun blankLinesAndCommentsAreSkipped() {
        val preview = ProxyLineImport.preview(
            text = "# provider list\n\n  socks5://192.0.2.1:1080  \r\n// note\nhttp://192.0.2.2:8080\n",
            options = auto,
            existing = emptyList()
        )

        assertEquals(listOf(3, 5), preview.lines.map { it.lineNumber })
        assertEquals(listOf(ProxyType.SOCKS5, ProxyType.HTTP), preview.lines.map { it.proxy?.type })
        assertTrue(preview.lines.all { it.status == ProxyImportStatus.NEW })
    }

    @Test
    fun parserErrorsAreKeptPerLine() {
        val preview = ProxyLineImport.preview("192.0.2.1:99999\nsocks4://192.0.2.1:1080", auto, emptyList())

        assertEquals(listOf(ProxyImportStatus.ERROR, ProxyImportStatus.ERROR), preview.lines.map { it.status })
        assertEquals(UserText.of(R.string.proxy_string_error_port_range), preview.lines[0].message)
        assertEquals(UserText.of(R.string.proxy_string_error_socks4), preview.lines[1].message)
        assertEquals(0, preview.importable.size)
    }

    @Test
    fun linesWithoutProtocolNeedDetectionInAutoMode() {
        val preview = ProxyLineImport.preview("192.0.2.1:8080", auto, emptyList())

        val line = preview.lines.single()
        assertEquals(ProxyImportStatus.NEW, line.status)
        assertNull(line.proxy?.type)
        assertTrue(line.needsDetection)
        assertEquals(1, preview.needsDetectionCount)
    }

    @Test
    fun fixedProtocolAppliesOnlyToLinesWithoutProtocol() {
        val preview = ProxyLineImport.preview(
            "192.0.2.1:8080\nhttps://192.0.2.2:443",
            auto.copy(protocol = ProxyType.SOCKS5),
            emptyList()
        )

        assertEquals(listOf(ProxyType.SOCKS5, ProxyType.HTTPS), preview.lines.map { it.proxy?.type })
        assertFalse(preview.lines.any { it.needsDetection })
    }

    @Test
    fun ambiguousLinesFollowTheBatchFieldOrder() {
        val text = "192.0.2.1:1080:198.51.100.2:3128"

        val hostFirst = ProxyLineImport.preview(text, auto, emptyList())
        val credentialsFirst = ProxyLineImport.preview(
            text,
            auto.copy(fieldOrder = ProxyFieldOrder.CREDENTIALS_FIRST),
            emptyList()
        )

        assertTrue(hostFirst.hasAmbiguous)
        assertTrue(hostFirst.lines.single().ambiguous)
        assertEquals("192.0.2.1", hostFirst.lines.single().proxy?.host)
        assertEquals("198.51.100.2", credentialsFirst.lines.single().proxy?.host)
    }

    @Test
    fun repeatedLinesInBatchKeepTheLastOne() {
        val preview = ProxyLineImport.preview(
            "socks5://u:old@192.0.2.1:1080\nsocks5://u:new@192.0.2.1:1080\nsocks5://other@192.0.2.1:1080",
            auto,
            emptyList()
        )

        assertEquals(
            listOf(ProxyImportStatus.DUPLICATE, ProxyImportStatus.NEW, ProxyImportStatus.NEW),
            preview.lines.map { it.status }
        )
        assertEquals("new", preview.importable.first().proxy?.password)
    }

    @Test
    fun existingProxyIsUpdatedWithPasswordAndProtocol() {
        val existing = ProxyEntity(id = 7L, host = "proxy.example", port = 1080, type = ProxyType.HTTP, username = "u", password = "old", label = "Main")

        val preview = ProxyLineImport.preview("socks5://u:new@PROXY.example:1080", auto, listOf(existing))

        val line = preview.lines.single()
        assertEquals(ProxyImportStatus.UPDATE, line.status)
        assertEquals(7L, line.existingId)
        assertEquals(
            existing.copy(type = ProxyType.SOCKS5, password = "new"),
            ProxyLineImport.updatedProxy(existing, line.proxy!!)
        )
    }

    @Test
    fun existingProxyKeepsItsProtocolWhenLineHasNoneInAutoMode() {
        val existing = ProxyEntity(id = 7L, host = "192.0.2.1", port = 1080, type = ProxyType.SOCKS5, username = "u", password = "p")

        val unchanged = ProxyLineImport.preview("192.0.2.1:1080:u:p", auto, listOf(existing)).lines.single()
        val updated = ProxyLineImport.preview("192.0.2.1:1080:u:q", auto, listOf(existing)).lines.single()

        assertEquals(ProxyImportStatus.UNCHANGED, unchanged.status)
        assertEquals(ProxyImportStatus.UPDATE, updated.status)
        assertEquals(ProxyType.SOCKS5, updated.proxy?.type)
        assertFalse(updated.needsDetection)
    }

    @Test
    fun recordsAboveTheLimitAreDropped() {
        val text = (1..ProxyLineImport.MAX_RECORDS + 5).joinToString("\n") { "http://192.0.2.1:${it}" }

        val preview = ProxyLineImport.preview(text, auto, emptyList())

        assertEquals(ProxyLineImport.MAX_RECORDS, preview.lines.size)
        assertEquals(5, preview.droppedCount)
    }

    @Test
    fun displayTextNeverContainsCredentials() {
        val line = ProxyLineImport.preview("socks5://secret-user:secret-pass@192.0.2.1:1080", auto, emptyList())
            .lines.single()

        val display = ProxyLineImport.displayText(line.proxy!!)
        assertEquals("SOCKS5  192.0.2.1:1080", display)
        assertFalse(display.contains("secret"))
        assertFalse(line.toString().contains("secret"))
    }
}
