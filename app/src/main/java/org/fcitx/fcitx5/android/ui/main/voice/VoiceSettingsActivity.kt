/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentContainerView
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.voice.VoicePreferences
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class VoiceSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = Toolbar(this).apply { title = getString(R.string.ai_voice_title) }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, (56 * resources.displayMetrics.density).toInt()))
        val container = FragmentContainerView(this).apply { id = R.id.voice_settings_container }
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.voice_settings_container, VoiceSettingsFragment()).commit()
    }
}

class VoiceSettingsFragment : PaddingPreferenceFragment() {
    private lateinit var prefs: VoicePreferences
    private lateinit var permission: Preference
    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermission()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        prefs = VoicePreferences(requireContext())
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        category("中文 AI 语音输入") {
            addPreference(Preference(context).apply {
                title = "语音与文字的去向"
                summary = "只有点击麦克风才会上传录音到你配置的识别服务；开启整理后仅把转写发送到整理服务。不会上传输入框上下文。录音与原文仅保留在本次输入会话。"
                isSelectable = false
            })
            addPreference(SwitchPreferenceCompat(context).apply {
                title = "启用 AI 语音按钮"
                isPersistent = false
                isChecked = prefs.enabled
                setOnPreferenceChangeListener { _, value -> prefs.enabled = value as Boolean; true }
            })
            permission = Preference(context).apply {
                title = "麦克风权限"
                setOnPreferenceClickListener { requestMic.launch(Manifest.permission.RECORD_AUDIO); true }
            }
            addPreference(permission)
            addPreference(Preference(context).apply {
                title = "输入试用"
                summary = "打开输入框，试用拼音、候选、语音和原文恢复"
                setOnPreferenceClickListener { startActivity(Intent(context, VoiceTestActivity::class.java)); true }
            })
        }
        category("语音识别 ASR") {
            list("asr_protocol", "API 协议", arrayOf("OpenRouter 音频 JSON", "OpenAI / Groq 兼容文件上传"), arrayOf("openrouter", "openai"))
            edit("asr_base", "识别 API 基础地址", url = true)
            edit("asr_model", "识别模型 ID")
            addKey("asr", "识别 API Key")
        }
        category("保守文字整理") {
            addPreference(SwitchPreferenceCompat(context).apply {
                title = "识别后自动整理"
                summary = "补标点、分段，去口头禅和重复；校验失败自动保留原文"
                isPersistent = false
                isChecked = prefs.cleanupEnabled
                setOnPreferenceChangeListener { _, value -> prefs.cleanupEnabled = value as Boolean; true }
            })
            edit("text_base", "整理 API 基础地址", url = true)
            edit("text_model", "整理模型 ID")
            addKey("text", "整理 API Key（同地址可复用识别 Key）")
            list("reasoning", "OpenRouter 推理强度", arrayOf("自动", "关闭", "低", "中", "高"), arrayOf("auto", "none", "low", "medium", "high"))
            edit("glossary", "常用医学词汇", multiline = true)
        }
        category("本地数据") {
            addPreference(Preference(context).apply {
                title = "清除语音服务 API Key"
                setOnPreferenceClickListener {
                    MaterialAlertDialogBuilder(requireContext()).setTitle("清除两个语音服务密钥？")
                        .setMessage("清除后需重新填写。拼音词库和用户学习不受影响。")
                        .setNegativeButton("取消", null).setPositiveButton("清除") { _, _ ->
                            prefs.clearKeys()
                            requireActivity().recreate()
                        }.show()
                    true
                }
            })
        }
        updatePermission()
    }

    override fun onResume() { super.onResume(); if (::permission.isInitialized) updatePermission() }

    private fun updatePermission() {
        permission.summary = if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            "已允许" else "点击授权；若系统不再询问，请在应用权限中开启麦克风"
    }

    private fun category(title: String, content: PreferenceCategory.() -> Unit) {
        val category = PreferenceCategory(requireContext()).apply { this.title = title }
        preferenceScreen.addPreference(category)
        category.content()
    }

    private fun PreferenceCategory.edit(name: String, label: String, url: Boolean = false, multiline: Boolean = false) {
        addPreference(EditTextPreference(context).apply {
            key = "voice_$name"
            title = label
            isPersistent = false
            text = prefs.string(name)
            summary = text?.take(100)
            setOnBindEditTextListener {
                it.inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else InputType.TYPE_CLASS_TEXT or if (url) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_VARIATION_NORMAL
                it.isSingleLine = !multiline
                it.imeOptions = it.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            setOnPreferenceChangeListener { _, value ->
                val str = value.toString().trim()
                try {
                    if (url) VoicePreferences.validateBaseUrl(str)
                    require(str.isNotBlank() || name == "glossary") { "内容不能为空" }
                    require(str.length <= if (name == "glossary") 4000 else 400) { "内容过长" }
                    prefs.putString(name, str)
                    summary = str.take(100)
                    true
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, e.message ?: "设置格式不正确", android.widget.Toast.LENGTH_SHORT).show()
                    false
                }
            }
        })
    }

    private fun PreferenceCategory.list(name: String, label: String, labels: Array<String>, values: Array<String>) {
        addPreference(ListPreference(context).apply {
            key = "voice_$name"
            title = label
            isPersistent = false
            entries = labels
            entryValues = values
            value = prefs.string(name)
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue -> prefs.putString(name, newValue.toString()); true }
        })
    }

    private fun PreferenceCategory.addKey(name: String, label: String) {
        addPreference(EditTextPreference(context).apply {
            key = "voice_${name}_key"
            title = label
            isPersistent = false
            text = ""
            summary = if (prefs.hasKey(name)) "已加密保存；留空可保留现有密钥" else "未保存；留空可保留现有密钥"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.isSaveEnabled = false
                it.imeOptions = it.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                if (Build.VERSION.SDK_INT >= 26) it.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                it.setText("")
            }
            setOnPreferenceChangeListener { _, value ->
                try {
                    val str = value.toString().trim()
                    if (str.isNotBlank()) prefs.saveKey(name, str)
                    summary = if (prefs.hasKey(name)) "已加密保存；留空可保留现有密钥" else "未保存"
                    // Never retain a key in EditTextPreference state or dialog restoration.
                    false
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "密钥保存失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                    false
                }
            }
        })
    }
}
