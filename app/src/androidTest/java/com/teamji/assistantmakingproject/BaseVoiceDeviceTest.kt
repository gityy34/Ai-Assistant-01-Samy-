package com.teamji.assistantmakingproject

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teamji.assistantmakingproject.audio.PcmPlayer
import com.teamji.assistantmakingproject.voice.BaseVoiceClient
import com.teamji.assistantmakingproject.voice.ReplyLanguage
import com.teamji.assistantmakingproject.voice.VoiceGender
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    @Test fun stopDoesNotPreventSubsequentSynthesis() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BaseVoiceClient(context)
        val request = async {
            runCatching { client.synthesize("Hello. ".repeat(100), ReplyLanguage.ENGLISH) }
        }
        delay(1000)
        client.stop()
        val result = withTimeout(125_000) { request.await() }
        // Speech may legitimately finish before Stop reaches the worker.
        // Otherwise Stop may interrupt it; either outcome must leave the next request usable.
        result.getOrNull()?.let { assertTrue(it.pcm16.isNotEmpty()) }
        val audio = client.synthesize("Hello again.", ReplyLanguage.ENGLISH)
        assertTrue(audio.pcm16.isNotEmpty())
    }

    @Test fun femaleModelsProduceSpeech() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BaseVoiceClient(context)
        for ((language, text) in listOf(
            ReplyLanguage.URDU to "السلام علیکم۔ یہ خاتون کی آواز ہے۔",
            ReplyLanguage.ENGLISH to "Hello. This is a female voice."
        )) {
            val audio = client.synthesize(text, language, VoiceGender.FEMALE)
            assertTrue(audio.pcm16.size > audio.sampleRateHz / 4)
            assertTrue(audio.pcm16.any { kotlin.math.abs(it.toInt()) > 100 })
        }
    }

    @Test fun stopInterruptsPlaybackAndCompletionIsReported() = runBlocking {
        withContext(Dispatchers.Main) {
            val player = PcmPlayer()
            val job = launch { player.playAndWait(ShortArray(32_000) { if (it % 32 < 16) 1000 else -1000 }, 16_000) }
            delay(150)
            assertTrue(job.isActive)
            player.stop()
            withTimeout(1000) { job.join() }
            player.playAndWait(ShortArray(1600) { 1000 }, 16_000)
        }
    }

    @Test fun englishCloneUsesReferenceAndProducesSpeech() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = File(context.filesDir, "voice_samples/clone-test.wav")
        reference.parentFile!!.mkdirs()
        // A bundled spoken reference exercises the native clone path without user data.
        val path = "models/sherpa-onnx-pocket-tts-int8-2026-01-26/test_wavs/bria.wav"
        context.assets.open(path).use { input -> reference.outputStream().use { input.copyTo(it) } }
        try {
            // Fixture may be 24 kHz. Convert with the native reader to the app's 16 kHz PCM reference format.
            val wave = com.k2fsa.sherpa.onnx.WaveReader.readWave(reference.absolutePath)
            val count = minOf(160_000L, wave.samples.size.toLong() * 16_000 / wave.sampleRate).toInt()
            val pcm = ShortArray(count) { i ->
                val source = (i.toLong() * wave.sampleRate / 16_000).toInt().coerceAtMost(wave.samples.lastIndex)
                (wave.samples[source].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            }
            val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
            bytes.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16_000).putInt(32_000)
            bytes.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(pcm.size * 2)
            pcm.forEach { bytes.putShort(it) }
            reference.writeBytes(bytes.array())
            val audio = BaseVoiceClient(context).synthesize("Hello. This is my voice test.", ReplyLanguage.ENGLISH, reference = reference)
            assertTrue(audio.pcm16.size > audio.sampleRateHz / 4)
            assertTrue(audio.pcm16.any { kotlin.math.abs(it.toInt()) > 100 })
        } finally { reference.delete() }
    }
}
