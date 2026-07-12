package com.resukisu.resukisu.ui.util

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class HiddenModuleSnapshot(
    val hiddenModuleIds: Set<String>,
    val shouldHideAllModules: Boolean,
    val hasPassword: Boolean
)

object HiddenModuleStore {
    private const val TAG = "HiddenModuleStore"
    private const val ROOT_DIR = "/data/adb/ksu"
    private const val STATE_FILE = "$ROOT_DIR/.s789_module_state"
    private const val AUTH_FILE = "$ROOT_DIR/bin/ksuda"
    private const val MASTER_KEY_FILE = "$ROOT_DIR/.s789_module_master"
    private const val BUSYBOX = "/data/adb/ksu/bin/busybox"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val NONCE_SIZE = 12
    private const val MASTER_KEY_SIZE = 32
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val ENVELOPE_VERSION = 1
    private const val PAYLOAD_VERSION = 1

    fun loadSnapshot(@Suppress("UNUSED_PARAMETER") context: Context): HiddenModuleSnapshot {
        val hasPassword = fileExists(AUTH_FILE)
        val payload = readEncryptedPayload(STATE_FILE, FilePurpose.State)

        return runCatching {
            val array = payload?.let { JSONObject(it).optJSONArray("hidden_ids") } ?: JSONArray()
            val hiddenIds = buildSet {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
            HiddenModuleSnapshot(
                hiddenModuleIds = hiddenIds,
                shouldHideAllModules = payload == null || !hasPassword,
                hasPassword = hasPassword
            )
        }.getOrElse {
            Log.e(TAG, "Failed to parse hidden module snapshot", it)
            HiddenModuleSnapshot(
                hiddenModuleIds = emptySet(),
                shouldHideAllModules = true,
                hasPassword = hasPassword
            )
        }
    }

    fun prepareForDebugMode(
        context: Context,
        fallbackHiddenIds: Set<String>
    ): HiddenModuleSnapshot {
        if (readEncryptedPayload(STATE_FILE, FilePurpose.State) == null) {
            writeState(fallbackHiddenIds)
        }
        return loadSnapshot(context)
    }

    fun setHiddenModuleIds(context: Context, ids: Set<String>): HiddenModuleSnapshot {
        writeState(ids)
        return loadSnapshot(context)
    }

    fun setPassword(@Suppress("UNUSED_PARAMETER") context: Context, password: String): Boolean {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hashPassword(password, salt)
        val payload = JSONObject()
            .put("version", PAYLOAD_VERSION)
            .put("salt", salt.encodeBase64())
            .put("hash", hash.encodeBase64())
            .toString()
        return writeEncryptedPayload(AUTH_FILE, FilePurpose.Auth, payload)
    }

    fun verifyPassword(@Suppress("UNUSED_PARAMETER") context: Context, password: String): Boolean {
        val payload = readEncryptedPayload(AUTH_FILE, FilePurpose.Auth) ?: return false
        return runCatching {
            val json = JSONObject(payload)
            val salt = json.optString("salt").decodeBase64() ?: return false
            val storedHash = json.optString("hash").decodeBase64() ?: return false
            val computedHash = hashPassword(password, salt)
            MessageDigest.isEqual(storedHash, computedHash)
        }.getOrElse {
            Log.e(TAG, "Failed to verify hidden module password", it)
            false
        }
    }

    private fun writeState(ids: Set<String>): Boolean {
        val payload = JSONObject()
            .put("version", PAYLOAD_VERSION)
            .put("hidden_ids", JSONArray(ids.sorted()))
            .toString()
        return writeEncryptedPayload(STATE_FILE, FilePurpose.State, payload)
    }

    private fun writeEncryptedPayload(path: String, purpose: FilePurpose, payload: String): Boolean {
        val masterKey = getOrCreateMasterKey() ?: return false
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val cipherText = runCatching {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    deriveFileKey(masterKey, purpose),
                    GCMParameterSpec(GCM_TAG_LENGTH, nonce)
                )
            }.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        }.getOrElse {
            Log.e(TAG, "Failed to encrypt payload for ${purpose.label}", it)
            return false
        }

