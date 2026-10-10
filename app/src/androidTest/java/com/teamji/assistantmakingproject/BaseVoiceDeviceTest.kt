package com.teamji.assistantmakingproject

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teamji.assistantmakingproject.audio.PcmPlayer
import com.teamji.assistantmakingproject.voice.BaseVoiceClient
import com.teamji.assistantmakingproject.voice.ReplyLanguage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseVoiceDeviceTest {
    @Test fun bundledModelsProduceAudiblePcmForBothLanguages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BaseVoiceClient(context)
        val phrases = listOf(
            ReplyLanguage.ENGLISH to "Hello. This is an offline voice test.",
            ReplyLanguage.URDU to "السلام علیکم۔ یہ اردو آواز کا ٹیسٹ ہے۔",
            ReplyLanguage.ENGLISH to "Voice playback can be tried again."
        )
        for ((language, text) in phrases) {
            val audio = client.synthesize(text, language)
            assertTrue("$language must produce audio", audio.pcm16.size > audio.sampleRateHz / 4)
            assertTrue("$language must produce a non-silent signal", audio.pcm16.any { kotlin.math.abs(it.toInt()) > 100 })
            withContext(Dispatchers.Main) {
                val player = PcmPlayer()
                try { player.play(audio.pcm16, audio.sampleRateHz) } finally { player.stop() }
            }
        }
    }

    @Test fun workerDeathIsReportedAndNextRequestCanRecover() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BaseVoiceClient(context)
        val request = async {
            runCatching { client.synthesize("Hello. ".repeat(100), ReplyLanguage.ENGLISH) }
        }
        delay(1000)
        client.stop()
        val result = withTimeout(125_000) { request.await() }
        assertTrue("Stopping the worker should fail the outstanding request safely", result.isFailure)
        val audio = client.synthesize("Hello again.", ReplyLanguage.ENGLISH)
        assertTrue(audio.pcm16.isNotEmpty())
    }
}
