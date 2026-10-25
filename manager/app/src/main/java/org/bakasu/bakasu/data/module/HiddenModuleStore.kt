package org.bakasu.bakasu.data.module

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface HiddenModuleFiles {
    fun exists(path: String): Boolean
    fun read(path: String): ByteArray?
    fun write(path: String, bytes: ByteArray): Boolean
}

data class HiddenModuleSnapshot(
    val hiddenModuleIds: Set<String> = emptySet(),
    val shouldHideAllModules: Boolean = true,
    val hasPassword: Boolean = true,
)

/** Disk v1 codec shared with CI35052; deliberately independent of Android package identity. */
class HiddenModuleStore(private val files: HiddenModuleFiles) {
    companion object {
        const val STATE_FILE = "/data/adb/ksu/.s789_module_state"
        const val AUTH_FILE = "/data/adb/ksu/bin/ksuda"
        const val MASTER_KEY_FILE = "/data/adb/ksu/.s789_module_master"
    }

    private var writeFailed = false

    @Synchronized
    fun loadSnapshot(): HiddenModuleSnapshot = runCatching {
        val hasPassword = files.exists(AUTH_FILE)
        val state = readPayload(STATE_FILE, "state_v1") ?: return HiddenModuleSnapshot(hasPassword = hasPassword)
        val auth = readPayload(AUTH_FILE, "auth_v1")
        if (auth == null || decode(auth["salt"].asString).size != 16 || decode(auth["hash"].asString).size != 32) {
            return HiddenModuleSnapshot(hasPassword = hasPassword)
        }
        val ids = state.getAsJsonArray("hidden_ids").map { it.asString }.toSet()
        HiddenModuleSnapshot(ids, writeFailed || !hasPassword, hasPassword)
    }.getOrDefault(HiddenModuleSnapshot())

    @Synchronized
    fun authenticate(password: String, confirmation: String): Boolean = runCatching {
        if (password.isEmpty()) return false
        if (files.exists(AUTH_FILE)) {
            if (!verifyPassword(password)) return false
            // An established identity without a readable state is an incomplete or
            // corrupt installation; never reconstruct it during authentication.
            if (!files.exists(STATE_FILE) || readPayload(STATE_FILE, "state_v1") == null) {
                writeFailed = true
                return false
            }
        } else {
            // A valid orphaned master key may be reused after an auth write failure;
            // an existing state still blocks setup so hidden data is never replaced.
            if (files.exists(STATE_FILE) || (files.exists(MASTER_KEY_FILE) && masterKey(create = false) == null) || password != confirmation) return false
            val auth = JsonObject().apply {
                addProperty("version", 1)
                val salt = randomBytes(16)
                addProperty("salt", encode(salt))
                addProperty("hash", encode(hashPassword(password, salt)))
            }
            if (!writePayload(AUTH_FILE, "auth_v1", auth)) return false
        }
        // Never repair a corrupt existing state by silently replacing it.
        if (!files.exists(STATE_FILE) && !writeState(emptySet())) return false
        writeFailed = false
        !loadSnapshot().shouldHideAllModules
    }.getOrDefault(false)

    @Synchronized
    fun verifyPassword(password: String): Boolean = runCatching {
        val auth = readPayload(AUTH_FILE, "auth_v1") ?: return false
        val salt = decode(auth["salt"].asString)
        val expected = decode(auth["hash"].asString)
        salt.size == 16 && expected.size == 32 && MessageDigest.isEqual(expected, hashPassword(password, salt))
    }.getOrDefault(false)

    @Synchronized
    fun setHiddenModuleIds(ids: Set<String>): HiddenModuleSnapshot {
        val success = runCatching {
            if (writeFailed || loadSnapshot().shouldHideAllModules) return@runCatching false
            writeState(ids) && readPayload(STATE_FILE, "state_v1")
                ?.getAsJsonArray("hidden_ids")?.map { it.asString }?.toSet() == ids
        }.getOrDefault(false)
        if (!success) writeFailed = true
        return loadSnapshot().let { if (success) it else it.copy(shouldHideAllModules = true) }
    }

    private fun writeState(ids: Set<String>): Boolean = writePayload(
        STATE_FILE,
        "state_v1",
        JsonObject().apply {
            addProperty("version", 1)
            add("hidden_ids", JsonArray().apply { ids.sorted().forEach { add(it) } })
        },
    )

    private fun masterKey(create: Boolean): ByteArray? {
        if (files.exists(MASTER_KEY_FILE)) return files.read(MASTER_KEY_FILE)?.takeIf { it.size == 32 }
        if (!create) return null
        val key = randomBytes(32)
        return key.takeIf { files.write(MASTER_KEY_FILE, key) && files.read(MASTER_KEY_FILE)?.contentEquals(key) == true }
    }

    private fun deriveKey(master: ByteArray, label: String): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        return SecretKeySpec(mac.doFinal(label.toByteArray(Charsets.UTF_8)), "AES")
    }

    private fun writePayload(path: String, label: String, payload: JsonObject): Boolean = runCatching {
        val master = masterKey(create = true) ?: return false
        val nonce = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(master, label), GCMParameterSpec(128, nonce))
        val envelope = JsonObject().apply {
            addProperty("version", 1)
            addProperty("nonce", encode(nonce))
            addProperty("ciphertext", encode(cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))))
        }.toString().toByteArray(Charsets.UTF_8)
        files.write(path, envelope) && files.read(path)?.contentEquals(envelope) == true && readPayload(path, label) == payload
    }.getOrDefault(false)

    private fun readPayload(path: String, label: String): JsonObject? = runCatching {
        val bytes = files.read(path) ?: return null
        val master = masterKey(create = false) ?: return null
        val envelope = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(envelope["version"].asInt == 1)
        val nonce = decode(envelope["nonce"].asString)
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(master, label), GCMParameterSpec(128, nonce))
        val payload = JsonParser.parseString(cipher.doFinal(decode(envelope["ciphertext"].asString)).toString(Charsets.UTF_8)).asJsonObject
        require(payload["version"].asInt == 1)
        payload
    }.getOrNull()

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)
    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
