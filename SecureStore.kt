package ai.horoai.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("horo_secure", Context.MODE_PRIVATE)
    private val alias = "HoroAI_AES_KEY"

    fun endpoint() = prefs.getString("endpoint", "") ?: ""
    fun model() = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
    fun apiKey(): String = prefs.getString("api_key", null)?.let { decrypt(it) } ?: ""

    fun loadHistory(): List<ChatMessage> = runCatching {
        val raw = prefs.getString("chat_history", null)?.let { decrypt(it) } ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatMessage(item.getString("text"), item.getBoolean("fromUser"))
        }
    }.getOrDefault(emptyList())

    fun saveHistory(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(100).forEach { message ->
            array.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser))
        }
        prefs.edit().putString("chat_history", encrypt(array.toString())).apply()
    }

    fun clearHistory() { prefs.edit().remove("chat_history").apply() }

    fun save(endpoint: String, model: String, key: String) {
        prefs.edit()
            .putString("endpoint", endpoint)
            .putString("model", model.ifBlank { "gpt-4o-mini" })
            .putString("api_key", if (key.isBlank()) "" else encrypt(key))
            .apply()
    }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(4 + iv.size + encrypted.size)
            .putInt(iv.size).put(iv).put(encrypted).array(), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        val data = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(data)
        val ivSize = buffer.int
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")
}
