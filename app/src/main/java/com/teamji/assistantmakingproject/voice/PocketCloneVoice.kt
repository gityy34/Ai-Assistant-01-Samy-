package com.teamji.assistantmakingproject.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import com.teamji.assistantmakingproject.audio.WavFileInspector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Experimental English cloning. Urdu is rejected instead of being sent to an English model. */
class PocketCloneVoice(private val context: Context) {
    private var loaded: OfflineTts? = null
    suspend fun synthesize(text: String, reference: File): SynthesisResult = withContext(Dispatchers.IO) {
        val format = WavFileInspector.requireCloneReferenceFormat(reference)
        val seconds = format.dataBytes.toDouble() / 32_000
        require(seconds in 3.0..15.0) { "Record a clean 8–12 second sample (accepted: 3–15 seconds)" }
        val folder = "sherpa-onnx-pocket-tts-int8-2026-01-26"
        val root = File(context.filesDir, "clone-models/$folder")
        val files = listOf("lm_flow.int8.onnx", "lm_main.int8.onnx", "encoder.onnx",
            "decoder.int8.onnx", "text_conditioner.onnx", "vocab.json", "token_scores.json")
        for (name in files) {
            val output = File(root, name)
            if (!output.isFile || output.length() == 0L) {
                root.mkdirs()
                val temp = File(root, "$name.tmp")
                try {
                    context.assets.open("models/$folder/$name").use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    check(temp.renameTo(output)) { "Unable to prepare cloning model" }
                } finally { temp.delete() }
            }
        }
        fun path(name: String) = File(root, name).absolutePath
        val tts = loaded ?: OfflineTts(config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                pocket = OfflineTtsPocketModelConfig(
                    lmFlow = path(files[0]), lmMain = path(files[1]), encoder = path(files[2]),
                    decoder = path(files[3]), textConditioner = path(files[4]),
                    vocabJson = path(files[5]), tokenScoresJson = path(files[6]),
                    voiceEmbeddingCacheCapacity = 1,
                ), numThreads = 2, provider = "cpu",
            )
        )).also { loaded = it }
        run {
            val wave = WaveReader.readWave(reference.absolutePath)
            require(wave.samples.isNotEmpty() && wave.samples.any { kotlin.math.abs(it) > 0.01f }) {
                "Recording is silent; record your voice again"
            }
            val referenceAudio = CloneReferenceAudio.prepare(wave.samples, wave.sampleRate)
            val audio = tts.generateWithConfig(text, GenerationConfig(
                referenceAudio = referenceAudio, referenceSampleRate = wave.sampleRate,
                numSteps = 10,
                extra = mapOf("seed" to "42", "temperature" to "0.6", "max_reference_audio_len" to "15"),
            ))
            require(audio.samples.isNotEmpty()) { "Clone model returned no speech" }
            SynthesisResult(ShortArray(audio.samples.size) {
                (audio.samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }, audio.sampleRate)
        }
    }
}
