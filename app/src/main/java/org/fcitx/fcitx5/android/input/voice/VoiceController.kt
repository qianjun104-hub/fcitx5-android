/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.ui.main.voice.VoicePermissionActivity

enum class VoicePhase { Idle, Starting, Recording, Transcribing, Cleaning, Ready, Error }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val status: String = "点击麦克风开始录音",
    val raw: String = "",
    val text: String = "",
    val canRestore: Boolean = false
)

class VoiceController(private val service: FcitxInputMethodService) {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state = mutableState.asStateFlow()
    private val prefs = VoicePreferences(service)
    private var job: Job? = null
    private var stopSignal: CompletableDeferred<Unit>? = null
    private var requestId = 0L
    private var target: Target? = null
    private var insertion: Insertion? = null

    fun start() {
        if (job?.isActive == true) return
        if (!service.allowsCloudVoice()) {
            fail("密码、隐私或非文本输入框不启用云端语音")
            return
        }
        if (!prefs.enabled) { fail("请先在设置中启用 AI 语音输入"); return }
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            service.startActivity(Intent(service, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val config = try { prefs.snapshot() } catch (e: Exception) {
            fail(e.message ?: "语音服务配置不正确"); return
        }
        val snapshot = captureTarget() ?: run { fail("请先完成拼音输入，并保持当前输入框不变"); return }
        target = snapshot
        insertion = null
        val id = ++requestId
        val capture = VoiceAudioCapture()
        val stop = CompletableDeferred<Unit>()
        stopSignal = stop
        mutableState.value = VoiceUiState(VoicePhase.Starting, "正在打开麦克风…")
        job = service.lifecycleScope.launch {
            var wav: ByteArray? = null
            var timer: Job? = null
            try {
                withContext(Dispatchers.IO) { capture.start({ stop.complete(Unit) }, { stop.complete(Unit) }) }
                val began = SystemClock.elapsedRealtime()
                mutableState.value = VoiceUiState(VoicePhase.Recording, "正在录音 · 0 秒")
                timer = launch {
                    while (true) {
                        delay(1000)
                        val seconds = (SystemClock.elapsedRealtime() - began) / 1000
                        mutableState.value = mutableState.value.copy(status = "正在录音 · $seconds 秒（最长 5 分钟）")
                    }
                }
                stop.await()
                timer.cancel()
                mutableState.value = VoiceUiState(VoicePhase.Transcribing, "正在识别…")
                wav = withContext(Dispatchers.IO) { capture.stopAsWav() }
                val result = VoicePipeline().run(config, wav) { raw ->
                    mutableState.value = VoiceUiState(
                        if (config.cleanup) VoicePhase.Cleaning else VoicePhase.Transcribing,
                        if (config.cleanup) "正在保守整理，可直接使用原文" else "识别完成", raw = raw)
                }
                if (id != requestId) return@launch
                val inserted = insert(snapshot, result.text, result.raw)
                mutableState.value = VoiceUiState(VoicePhase.Ready,
                    if (!inserted) "输入位置已变化，结果未插入；可以复制后自行粘贴"
                    else result.warning ?: if (result.text != result.raw) "已识别并整理" else "已输入原始转写",
                    result.raw, result.text, inserted && result.raw != result.text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) fail(if (e is VoiceException) e.message.orEmpty() else "录音或识别失败，请检查设置后重试")
            } finally {
                timer?.cancel()
                withContext(NonCancellable + Dispatchers.IO) { runCatching { capture.cancel() }; wav?.fill(0) }
                if (id == requestId) stopSignal = null
            }
        }
    }

    fun stop() { stopSignal?.complete(Unit) }

    /** User explicitly prefers raw ASR while cleanup is pending. */
    fun useRaw() {
        val raw = state.value.raw
        val snapshot = target ?: return
        if (raw.isBlank()) return
        requestId++
        job?.cancel()
        stopSignal = null
        val inserted = insert(snapshot, raw, raw)
        mutableState.value = VoiceUiState(VoicePhase.Ready,
            if (inserted) "已输入原始转写" else "输入位置已变化，结果未插入；可以复制后自行粘贴", raw, raw)
    }

    /** Only replaces the exact unchanged insertion at its original caret in this session. */
    fun restoreRaw() {
        val item = insertion ?: return
        val snapshot = item.target
        val range = service.currentInputSelection
        val connection = service.currentInputConnection
        val end = snapshot.start + item.inserted.length
        if (snapshot.session != service.voiceSession || !service.allowsCloudVoice() ||
            connection == null || range.start != end || range.end != end ||
            connection.getTextBeforeCursor(snapshot.before.length + item.inserted.length, 0)?.toString() != snapshot.before + item.inserted ||
            connection.getTextAfterCursor(CONTEXT_CHARS, 0)?.toString() != snapshot.after) {
            mutableState.value = state.value.copy(status = "文字或光标已变化，未覆盖内容；可以查看或复制原文", canRestore = false)
            return
        }
        if (service.replaceVoiceText(snapshot.start, end, item.raw)) {
            insertion = null
            mutableState.value = state.value.copy(status = "已恢复原始转写", text = item.raw, canRestore = false)
        } else mutableState.value = state.value.copy(status = "当前应用拒绝替换，未恢复原文", canRestore = false)
    }

    fun cancel() {
        requestId++
        job?.cancel()
        job = null
        stopSignal = null
        target = null
        insertion = null
        mutableState.value = VoiceUiState()
    }

    private fun fail(message: String) { mutableState.value = VoiceUiState(VoicePhase.Error, message) }

    private fun captureTarget(): Target? {
        if (!service.isInputViewShown || service.hasVoiceComposingText()) return null
        val connection = service.currentInputConnection ?: return null
        val range = service.currentInputSelection
        if (range.start < 0 || range.end < 0) return null
        val before = connection.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString() ?: return null
        val after = connection.getTextAfterCursor(CONTEXT_CHARS, 0)?.toString() ?: return null
        val selected = if (range.isEmpty()) "" else connection.getSelectedText(0)?.toString() ?: return null
        return Target(service.voiceSession, range.start, range.end, before, after, selected)
    }

    private fun insert(snapshot: Target, text: String, raw: String): Boolean {
        if (text.isBlank() || snapshot != captureTarget()) return false
        if (!service.commitVoiceText(text)) return false
        insertion = Insertion(snapshot, text, raw)
        return true
    }

    private data class Target(val session: Long, val start: Int, val end: Int,
                              val before: String, val after: String, val selected: String)
    private data class Insertion(val target: Target, val inserted: String, val raw: String)
    companion object { private const val CONTEXT_CHARS = 64 }
}
