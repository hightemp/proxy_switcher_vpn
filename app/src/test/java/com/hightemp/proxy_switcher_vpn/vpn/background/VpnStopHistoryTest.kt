package com.hightemp.proxy_switcher_vpn.vpn.background

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStopHistoryTest {
    @Test
    fun noStopIsRecordedByDefault() = runTest {
        val history = VpnStopHistory(FakePreferencesDataStore())

        assertNull(history.lastStop.first())
        assertNull(history.lastSeenProcessExitMillis.first())
    }

    @Test
    fun recordsLastStopWithReason() = runTest {
        val history = VpnStopHistory(FakePreferencesDataStore())

        history.record(VpnStopReason.USER, "VPN service stopped.", timestampMillis = 10L)
        history.record(VpnStopReason.FAIL_CLOSED, "Proxy did not recover.", timestampMillis = 20L)

        assertEquals(
            VpnStopRecord(
                timestampMillis = 20L,
                reason = VpnStopReason.FAIL_CLOSED,
                message = "Proxy did not recover."
            ),
            history.lastStop.first()
        )
    }

    @Test
    fun storedMessageMasksCredentialsAndIsLengthLimited() = runTest {
        val history = VpnStopHistory(FakePreferencesDataStore())

        history.record(
            reason = VpnStopReason.FAIL_CLOSED,
            message = "auth failed for alice with s3cret " + "x".repeat(1_000),
            sensitiveValues = listOf("alice", "s3cret", ""),
            timestampMillis = 1L
        )

        val message = history.lastStop.first()!!.message
        assertFalse(message.contains("alice"))
        assertFalse(message.contains("s3cret"))
        assertTrue(message.startsWith("auth failed for *** with ***"))
        assertEquals(VpnStopHistory.MAX_MESSAGE_LENGTH, message.length)
    }

    @Test
    fun tracksLastSeenProcessExit() = runTest {
        val history = VpnStopHistory(FakePreferencesDataStore())

        history.markProcessExitSeen(1_234L)

        assertEquals(1_234L, history.lastSeenProcessExitMillis.first())
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
