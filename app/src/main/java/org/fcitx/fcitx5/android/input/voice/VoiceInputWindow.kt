/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.ui.main.voice.VoiceSettingsActivity
import splitties.dimensions.dp

class VoiceInputWindow : InputWindow.ExtendedInputWindow<VoiceInputWindow>() {
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private var observer: Job? = null
    private var showRaw = false
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var mic: Button
    private lateinit var restore: Button
    private lateinit var raw: Button
    private lateinit var copy: Button
    override val title get() = context.getString(R.string.ai_voice_title)

    override fun onCreateView(): View {
        fun button(label: String, action: () -> Unit) = Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { InputFeedbacks.hapticFeedback(it); action() }
        }
        return LinearLayout(context).apply {
            // A theme/orientation replacement can detach the entire InputView without
            // asking InputWindowManager to remove this particular window.
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) { observe() }
                override fun onViewDetachedFromWindow(view: View) { onDetached() }
            })
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(theme.backgroundColor)
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
            status = TextView(context).apply { textSize = 14f; setTextColor(theme.keyTextColor); gravity = Gravity.CENTER }
            addView(status, LinearLayout.LayoutParams(-1, -2))
            transcript = TextView(context).apply { textSize = 16f; setTextColor(theme.keyTextColor); setTextIsSelectable(false) }
            addView(ScrollView(context).apply { addView(transcript) }, LinearLayout.LayoutParams(-1, 0, 1f))
            mic = button("开始录音") {
                if (service.aiVoice.state.value.phase == VoicePhase.Recording) service.aiVoice.stop()
                else service.aiVoice.start()
            }
            addView(mic, LinearLayout.LayoutParams(-1, context.dp(48)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                raw = button("使用原文") {
                    if (service.aiVoice.state.value.phase == VoicePhase.Cleaning) service.aiVoice.useRaw()
                    else { showRaw = !showRaw; render(service.aiVoice.state.value) }
                }
                restore = button("恢复原文") { service.aiVoice.restoreRaw() }
                copy = button("复制") {
                    val value = service.aiVoice.state.value
                    val text = if (showRaw || value.text.isEmpty()) value.raw else value.text
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (text.isNotBlank()) { clipboard.setPrimaryClip(ClipData.newPlainText("语音输入", text)); status.text = "已复制" }
                }
                for (b in listOf(raw, restore, copy)) addView(b, LinearLayout.LayoutParams(0, context.dp(48), 1f))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(button("取消") { service.aiVoice.cancel() }, LinearLayout.LayoutParams(0, context.dp(44), 1f))
                addView(button("语音设置") {
                    service.startActivity(Intent(service, VoiceSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, LinearLayout.LayoutParams(0, context.dp(44), 1f))
            })
        }
    }

    private fun render(state: VoiceUiState) {
        status.text = state.status
        transcript.text = if (showRaw || state.text.isBlank()) state.raw else state.text
        mic.text = when (state.phase) {
            VoicePhase.Recording -> "停止并识别"
            VoicePhase.Starting -> "正在打开麦克风…"
            VoicePhase.Transcribing -> "正在识别…"
            VoicePhase.Cleaning -> "正在整理…"
            else -> "开始录音"
        }
        mic.isEnabled = state.phase !in setOf(VoicePhase.Starting, VoicePhase.Transcribing, VoicePhase.Cleaning)
        restore.isEnabled = state.canRestore
        raw.isEnabled = state.raw.isNotBlank()
        raw.text = if (state.phase == VoicePhase.Cleaning) "使用原文" else if (showRaw) "查看结果" else "查看原文"
        copy.isEnabled = state.raw.isNotBlank()
    }

    override fun onAttached() {
        observe()
        if (service.aiVoice.state.value.phase == VoicePhase.Idle) service.aiVoice.start()
    }

    private fun observe() {
        if (observer?.isActive != true) {
            observer = service.lifecycleScope.launch { service.aiVoice.state.collect { render(it) } }
        }
    }

    override fun onDetached() { observer?.cancel(); observer = null; service.aiVoice.cancel() }
}
