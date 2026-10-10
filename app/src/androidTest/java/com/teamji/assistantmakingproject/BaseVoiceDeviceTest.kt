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


    @Test fun sessionKeepsTheSameWorkerForSuccessiveChunks() = runBlocking {
        val client = BaseVoiceClient(InstrumentationRegistry.getInstrumentation().targetContext)
        client.withSession {
            client.synthesize("Hello. This is the first part.", ReplyLanguage.ENGLISH)
            val firstPid = client.lastWorkerPid
            client.synthesize("This is the second part.", ReplyLanguage.ENGLISH)
            assertTrue(firstPid > 0)
            assertEquals("Worker must not restart between parts", firstPid, client.lastWorkerPid)
        }
    }

    @Test fun englishCloneChangesWithMaleAndFemaleReferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = BaseVoiceClient(context)
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val female = File(dir, "clone-female-test.wav")
        val male = File(dir, "clone-male-test.wav")
        val fixture = File(context.cacheDir, "clone-fixture.wav")
        try {
            context.assets.open("models/sherpa-onnx-pocket-tts-int8-2026-01-26/test_wavs/bria.wav").use { input ->
                fixture.outputStream().use { input.copyTo(it) }
            }
            val wave = com.k2fsa.sherpa.onnx.WaveReader.readWave(fixture.absolutePath)
            writeReference(female, wave.samples, wave.sampleRate)
            val maleAudio = client.synthesize(
                "Hello, my name is James. I am speaking clearly and calmly for this voice recording.",
                ReplyLanguage.ENGLISH, VoiceGender.MALE)
            writeReference(male, FloatArray(maleAudio.pcm16.size) { maleAudio.pcm16[it] / 32768f }, maleAudio.sampleRateHz)
            client.withSession {
                val text = "Hello. This is my voice test."
                val a = client.synthesize(text, ReplyLanguage.ENGLISH, reference = female)
                val b = client.synthesize(text, ReplyLanguage.ENGLISH, reference = male)
                val repeat = client.synthesize(text, ReplyLanguage.ENGLISH, reference = male)
                for (audio in listOf(a, b, repeat)) {
                    assertTrue(audio.pcm16.size > audio.sampleRateHz / 4)
                    assertTrue(audio.pcm16.any { kotlin.math.abs(it.toInt()) > 100 })
                }
                val common = minOf(a.pcm16.size, b.pcm16.size)
                val difference = (0 until common).sumOf { kotlin.math.abs(a.pcm16[it].toInt() - b.pcm16[it].toInt()).toDouble() } / common
                assertTrue("Changing the reference with a fixed seed must change generated speech", difference > 100.0)
                assertEquals("Fixed-seed generation with the same reference must be repeatable", b.pcm16.size, repeat.pcm16.size)
                val repeatedDifference = b.pcm16.indices.sumOf { kotlin.math.abs(b.pcm16[it].toInt() - repeat.pcm16[it].toInt()).toDouble() } / b.pcm16.size
                assertTrue("Reference conditioning must be stable", repeatedDifference < 100.0)
            }
        } finally { female.delete(); male.delete(); fixture.delete() }
    }

    @Test fun successfulRepliesSurviveImmediateWorkerUnbinding() = runBlocking {
        val client = BaseVoiceClient(InstrumentationRegistry.getInstrumentation().targetContext)
        repeat(8) {
            val audio = client.synthesize("Hello. This file must remain readable.", ReplyLanguage.ENGLISH)
            assertTrue("Completed audio must be fully readable after unbind", audio.pcm16.size > audio.sampleRateHz / 4)
            assertTrue(audio.pcm16.any { kotlin.math.abs(it.toInt()) > 100 })
        }
    }

    private fun writeReference(file: File, samples: FloatArray, rate: Int) {
        val count = minOf(160_000L, samples.size.toLong() * 16_000 / rate).toInt()
        val pcm = ShortArray(count) { i ->
            val source = (i.toLong() * rate / 16_000).toInt().coerceAtMost(samples.lastIndex)
            (samples[source].coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16_000).putInt(32_000)
        bytes.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { bytes.putShort(it) }
        file.writeBytes(bytes.array())
    }
}
