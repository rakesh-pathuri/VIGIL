package com.vigil.security.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vigil_secure_prefs"
        private const val KEY_ALIAS = "VigilMasterKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_GSB_API_KEY = "gsb_api_key_enc"
        private const val KEY_VT_API_KEY = "vt_api_key_enc"
        private const val KEY_LOCAL_HEURISTICS_ENABLED = "local_heuristics_enabled"
        private const val KEY_SHOW_OVERLAY_ON_SAFE = "show_overlay_on_safe"
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Graceful fallback handled if keystore unavailable
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Exception) {
            null
        }
    }

    private fun encrypt(plainText: String): String {
        return try {
            val key = getSecretKey() ?: return plainText
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    private fun decrypt(encryptedBase64: String): String {
        return try {
            val key = getSecretKey() ?: return encryptedBase64
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return ""
            val iv = ByteArray(12)
            val cipherBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var isLocalHeuristicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_HEURISTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_HEURISTICS_ENABLED, value).apply()

    var showOverlayOnSafe: Boolean
        get() = prefs.getBoolean(KEY_SHOW_OVERLAY_ON_SAFE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_OVERLAY_ON_SAFE, value).apply()

    var googleSafeBrowsingApiKey: String
        get() {
            val raw = prefs.getString(KEY_GSB_API_KEY, "") ?: ""
            return if (raw.isNotEmpty()) decrypt(raw) else ""
        }
        set(value) {
            val encrypted = if (value.isNotEmpty()) encrypt(value) else ""
            prefs.edit().putString(KEY_GSB_API_KEY, encrypted).apply()
        }

    var virusTotalApiKey: String
        get() {
            val raw = prefs.getString(KEY_VT_API_KEY, "") ?: ""
            return if (raw.isNotEmpty()) decrypt(raw) else ""
        }
        set(value) {
            val encrypted = if (value.isNotEmpty()) encrypt(value) else ""
            prefs.edit().putString(KEY_VT_API_KEY, encrypted).apply()
        }
}
