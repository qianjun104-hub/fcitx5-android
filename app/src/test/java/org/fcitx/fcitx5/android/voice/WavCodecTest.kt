package org.fcitx.fcitx5.android.voice

import org.fcitx.fcitx5.android.input.voice.WavCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavCodecTest {
    @Test fun encodedAudioHasCorrectRiffLengthsAndPcmFormat() {
        val pcm = byteArrayOf(0, 0, 127, 0, 0, -128)
        val wav = WavCodec.encode(pcm)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + pcm.size, b.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(pcm.size, b.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test(expected = IllegalArgumentException::class) fun incompleteSampleIsRejected() { WavCodec.encode(byteArrayOf(1)) }
    @Test(expected = IllegalArgumentException::class) fun emptyAudioIsRejected() { WavCodec.encode(byteArrayOf()) }
}
