/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class VoicePermissionActivity : AppCompatActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Toast.makeText(this, "麦克风已授权，请返回输入框再次点击语音按钮", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish(); return
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 96, 32, 32)
            addView(TextView(this@VoicePermissionActivity).apply { text = "语音输入需要麦克风权限。授权后返回原输入框，重新点击语音按钮。" })
            addView(Button(this@VoicePermissionActivity).apply {
                text = "授权麦克风"
                setOnClickListener { request.launch(Manifest.permission.RECORD_AUDIO) }
            })
            addView(Button(this@VoicePermissionActivity).apply {
                text = "打开应用权限设置"
                setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            })
            addView(Button(this@VoicePermissionActivity).apply { text = "返回"; setOnClickListener { finish() } })
        })
        if (savedInstanceState == null) request.launch(Manifest.permission.RECORD_AUDIO)
    }
}
