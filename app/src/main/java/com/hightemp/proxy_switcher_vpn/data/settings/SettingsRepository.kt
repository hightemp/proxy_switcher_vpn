package com.hightemp.proxy_switcher_vpn.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            val privacyDisclosureAccepted =
                preferences[PRIVACY_DISCLOSURE_ACCEPTED] ?: false
            AppSettings(
                selectedProxyId = preferences[SELECTED_PROXY_ID],
                domainDestinationLoggingEnabled =
                    (preferences[DOMAIN_DESTINATION_LOGGING_ENABLED] ?: false) &&
                        privacyDisclosureAccepted,
                privacyDisclosureAccepted = privacyDisclosureAccepted
            )
        }

    suspend fun setSelectedProxyId(proxyId: Long?) {
        dataStore.edit { preferences ->
            if (proxyId == null) {
                preferences.remove(SELECTED_PROXY_ID)
            } else {
                preferences[SELECTED_PROXY_ID] = proxyId
            }
        }
    }

    suspend fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { preferences ->
            preferences[PRIVACY_DISCLOSURE_ACCEPTED] = accepted
            if (!accepted) {
                preferences[DOMAIN_DESTINATION_LOGGING_ENABLED] = false
            }
        }
    }

    suspend fun setDomainDestinationLoggingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            val privacyDisclosureAccepted =
                preferences[PRIVACY_DISCLOSURE_ACCEPTED] ?: false
            preferences[DOMAIN_DESTINATION_LOGGING_ENABLED] =
                enabled && privacyDisclosureAccepted
        }
    }

    private companion object {
        val SELECTED_PROXY_ID = longPreferencesKey("selected_proxy_id")
        val DOMAIN_DESTINATION_LOGGING_ENABLED =
            booleanPreferencesKey("domain_destination_logging_enabled")
        val PRIVACY_DISCLOSURE_ACCEPTED =
            booleanPreferencesKey("privacy_disclosure_accepted")
    }
}
