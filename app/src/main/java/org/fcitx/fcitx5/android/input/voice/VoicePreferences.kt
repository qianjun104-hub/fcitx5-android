/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VoicePreferences(context: Context) {
    private val context = context.applicationContext
    private val unlocked: Boolean
        get() = Build.VERSION.SDK_INT < 24 ||
            context.getSystemService(UserManager::class.java).isUserUnlocked
    private val prefs by lazy { context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE) }
    private val secrets by lazy { context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE) }

    var enabled: Boolean
        get() = unlocked && prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var cleanupEnabled: Boolean
        get() = prefs.getBoolean("cleanup", true)
        set(value) { prefs.edit().putBoolean("cleanup", value).apply() }

    fun string(key: String): String = prefs.getString(key, defaults[key].orEmpty()).orEmpty()
    fun putString(key: String, value: String) { prefs.edit().putString(key, value.trim()).apply() }
    fun hasKey(name: String): Boolean = unlocked && secrets.contains("${name}_data")

    fun saveKey(name: String, value: String) {
        require(name == "asr" || name == "text")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        secrets.edit()
            .putString("${name}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${name}_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun readKey(name: String): String? {
        if (!unlocked) return null
        val iv = secrets.getString("${name}_iv", null) ?: return null
        val data = secrets.getString("${name}_data", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Keystore material can disappear after restore/reinstall. Never expose ciphertext.
            null
        }
    }

    fun clearKeys() { secrets.edit().clear().apply() }

    fun snapshot(): VoiceConfig {
        val asrBase = validateBaseUrl(string("asr_base"))
        val textBase = validateBaseUrl(string("text_base"))
        val asrKey = readKey("asr") ?: throw VoiceException("请先保存有效的语音识别 API Key")
        val textKey = readKey("text") ?: if (asrBase == textBase) asrKey else ""
        return VoiceConfig(
            asrBase, string("asr_protocol"), asrKey, string("asr_model"),
            textBase, textKey, string("text_model"), cleanupEnabled, string("reasoning"),
            string("glossary").take(4000)
        ).also {
            require(it.asrModel.isNotBlank()) { "请填写语音识别模型 ID" }
        }
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        const val SETTINGS_FILE = "personal_voice_settings"
        const val SECRETS_FILE = "personal_voice_secrets"
        private const val KEY_ALIAS = "personal_ime_voice_aes_v1"
        val defaults = mapOf(
            "asr_base" to "https://openrouter.ai/api/v1",
            "asr_protocol" to "openrouter",
            "asr_model" to "qwen/qwen3-asr-1.7b",
            "text_base" to "https://openrouter.ai/api/v1",
            "text_model" to "deepseek/deepseek-v4.1-flash",
            "reasoning" to "auto",
            "glossary" to "CT，MRI，PCT，CRP，PVP，TFCC，腰椎，椎间盘，股骨颈，头孢唑林，头孢呋辛，哌拉西林，克林霉素，甘露醇，甘油果糖，美托洛尔，地高辛，西地兰"
        )

        fun validateBaseUrl(value: String): String {
            val uri = try { URI(value.trim()) } catch (_: Exception) { throw VoiceException("API 地址格式不正确") }
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "API 基础地址必须使用 HTTPS，不能包含账号、查询参数或片段"
            }
            return uri.toString().trimEnd('/')
        }
    }
}

// Keep keys out of toString(), crash messages and build metadata.
class VoiceConfig(
    val asrBase: String, val asrProtocol: String, val asrKey: String, val asrModel: String,
    val textBase: String, val textKey: String, val textModel: String,
    val cleanup: Boolean, val reasoning: String, val glossary: String
)

class VoiceException(message: String) : Exception(message)
