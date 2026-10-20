package com.hightemp.proxy_switcher_vpn.vpn.background

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hightemp.proxy_switcher_vpn.R
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class VpnStopReason(@StringRes val label: Int) {
    USER(R.string.stop_reason_user),
    FAIL_CLOSED(R.string.stop_reason_fail_closed),
    PERMISSION_REVOKED(R.string.stop_reason_permission_revoked),
    SERVICE_DESTROYED(R.string.stop_reason_service_destroyed)
}

data class VpnStopRecord(
    val timestampMillis: Long,
    val reason: VpnStopReason,
    val message: String
)

/**
 * Keeps the last VPN stop reason across process restarts, so a stop is still
 * explainable after the in-memory logs are gone. A SIGKILL leaves no record
 * here; [ProcessExitHistory] covers that case.
 */
class VpnStopHistory @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val lastStop: Flow<VpnStopRecord?> = preferences.map { prefs ->
        val timestamp = prefs[LAST_STOP_AT] ?: return@map null
        val reason = prefs[LAST_STOP_REASON]
            ?.let { name -> VpnStopReason.entries.firstOrNull { it.name == name } }
            ?: return@map null
        VpnStopRecord(
            timestampMillis = timestamp,
            reason = reason,
            message = prefs[LAST_STOP_MESSAGE].orEmpty()
        )
    }

    val lastSeenProcessExitMillis: Flow<Long?> = preferences.map { it[LAST_SEEN_PROCESS_EXIT_AT] }

    suspend fun record(
        reason: VpnStopReason,
        message: String,
        sensitiveValues: Collection<String> = emptyList(),
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val safeMessage = sensitiveValues
            .filter { it.isNotBlank() }
            .fold(message) { masked, secret -> masked.replace(secret, MASKED_SECRET) }
            .take(MAX_MESSAGE_LENGTH)
        dataStore.edit { prefs ->
            prefs[LAST_STOP_AT] = timestampMillis
            prefs[LAST_STOP_REASON] = reason.name
            prefs[LAST_STOP_MESSAGE] = safeMessage
        }
    }

    suspend fun markProcessExitSeen(timestampMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_SEEN_PROCESS_EXIT_AT] = timestampMillis
        }
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 300
        private const val MASKED_SECRET = "***"
        private val LAST_STOP_AT = longPreferencesKey("last_vpn_stop_at")
        private val LAST_STOP_REASON = stringPreferencesKey("last_vpn_stop_reason")
        private val LAST_STOP_MESSAGE = stringPreferencesKey("last_vpn_stop_message")
        private val LAST_SEEN_PROCESS_EXIT_AT =
            longPreferencesKey("last_seen_process_exit_at")
    }
}
