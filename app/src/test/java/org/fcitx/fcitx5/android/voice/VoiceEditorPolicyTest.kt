package org.fcitx.fcitx5.android.voice

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.input.voice.VoiceEditorPolicy
import org.junit.Assert.*
import org.junit.Test

class VoiceEditorPolicyTest {
    @Test fun normalMultilineTextAllowsVoice() {
        assertTrue(VoiceEditorPolicy.allowsCloud(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0))
    }
    @Test fun allPasswordFormsAreBlockedIncludingVisibleAndNumeric() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
            assertFalse(VoiceEditorPolicy.allowsCloud(InputType.TYPE_CLASS_TEXT or variation, 0))
        assertFalse(VoiceEditorPolicy.allowsCloud(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0))
    }
    @Test fun privateAndNonTextEditorsDoNotUseCloud() {
        assertFalse(VoiceEditorPolicy.allowsCloud(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        for (type in listOf(InputType.TYPE_NULL, InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME))
            assertFalse(VoiceEditorPolicy.allowsCloud(type, 0))
    }
}
