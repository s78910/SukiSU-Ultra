package com.sukisu.ultra.ui.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object HiddenModuleStore {
    private const val PREFS_NAME = "module_visibility_secure"
    private const val KEY_ALIAS = "module_visibility_key_v1"
    private const val KEY_HIDDEN_IDS = "hidden_ids"
    private const val KEY_PASSWORD = "password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256

    fun getHiddenModuleIds(context: Context): Set<String> {
        val payload = getEncrypted(context, KEY_HIDDEN_IDS) ?: return emptySet()
        return runCatching {
            val array = JSONArray(payload)
            buildSet {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
        }.getOrDefault(emptySet())
    }

    fun setHiddenModuleIds(context: Context, ids: Set<String>) {
        val payload = JSONArray(ids.sorted()).toString()
        putEncrypted(context, KEY_HIDDEN_IDS, payload)
    }

    fun hasPassword(context: Context): Boolean = context.securePrefs().contains(KEY_PASSWORD)

    fun setPassword(context: Context, password: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hashPassword(password, salt)
        putEncrypted(context, KEY_PASSWORD, "${salt.encodeBase64()}:${hash.encodeBase64()}")
    }

    fun verifyPassword(context: Context, password: String): Boolean {
        val payload = getEncrypted(context, KEY_PASSWORD) ?: return false
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) return false
        val salt = parts[0].decodeBase64() ?: return false
        val storedHash = parts[1].decodeBase64() ?: return false
        val computedHash = hashPassword(password, salt)
        return storedHash.contentEquals(computedHash)
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun putEncrypted(context: Context, key: String, value: String) {
        val encryptedValue = encrypt(value)
        context.securePrefs().edit().putString(key, encryptedValue).apply()
    }

    private fun getEncrypted(context: Context, key: String): String? {
        val encryptedValue = context.securePrefs().getString(key, null) ?: return null
        return decrypt(encryptedValue)
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encryptedBytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return "${cipher.iv.encodeBase64()}:${encryptedBytes.encodeBase64()}"
    }

    private fun decrypt(value: String): String? {
        val parts = value.split(":", limit = 2)
        if (parts.size != 2) return null
        val iv = parts[0].decodeBase64() ?: return null
        val encryptedBytes = parts[1].decodeBase64() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH, iv)
                )
            }
            String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun Context.securePrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray? = runCatching {
        Base64.decode(this, Base64.NO_WRAP)
    }.getOrNull()
}
