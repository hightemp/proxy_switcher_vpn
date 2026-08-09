package com.hightemp.proxy_switcher_vpn.service

data class VpnReconnectPolicy(
    val monitorFailureThreshold: Int = DEFAULT_MONITOR_FAILURE_THRESHOLD,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    val initialBackoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MILLIS,
    val maxBackoffMillis: Long = DEFAULT_MAX_BACKOFF_MILLIS,
    val unlimitedReconnectAttempts: Boolean = false
) {
    init {
        require(monitorFailureThreshold > 0) {
            "monitorFailureThreshold must be positive."
        }
        require(maxReconnectAttempts > 0) {
            "maxReconnectAttempts must be positive."
        }
        require(initialBackoffMillis > 0L) {
            "initialBackoffMillis must be positive."
        }
        require(maxBackoffMillis >= initialBackoffMillis) {
            "maxBackoffMillis must be greater than or equal to initialBackoffMillis."
        }
    }

    /** Human readable attempt budget used in logs and notifications. */
    val attemptBudgetLabel: String
        get() = if (unlimitedReconnectAttempts) {
            UNLIMITED_ATTEMPTS_LABEL
        } else {
            maxReconnectAttempts.toString()
        }

    fun hasAttempt(attempt: Int): Boolean {
        return unlimitedReconnectAttempts || attempt <= maxReconnectAttempts
    }

    fun backoffForAttempt(attempt: Int): Long {
        require(attempt > 0) {
            "attempt must be positive."
        }
        var delay = initialBackoffMillis
        repeat(attempt - 1) {
            delay = (delay * 2L).coerceAtMost(maxBackoffMillis)
        }
        return delay
    }

    private companion object {
        const val DEFAULT_MONITOR_FAILURE_THRESHOLD = 3
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
        const val DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000L
        const val DEFAULT_MAX_BACKOFF_MILLIS = 15_000L
        const val UNLIMITED_ATTEMPTS_LABEL = "unlimited"
    }
}
