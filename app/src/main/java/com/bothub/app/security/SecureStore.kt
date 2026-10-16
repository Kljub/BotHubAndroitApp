package com.bothub.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets of the pairing (device token, Guard secret) in the app's
 * preferences, encrypted with an AES key of the AndroidKeyStore (AES-GCM).
 * Plain values (server address, names) are kept as they are.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("bothub", Context.MODE_PRIVATE)
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val key: SecretKey
        get() = (ks.getKey("bothub-store", null) as SecretKey?) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("bothub-store", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }

    fun putSecret(name: String, value: String?) {
        if (value == null) return prefs.edit().remove(name).apply()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val out = c.iv + c.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun secret(name: String): String? {
        val raw = prefs.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12)) }
            String(c.doFinal(raw, 12, raw.size - 12))
        }.getOrNull()
    }

    fun put(name: String, value: String?) = prefs.edit().apply { if (value == null) remove(name) else putString(name, value) }.apply()
    fun get(name: String): String? = prefs.getString(name, null)
    fun putLong(name: String, value: Long) = prefs.edit().putLong(name, value).apply()
    fun getLong(name: String): Long = prefs.getLong(name, 0)
    fun clear() = prefs.edit().clear().apply()
}
