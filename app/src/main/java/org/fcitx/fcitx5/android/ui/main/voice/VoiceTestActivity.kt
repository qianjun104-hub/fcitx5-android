/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.voice

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class VoiceTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { text = "输入试用 · 内容不会保存\n可试说：嗯，今天上午 9 点开会，然后下午 3 点再复查。" })
        root.addView(EditText(this).apply {
            hint = "点击这里调出键盘"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = android.view.Gravity.TOP
            isSaveEnabled = false
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "返回设置"; setOnClickListener { finish() } })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(20 + bars.left, 20 + bars.top, 20 + bars.right, 20 + bars.bottom)
            insets
        }
    }
}
