package com.siftalpha.studio.cloud

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.siftalpha.cloud.agent.CloudCredential
import com.siftalpha.cloud.agent.CloudCredentialStore
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android-only CloudCredentialStore backed by an Android Keystore AES/GCM key. */
class AndroidCloudCredentialStore(context: Context) : CloudCredentialStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun get(serverId: String): CloudCredential? {
        val packed = prefs.getString(prefKey(serverId), null) ?: return null
        return runCatching {
            decrypt(packed)
                ?.let(CloudCredentialPayloadCodec::decode)
                ?.takeIf { it.serverId == serverId }
        }.getOrNull()
    }

    override fun save(credential: CloudCredential) {
        require(credential.serverId.isNotBlank()) { "serverId must not be blank" }
        require(credential.baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(credential.bearerToken.isNotBlank()) { "bearerToken must not be blank" }
        prefs.edit()
            .putString(prefKey(credential.serverId), encrypt(CloudCredentialPayloadCodec.encode(credential)))
            .apply()
    }

    override fun delete(serverId: String) {
        if (serverId.isBlank()) return
        prefs.edit().remove(prefKey(serverId)).apply()
    }

    private fun encrypt(payload: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP)
        return "$iv:$ciphertext"
    }

    private fun decrypt(packed: String): ByteArray? {
        val parts = packed.split(':', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.DEFAULT)
        val ciphertext = Base64.decode(parts[1], Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun prefKey(serverId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(serverId.toByteArray(Charsets.UTF_8))
        return "credential:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    companion object {
        const val PREFS_NAME = "siftalpha_cloud_credentials_v1"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "siftalpha_cloud_credentials_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

