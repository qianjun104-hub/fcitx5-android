/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs

/** The reader owns release; callers stop and join it before reading or erasing PCM. */
class VoiceAudioCapture {
    @Volatile private var running = false
    @Volatile private var failure: String? = null
    @Volatile private var signal = false
    private var recorder: AudioRecord? = null
    private var reader: Thread? = null
    private var pcm = ErasableBuffer()
    val isRecording get() = running

    @SuppressLint("MissingPermission") // Controller checks the runtime permission before calling.
    @Synchronized
    fun start(onLimit: () -> Unit, onFailure: () -> Unit) {
        check(reader == null) { "录音尚未结束" }
        val size = AudioRecord.getMinBufferSize(WavCodec.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (size <= 0) throw VoiceException("手机无法初始化录音缓冲区")
        val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WavCodec.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            throw VoiceException("录音初始化失败，请检查麦克风是否被占用")
        }
        try { audio.startRecording() } catch (e: Exception) { audio.release(); throw e }
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            audio.release()
            throw VoiceException("麦克风未开始录音")
        }
        recorder = audio
        pcm = ErasableBuffer()
        signal = false
        failure = null
        running = true
        reader = thread(name = "personal-ime-audio", isDaemon = true) {
            val buffer = ByteArray(size)
            try {
                while (running) {
                    val count = audio.read(buffer, 0, buffer.size)
                    if (!running) break
                    if (count <= 0) throw VoiceException("录音被中断，请检查麦克风")
                    val available = MAX_PCM_BYTES - pcm.size()
                    val accepted = minOf(count, available) and -2
                    pcm.write(buffer, 0, accepted)
                    for (i in 0 until accepted step 2) {
                        val sample = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort().toInt()
                        if (abs(sample) > 32) signal = true
                    }
                    if (pcm.size() >= MAX_PCM_BYTES) {
                        running = false
                        onLimit()
                    }
                }
            } catch (_: Exception) {
                if (running) { failure = "录音被中断，请检查麦克风"; running = false; onFailure() }
            } finally {
                buffer.fill(0)
                runCatching { audio.stop() }
                audio.release()
            }
        }
    }

    /** Runs on Dispatchers.IO, never on the UI thread. */
    @Synchronized
    fun stopAsWav(): ByteArray {
        stopReader()
        try {
            failure?.let { throw VoiceException(it) }
            if (pcm.size() < WavCodec.SAMPLE_RATE * 2 / 5 || !signal) {
                throw VoiceException("没有检测到有效语音，请重试")
            }
            val raw = pcm.toByteArray()
            return try { WavCodec.encode(raw) } finally { raw.fill(0) }
        } finally { pcm.erase() }
    }

    @Synchronized
    fun cancel() {
        stopReader()
        pcm.erase()
    }

    private fun stopReader() {
        running = false
        runCatching { recorder?.stop() }
        val worker = reader
        worker?.join(2000)
        if (worker?.isAlive == true) throw VoiceException("麦克风尚未释放，请稍后重试")
        reader = null
        recorder = null
    }

    private class ErasableBuffer : ByteArrayOutputStream() {
        fun erase() { buf.fill(0); reset() }
    }

    companion object {
        const val MAX_SECONDS = 300
        private const val MAX_PCM_BYTES = WavCodec.SAMPLE_RATE * 2 * MAX_SECONDS
    }
}
