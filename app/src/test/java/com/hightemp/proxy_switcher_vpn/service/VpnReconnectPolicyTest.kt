package com.hightemp.proxy_switcher_vpn.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnReconnectPolicyTest {
    @Test
    fun backoffDoublesUntilMaximum() {
        val policy = VpnReconnectPolicy(
            monitorFailureThreshold = 3,
            maxReconnectAttempts = 5,
            initialBackoffMillis = 1_000L,
            maxBackoffMillis = 5_000L
        )

        assertEquals(1_000L, policy.backoffForAttempt(1))
        assertEquals(2_000L, policy.backoffForAttempt(2))
        assertEquals(4_000L, policy.backoffForAttempt(3))
        assertEquals(5_000L, policy.backoffForAttempt(4))
        assertEquals(5_000L, policy.backoffForAttempt(5))
    }

    @Test
    fun boundedPolicyStopsAfterMaxAttempts() {
        val policy = VpnReconnectPolicy(maxReconnectAttempts = 2)

        assertTrue(policy.hasAttempt(1))
        assertTrue(policy.hasAttempt(2))
        assertFalse(policy.hasAttempt(3))
        assertEquals("2", policy.attemptBudgetLabel)
    }

    @Test
    fun unlimitedPolicyAlwaysAllowsAnotherAttempt() {
        val policy = VpnReconnectPolicy(
            maxReconnectAttempts = 2,
            unlimitedReconnectAttempts = true
        )

        assertTrue(policy.hasAttempt(3))
        assertTrue(policy.hasAttempt(1_000))
        assertEquals("unlimited", policy.attemptBudgetLabel)
    }

    @Test
    fun invalidPolicyValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VpnReconnectPolicy(monitorFailureThreshold = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VpnReconnectPolicy(maxReconnectAttempts = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VpnReconnectPolicy(initialBackoffMillis = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VpnReconnectPolicy(
                initialBackoffMillis = 2_000L,
                maxBackoffMillis = 1_000L
            )
        }
    }
}
