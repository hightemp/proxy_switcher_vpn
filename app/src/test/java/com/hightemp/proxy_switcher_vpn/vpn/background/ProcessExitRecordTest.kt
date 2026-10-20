package com.hightemp.proxy_switcher_vpn.vpn.background

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitRecordTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun sigkillWithForegroundServiceIsSystemKillWhileVpnActive() {
        val record = ProcessExitRecord(
            timestampMillis = 1_760_788_067_000L,
            reason = ProcessExitRecord.REASON_SIGNALED,
            status = 9,
            importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
        )

        assertTrue(record.killedWhileServiceInForeground)
        assertEquals(
            "2025-10-18 11:47 · killed by system (signal 9) while the VPN service was in foreground",
            record.summary(utc)
        )
    }

    @Test
    fun lowMemoryKillIsReported() {
        val record = ProcessExitRecord(
            timestampMillis = 0L,
            reason = ProcessExitRecord.REASON_LOW_MEMORY,
            status = 0,
            importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
        )

        assertTrue(record.killedWhileServiceInForeground)
        assertTrue(record.summary(utc).contains("killed by low memory killer"))
    }

    @Test
    fun cachedProcessKillIsNotVpnRelated() {
        val record = ProcessExitRecord(
            timestampMillis = 0L,
            reason = ProcessExitRecord.REASON_SIGNALED,
            status = 9,
            importance = ProcessExitRecord.IMPORTANCE_CACHED
        )

        assertFalse(record.killedWhileServiceInForeground)
        assertEquals("1970-01-01 00:00 · killed by system (signal 9) in background", record.summary(utc))
    }

    @Test
    fun updatesAndUserActionsAreNotSystemKills() {
        val updated = ProcessExitRecord(
            timestampMillis = 0L,
            reason = ProcessExitRecord.REASON_PACKAGE_UPDATED,
            status = 0,
            importance = ProcessExitRecord.IMPORTANCE_FOREGROUND
        )
        val forceStopped = ProcessExitRecord(
            timestampMillis = 0L,
            reason = ProcessExitRecord.REASON_USER_REQUESTED,
            status = 0,
            importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
        )

        assertFalse(updated.killedWhileServiceInForeground)
        assertFalse(forceStopped.killedWhileServiceInForeground)
        assertTrue(updated.summary(utc).contains("app updated"))
        assertTrue(forceStopped.summary(utc).contains("stopped by user"))
    }

    @Test
    fun crashesAreReportedAsCrashes() {
        val record = ProcessExitRecord(
            timestampMillis = 0L,
            reason = ProcessExitRecord.REASON_CRASH_NATIVE,
            status = 6,
            importance = ProcessExitRecord.IMPORTANCE_FOREGROUND_SERVICE
        )

        assertTrue(record.isCrash)
        assertTrue(record.summary(utc).contains("native crash"))
    }

    @Test
    fun newExitIsOnlyReportedOnce() {
        val older = ProcessExitRecord(100L, ProcessExitRecord.REASON_SIGNALED, 9, 125)
        val newer = ProcessExitRecord(200L, ProcessExitRecord.REASON_SIGNALED, 9, 125)

        assertEquals(newer, ProcessExitRecord.unseenLatest(listOf(older, newer), lastSeenMillis = null))
        assertEquals(newer, ProcessExitRecord.unseenLatest(listOf(newer, older), lastSeenMillis = 100L))
        assertNull(ProcessExitRecord.unseenLatest(listOf(older, newer), lastSeenMillis = 200L))
        assertNull(ProcessExitRecord.unseenLatest(emptyList(), lastSeenMillis = null))
    }
}
