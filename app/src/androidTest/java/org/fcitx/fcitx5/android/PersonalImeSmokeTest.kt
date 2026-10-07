/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.input.voice.VoiceAudioCapture
import org.fcitx.fcitx5.android.input.voice.VoicePreferences
import org.fcitx.fcitx5.android.ui.main.voice.VoiceTestActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the installed IME and native engine, without contacting a cloud provider. */
class PersonalImeSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui: UiAutomation = instrumentation.uiAutomation
    private val client = "personal-ime-smoke"

    @Test
    fun freshChineseInputAndVoiceLifecycle() {
        val prefs = VoicePreferences(context)
        var activity: VoiceTestActivity? = null
        var connected = false
        try {
            ui.serviceInfo = ui.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            ui.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
            prefs.enabled = true
            prefs.clearKeys()
            val ime = "${context.packageName}/org.fcitx.fcitx5.android.input.FcitxInputMethodService"
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $ime")
            shell("ime set $ime")
            val screen = instrumentation.startActivitySync(
                Intent(context, VoiceTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as VoiceTestActivity
            activity = screen
            val editor = findEditor(screen.window.decorView) ?: error("Test editor missing")
            showKeyboard(screen, editor)
            await("AI microphone on the installed keyboard") { imeNode { it.contentDescription?.toString() == "中文 AI 语音" } != null }

            val fcitx = FcitxDaemon.connect(client)
            connected = true
            runBlocking {
                withTimeout(30_000) {
                    fcitx.runOnReady {
                        assertEquals(listOf("keyboard-us", "pinyin"), enabledIme().map { it.uniqueName })
                        while (currentIme().uniqueName != "pinyin") delay(100)
                        for (letter in "nihaoshijie") { sendKey(letter); delay(70) }
                        while (getCandidates(0, 1).firstOrNull()?.text != "你好世界") delay(100)
                        assertTrue(select(0))
                    }
                }
            }
            await("Native pinyin commits Chinese text into the actual editor") {
                var text = ""
                instrumentation.runOnMainSync { text = editor.text.toString() }
                text == "你好世界"
            }
            screenshot("01-native-chinese-keyboard")

            click { it.contentDescription?.toString() == "中文 AI 语音" }
            await("Missing-key message, without recording or sending an API request") {
                imeNode { it.text?.toString()?.contains("请先保存有效的语音识别 API Key") == true } != null
            }
            screenshot("02-voice-configuration-required")

            // An intentionally invalid local token permits opening the microphone; this
            // test never presses stop/transcribe, so the token never leaves the emulator.
            val fakeKey = "local-instrumentation-token"
            prefs.saveKey("asr", fakeKey)
            assertEquals(fakeKey, prefs.readKey("asr"))
            val secrets = File(context.applicationInfo.dataDir, "shared_prefs/${VoicePreferences.SECRETS_FILE}.xml")
            instrumentation.waitForIdleSync()
            if (secrets.exists()) assertFalse(secrets.readText().contains(fakeKey))
            click { it.text?.toString() == "开始录音" }
            await("IME AudioRecord starts on the current Android version") {
                imeNode { it.text?.toString() == "停止并识别" } != null
            }
            screenshot("03-voice-recording")
            click { it.text?.toString() == "取消" }
            await("Cancellation returns the panel to idle") {
                imeNode { it.text?.toString() == "点击麦克风开始录音" } != null
            }
            click { it.text?.toString() == "开始录音" }
            await("Microphone can be reopened after cancellation") {
                imeNode { it.text?.toString() == "停止并识别" } != null
            }
            instrumentation.runOnMainSync {
                screen.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
            }
            await("Hiding the keyboard closes its recording window") {
                ui.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
            // Reopening AudioRecord also catches leaked microphone ownership after hide.
            val probe = VoiceAudioCapture()
            try { probe.start({}, {}); assertTrue(probe.isRecording) } finally { probe.cancel() }

            instrumentation.runOnMainSync {
                editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            showKeyboard(screen, editor)
            await("Keyboard returns for a password field") { ui.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            SystemClock.sleep(500)
            assertNull("Cloud voice entry must be absent in a password field",
                imeNode { it.contentDescription?.toString() == "中文 AI 语音" })
            screenshot("04-password-field")
        } catch (failure: Throwable) {
            runCatching { screenshot("failure") }
            throw failure
        } finally {
            prefs.clearKeys()
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            if (connected) FcitxDaemon.disconnect(client)
        }
    }

    private fun shell(command: String): String = ui.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun showKeyboard(activity: VoiceTestActivity, editor: EditText) {
        instrumentation.runOnMainSync {
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun findEditor(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findEditor(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun imeNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
        ui.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .firstNotNullOfOrNull { findNode(it.root, predicate) }

    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) findNode(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun click(predicate: (AccessibilityNodeInfo) -> Boolean) {
        val node = imeNode(predicate) ?: error("Requested IME control missing")
        assertTrue("IME control accepts a click", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("Timed out: $description")
    }

    private fun screenshot(name: String) {
        // UTP uninstalls the target APK after instrumentation; internal/external
        // app directories are removed before the workflow can collect them.
        shell("mkdir -p /data/local/tmp/personal-ime-smoke")
        shell("screencap -p /data/local/tmp/personal-ime-smoke/$name.png")
    }
}
