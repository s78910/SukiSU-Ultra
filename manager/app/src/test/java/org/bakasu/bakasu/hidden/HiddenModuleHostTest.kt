package org.bakasu.bakasu.hidden

import org.bakasu.bakasu.data.module.*
import org.bakasu.bakasu.ui.screen.main.HiddenModuleControls
import com.google.gson.JsonParser
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.*

private class MemoryFiles : HiddenModuleFiles {
    val data = mutableMapOf<String, ByteArray>()
    var failRead = false
    var failWrite = false
    var failAuthWrite = false
    var corruptWrite = false
    override fun exists(path: String): Boolean { check(!failRead); return path in data }
    override fun read(path: String): ByteArray? { check(!failRead); return data[path]?.clone() }
    override fun write(path: String, bytes: ByteArray): Boolean {
        if (failWrite || (failAuthWrite && path == HiddenModuleStore.AUTH_FILE)) return false
        data[path] = if (corruptWrite) byteArrayOf(1) else bytes.clone()
        return true
    }
}

/** Independent old JSONObject-v1 wire fixture, not produced by the migrated codec. */
private fun legacyEnvelope(master: ByteArray, label: String, payload: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(master, "HmacSHA256"))
    val nonce = ByteArray(12) { (it + 7).toByte() }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(mac.doFinal(label.toByteArray()), "AES"), GCMParameterSpec(128, nonce))
    val b64 = Base64.getEncoder()
    return """{"version":1,"nonce":"${b64.encodeToString(nonce)}","ciphertext":"${b64.encodeToString(cipher.doFinal(payload.toByteArray()))}"}""".toByteArray()
}

fun main() {
    val files = MemoryFiles()
    val master = ByteArray(32) { it.toByte() }
    val salt = ByteArray(16) { (it + 31).toByte() }
    val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec("legacy-pass".toCharArray(), salt, 120_000, 256)).encoded
    files.data[HiddenModuleStore.MASTER_KEY_FILE] = master
    files.data[HiddenModuleStore.AUTH_FILE] = legacyEnvelope(master, "auth_v1", """{"version":1,"salt":"${Base64.getEncoder().encodeToString(salt)}","hash":"${Base64.getEncoder().encodeToString(hash)}"}""")
    files.data[HiddenModuleStore.STATE_FILE] = legacyEnvelope(master, "state_v1", """{"version":1,"hidden_ids":["secret_module","中文模块"]}""")
    val store = HiddenModuleStore(files)
    check(store.loadSnapshot().hiddenModuleIds == setOf("secret_module", "中文模块"))
    check(store.authenticate("legacy-pass", ""))
    check(!store.authenticate("bad-pass", ""))
    println("PASS legacy v1 encrypted fixture / existing password / bad password")
    val result = store.setHiddenModuleIds(setOf("next_module"))
    check(!result.shouldHideAllModules && result.hiddenModuleIds == setOf("next_module"))
    check("next_module" !in files.data.getValue(HiddenModuleStore.STATE_FILE).toString(Charsets.UTF_8))
    val e = JsonParser.parseString(files.data.getValue(HiddenModuleStore.STATE_FILE).toString(Charsets.UTF_8)).asJsonObject
    val k = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(master, "HmacSHA256")) }.doFinal("state_v1".toByteArray())
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(e["nonce"].asString)))
    check(JsonParser.parseString(c.doFinal(Base64.getDecoder().decode(e["ciphertext"].asString)).toString(Charsets.UTF_8)).asJsonObject["hidden_ids"].asJsonArray[0].asString == "next_module")
    println("PASS migrated write / independent legacy-compatible decrypt / no plaintext list")
    files.failWrite = true
    check(store.setHiddenModuleIds(setOf("lost")).shouldHideAllModules)
    check(store.loadSnapshot().shouldHideAllModules)
    files.failWrite = false
    check(store.authenticate("legacy-pass", ""))
    files.failRead = true
    check(store.loadSnapshot().shouldHideAllModules && !store.authenticate("legacy-pass", ""))
    files.failRead = false
    println("PASS write failure latched fail-close / read failure / recovery requires authentication")
    val saved = files.data.getValue(HiddenModuleStore.STATE_FILE).clone()
    files.data[HiddenModuleStore.STATE_FILE] = byteArrayOf(123)
    check(store.loadSnapshot().shouldHideAllModules && !store.authenticate("legacy-pass", ""))
    files.data[HiddenModuleStore.STATE_FILE] = saved
    val auth = files.data.getValue(HiddenModuleStore.AUTH_FILE).clone()
    files.data[HiddenModuleStore.AUTH_FILE] = byteArrayOf(1)
    check(store.loadSnapshot().shouldHideAllModules && !store.authenticate("legacy-pass", ""))
    files.data[HiddenModuleStore.AUTH_FILE] = auth
    println("PASS state/auth corruption and invalid JSON fail-close")
    check(store.authenticate("legacy-pass", ""))
    files.corruptWrite = true
    check(store.setHiddenModuleIds(setOf("corrupt")).shouldHideAllModules)
    println("PASS write-readback corruption fail-close")
    val freshFiles = MemoryFiles()
    val fresh = HiddenModuleStore(freshFiles)
    check(fresh.loadSnapshot().shouldHideAllModules)
    check(!fresh.authenticate("", "") && !fresh.authenticate("a", "b"))
    check(fresh.authenticate("new-pass", "new-pass"))
    check(fresh.verifyPassword("new-pass"))
    println("PASS first setup / empty/mismatched password / authenticated initialization")
    freshFiles.data.remove(HiddenModuleStore.STATE_FILE)
    check(fresh.loadSnapshot().shouldHideAllModules)
    check(!fresh.authenticate("new-pass", ""))
    check(HiddenModuleStore.STATE_FILE !in freshFiles.data)
    println("PASS authenticated identity with missing state remains fail-closed")
    val partialFiles = MemoryFiles()
    val partial = HiddenModuleStore(partialFiles)
    partialFiles.failAuthWrite = true
    check(!partial.authenticate("retry-pass", "retry-pass"))
    check(HiddenModuleStore.MASTER_KEY_FILE in partialFiles.data)
    partialFiles.failAuthWrite = false
    check(partial.authenticate("retry-pass", "retry-pass"))
    check(partial.verifyPassword("retry-pass"))
    println("PASS first-setup auth write failure retries with existing master key")
    val normal = HiddenModuleControls(hiddenIds = setOf("hidden"), failed = false)
    check(normal.isVisible("visible") && !normal.isVisible("hidden"))
    check(normal.copy(management = true).isVisible("hidden"))
    check(!normal.copy(failed = true, management = true).isVisible("visible"))
    val selected = setOf("hidden", "visible")
    val toggled = normal.hiddenIds.toMutableSet().apply { selected.forEach { if (!remove(it)) add(it) } }
    check(toggled == setOf("visible"))
    println("PASS normal filtering / management visibility / failed management closes / batch symmetric toggle")
}
