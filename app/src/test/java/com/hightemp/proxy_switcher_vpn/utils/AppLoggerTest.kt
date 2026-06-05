package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLoggerTest {
    @Before
    fun setUp() {
        AppLogger.clear()
    }

    @After
    fun tearDown() {
        AppLogger.clear()
    }

    @Test
    fun logsCanBeObservedAndCleared() {
        AppLogger.info(
            message = "VPN started.",
            timestampMillis = 100L,
            type = LogType.VPN
        )

        val entry = AppLogger.logs.value.single()
        assertEquals(100L, entry.timestampMillis)
        assertEquals(VpnEngineLogLevel.INFO, entry.level)
        assertEquals(LogType.VPN, entry.type)
        assertEquals("VPN started.", entry.message)

        AppLogger.clear()
        assertTrue(AppLogger.logs.value.isEmpty())
    }

    @Test
    fun logsCanBeFilteredByLevelAndType() {
        AppLogger.info("DNS query.", type = LogType.DNS)
        AppLogger.warning("UDP blocked.", type = LogType.UDP)

        assertEquals(1, AppLogger.filteredLogs(type = LogType.DNS).size)
        assertEquals(1, AppLogger.filteredLogs(level = VpnEngineLogLevel.WARNING).size)
        assertEquals(
            "UDP blocked.",
            AppLogger.filteredLogs(
                level = VpnEngineLogLevel.WARNING,
                type = LogType.UDP
            ).single().message
        )
    }

    @Test
    fun sensitiveValuesAreMasked() {
        AppLogger.info(
            message = "Proxy password secret-password was used.",
            sensitiveValues = listOf("secret-password")
        )

        val message = AppLogger.logs.value.single().message
        assertFalse(message.contains("secret-password"))
        assertTrue(message.contains("***"))
    }

    @Test
    fun historyIsBounded() {
        repeat(1_005) { index ->
            AppLogger.debug("entry-$index")
        }

        val logs = AppLogger.logs.value
        assertEquals(1_000, logs.size)
        assertEquals("entry-5", logs.first().message)
        assertEquals("entry-1004", logs.last().message)
    }
}
