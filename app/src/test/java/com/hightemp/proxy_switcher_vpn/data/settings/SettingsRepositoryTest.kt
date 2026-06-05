package com.hightemp.proxy_switcher_vpn.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun settingsDefaultsDisableSensitiveLogging() = runTest {
        val holder = createRepository("defaults.preferences_pb")

        try {
            val settings = holder.repository.settings.first()

            assertNull(settings.selectedProxyId)
            assertFalse(settings.domainDestinationLoggingEnabled)
            assertFalse(settings.privacyDisclosureAccepted)
        } finally {
            holder.scope.cancel()
        }
    }

    @Test
    fun domainDestinationLoggingRequiresPrivacyDisclosure() = runTest {
        val holder = createRepository("privacy-gate.preferences_pb")

        try {
            holder.repository.setDomainDestinationLoggingEnabled(true)
            assertFalse(holder.repository.settings.first().domainDestinationLoggingEnabled)

            holder.repository.setPrivacyDisclosureAccepted(true)
            holder.repository.setDomainDestinationLoggingEnabled(true)
            assertTrue(holder.repository.settings.first().domainDestinationLoggingEnabled)

            holder.repository.setPrivacyDisclosureAccepted(false)
            val settings = holder.repository.settings.first()
            assertFalse(settings.domainDestinationLoggingEnabled)
            assertFalse(settings.privacyDisclosureAccepted)
        } finally {
            holder.scope.cancel()
        }
    }

    @Test
    fun selectedProxyIdPersistsAcrossRepositoryRecreation() = runTest {
        val holder = createRepository("repository-recreation.preferences_pb")

        try {
            holder.repository.setSelectedProxyId(42L)

            val recreatedRepository = SettingsRepository(holder.dataStore)
            assertEquals(42L, recreatedRepository.settings.first().selectedProxyId)
        } finally {
            holder.scope.cancel()
        }
    }

    private fun createRepository(fileName: String): RepositoryHolder {
        return createRepository(temporaryFolder.newFile(fileName))
    }

    private fun createRepository(file: File): RepositoryHolder {
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return RepositoryHolder(
            repository = SettingsRepository(dataStore),
            dataStore = dataStore,
            scope = scope
        )
    }

    private data class RepositoryHolder(
        val repository: SettingsRepository,
        val dataStore: DataStore<Preferences>,
        val scope: TestScope
    )
}
