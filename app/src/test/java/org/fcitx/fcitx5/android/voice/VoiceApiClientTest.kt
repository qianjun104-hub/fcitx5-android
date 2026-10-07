package org.fcitx.fcitx5.android.voice

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.fcitx.fcitx5.android.input.voice.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class VoiceApiClientTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun config(protocol: String = "openrouter", cleanup: Boolean = true) = VoiceConfig(
        server.url("/api/v1").toString().trimEnd('/'), protocol, "test-token", "qwen/qwen3-asr-1.7b",
        server.url("/api/v1").toString().trimEnd('/'), "test-token", "test-cleaner", cleanup, "auto", "CT，头孢唑林")

    @Test fun openRouterUsesTranscriptionJsonWithWavAndChineseHint() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"今天复查CT\"}"))
        assertEquals("今天复查CT", VoiceApiClient().transcribe(config(), byteArrayOf(1, 2)))
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/audio/transcriptions", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("zh", json["language"]!!.jsonPrimitive.content)
        assertEquals("wav", json["input_audio"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("AQI=", json["input_audio"]!!.jsonObject["data"]!!.jsonPrimitive.content)
    }

    @Test fun openAiCompatibleAsrUsesFileUpload() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"原文\"}"))
        assertEquals("原文", VoiceApiClient().transcribe(config("openai"), byteArrayOf(0, 0)))
        val request = server.takeRequest(3, TimeUnit.SECONDS)!!
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"voice.wav\""))
        assertTrue(body.contains("name=\"model\""))
    }

    @Test fun cleanupFailureFallsBackToAsrTextAndNoErrorBodyLeaks() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"右侧疼痛3天\"}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("sensitive upstream body"))
        val result = VoicePipeline().run(config(), byteArrayOf(0, 0)) {}
        assertEquals("右侧疼痛3天", result.raw)
        assertEquals(result.raw, result.text)
        assertNotNull(result.warning)
        assertFalse(result.warning!!.contains("sensitive"))
    }

    @Test fun hallucinatedCleanupAndTruncationFallBackToOriginal() = runBlocking {
        for (reason in listOf("stop", "length")) {
            server.enqueue(MockResponse().setBody("{\"text\":\"今天上午复查\"}"))
            server.enqueue(MockResponse().setBody("{\"choices\":[{\"finish_reason\":\"$reason\",\"message\":{\"content\":\"请立即使用抗菌药物\"}}]}"))
            val result = VoicePipeline().run(config(), byteArrayOf(0, 0)) {}
            assertEquals(result.raw, result.text)
            assertNotNull(result.warning)
        }
    }

    @Test fun disabledCleanupMakesOnlyOneRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"原文\"}"))
        val result = VoicePipeline().run(config(cleanup = false), byteArrayOf(0, 0)) {}
        assertEquals("原文", result.text)
        assertEquals(1, server.requestCount)
    }

    @Test fun blankAndMalformedResponsesNeverBecomeInsertedText() = runBlocking {
        for (body in listOf("{\"text\":\"\"}", "{\"message\":\"error\"}", "<html>provider error</html>")) {
            server.enqueue(MockResponse().setBody(body))
            try { VoiceApiClient().transcribe(config(), byteArrayOf(0, 0)); fail("response should be rejected") }
            catch (e: VoiceException) { assertFalse(e.message!!.contains("<html>")) }
        }
    }

    @Test fun cancellationFinishesWithoutWaitingForServerResponse() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = async { VoiceApiClient().transcribe(config(), byteArrayOf(0, 0)) }
        // Await request arrival without blocking the runBlocking coroutine dispatcher.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        }
        withTimeout(2000) { call.cancelAndJoin() }
        assertTrue(call.isCancelled)
    }
}
