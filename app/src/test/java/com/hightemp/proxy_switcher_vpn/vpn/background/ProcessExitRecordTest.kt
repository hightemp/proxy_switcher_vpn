package com.hightemp.proxy_switcher_vpn.vpn.background

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
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
            UserText.of(R.string.exit_summary_foreground, "2025-10-18 11:47", UserText.of(R.string.exit_signaled, 9)),
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
        assertEquals(
            UserText.of(R.string.exit_summary_foreground, "1970-01-01 00:00", UserText.of(R.string.exit_low_memory)),
            record.summary(utc)
        )
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
        assertEquals(
            UserText.of(R.string.exit_summary_background, "1970-01-01 00:00", UserText.of(R.string.exit_signaled, 9)),
            record.summary(utc)
        )
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
        assertEquals(
            UserText.of(R.string.diag_time_and_text, "1970-01-01 00:00", UserText.of(R.string.exit_package_updated)),
            updated.summary(utc)
        )
        assertEquals(
            UserText.of(R.string.diag_time_and_text, "1970-01-01 00:00", UserText.of(R.string.exit_user)),
            forceStopped.summary(utc)
        )
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
        assertEquals(
            UserText.of(R.string.exit_summary_foreground, "1970-01-01 00:00", UserText.of(R.string.exit_crash_native)),
            record.summary(utc)
        )
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