        val envelope = JSONObject()
            .put("version", ENVELOPE_VERSION)
            .put("nonce", nonce.encodeBase64())
            .put("ciphertext", cipherText.encodeBase64())
            .toString()

        return writeRootFile(path, envelope.toByteArray(StandardCharsets.UTF_8))
    }

    private fun readEncryptedPayload(path: String, purpose: FilePurpose): String? {
        val envelopeBytes = readRootFile(path) ?: return null
        val masterKey = readMasterKey() ?: return null
        return runCatching {
            val envelope = JSONObject(String(envelopeBytes, StandardCharsets.UTF_8))
            if (envelope.optInt("version", -1) != ENVELOPE_VERSION) {
                return null
            }

            val nonce = envelope.optString("nonce").decodeBase64() ?: return null
            val cipherText = envelope.optString("ciphertext").decodeBase64() ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    deriveFileKey(masterKey, purpose),
                    GCMParameterSpec(GCM_TAG_LENGTH, nonce)
                )
            }
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        }.getOrElse {
            Log.e(TAG, "Failed to decrypt payload for ${purpose.label}", it)
            null
        }
    }

    private fun getOrCreateMasterKey(): ByteArray? {
        readMasterKey()?.let { return it }

        val key = ByteArray(MASTER_KEY_SIZE).also(SecureRandom()::nextBytes)
        return if (writeRootFile(MASTER_KEY_FILE, key)) {
            key
        } else {
            Log.e(TAG, "Failed to create hidden module master key")
            null
        }
    }

    private fun readMasterKey(): ByteArray? {
        val bytes = readRootFile(MASTER_KEY_FILE) ?: return null
        return bytes.takeIf { it.size == MASTER_KEY_SIZE } ?: run {
            Log.e(TAG, "Invalid hidden module master key length: ${bytes.size}")
            null
        }
    }

    private fun deriveFileKey(masterKey: ByteArray, purpose: FilePurpose): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(masterKey, "HmacSHA256"))
        }
        val derived = mac.doFinal(purpose.label.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(derived.copyOf(MASTER_KEY_SIZE), "AES")
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun fileExists(path: String): Boolean {
        val result = getRootShell().newJob().add("[ -f ${shellQuote(path)} ]").exec()
        return result.isSuccess
    }

    private fun readRootFile(path: String): ByteArray? {
        if (!fileExists(path)) {
            return null
        }

        val result = getRootShell().newJob()
            .add("$BUSYBOX base64 ${shellQuote(path)}")
            .to(ArrayList(), null)
            .exec()
        if (!result.isSuccess) {
            Log.e(TAG, "Failed to read root file: $path")
            return null
        }

        val encoded = result.out.joinToString(separator = "")
        if (encoded.isBlank()) {
            return ByteArray(0)
        }
        return runCatching {
            Base64.decode(encoded, Base64.DEFAULT)
        }.getOrElse {
            Log.e(TAG, "Failed to decode root file: $path", it)
            null
        }
    }

    private fun writeRootFile(path: String, bytes: ByteArray): Boolean {
        val parent = File(path).parent ?: return false
        val tempPath = "$path.tmp"
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val command = buildString {
            append("mkdir -p ")
            append(shellQuote(parent))
            append(" && rm -f ")
            append(shellQuote(tempPath))
            append(" && printf %s ")
            append(shellQuote(encoded))
            append(" | ")
            append(BUSYBOX)
            append(" base64 -d > ")
            append(shellQuote(tempPath))
            append(" && chmod 600 ")
            append(shellQuote(tempPath))
            append(" && mv -f ")
            append(shellQuote(tempPath))
            append(' ')
            append(shellQuote(path))
            append(" && chmod 600 ")
            append(shellQuote(path))
        }
        val result = getRootShell().newJob().add(command).exec()
        if (!result.isSuccess) {
            Log.e(TAG, "Failed to write root file: $path")
        }
        return result.isSuccess
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray? = runCatching {
        Base64.decode(this, Base64.NO_WRAP)
    }.getOrNull()

    private enum class FilePurpose(val label: String) {
        State("state_v1"),
        Auth("auth_v1")
    }
}
