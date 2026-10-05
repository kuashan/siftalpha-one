package com.siftalpha.studio.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siftalpha.cloud.agent.CloudCredential
import com.siftalpha.cloud.agent.CloudCredentialStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCloudCredentialStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var serverId: String
    private lateinit var store: CloudCredentialStore

    @Before
    fun setUp() {
        serverId = "instrumentation-${UUID.randomUUID()}"
        store = AndroidCloudCredentialStore(context)
    }

    @After
    fun tearDown() {
        store.delete(serverId)
    }

    @Test
    fun saveReadAndDeleteUsesPlatformNeutralContract() {
        val credential = credential()

        store.save(credential)

        assertEquals(credential, store.get(serverId))
        store.delete(serverId)
        assertNull(store.get(serverId))
    }

    @Test
    fun cloudNamespaceIsSeparateAndStoredValuesDoNotContainBearerToken() {
        val credential = credential()
        store.save(credential)
        val prefs = context.getSharedPreferences(
            AndroidCloudCredentialStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        assertNotEquals("siftalpha_project_secrets_v1", AndroidCloudCredentialStore.PREFS_NAME)
        assertTrue(prefs.all.isNotEmpty())
        assertFalse(prefs.all.values.filterIsInstance<String>().any { it.contains(credential.bearerToken) })
    }

    @Test
    fun corruptedCiphertextFailsClosed() {
        val credential = credential()
        store.save(credential)
        val prefs = context.getSharedPreferences(
            AndroidCloudCredentialStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        prefs.edit().putString(
            prefs.all.keys.single(),
            "corrupted-ciphertext",
        ).commit()

        assertNull(store.get(serverId))
    }

    private fun credential(): CloudCredential = CloudCredential(
        serverId = serverId,
        baseUrl = "https://198.51.100.10:8443",
        bearerToken = UUID.randomUUID().toString(),
    )
}

