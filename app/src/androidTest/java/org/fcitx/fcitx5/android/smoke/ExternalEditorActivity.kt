/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.smoke

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Runs in the instrumentation APK's own process and UID, not in the IME. */
class ExternalEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 64, 24, 24) }
        root.addView(TextView(this).apply { text = "独立应用输入框 · 测试专用" })
        val editor = EditText(this).apply {
            contentDescription = "external-smoke-editor"
            hint = "跨应用键盘与麦克风检查"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            isSaveEnabled = false
        }
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        editor.requestFocus()
    }
}
