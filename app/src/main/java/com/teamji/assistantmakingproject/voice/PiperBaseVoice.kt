package com.teamji.assistantmakingproject.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Base voices only. This class never uses or claims to clone a reference voice. */
class PiperBaseVoice(private val context: Context) {
    private val mutex = Mutex()
    private var tts: OfflineTts? = null
    private var loadedLanguage: ReplyLanguage? = null
    private val stopped = AtomicBoolean(false)

    suspend fun synthesize(text: String, language: ReplyLanguage): SynthesisResult =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(text.isNotBlank()) { "Enter text to speak" }
            stopped.set(false)
            val folder = if (language == ReplyLanguage.URDU)
                "vits-piper-ur_PK-fasih-medium" else "vits-piper-en_US-lessac-medium"
            if (loadedLanguage != language || tts == null) {
                tts?.release()
                tts = null
                loadedLanguage = null
                val root = File(context.filesDir, "base-models-2/$folder")
                val ready = File(root, ".ready")
                if (!ready.isFile) {
                    root.deleteRecursively()
                    copyAssets("models/$folder", root)
                    // Marker is written only after every required resource is checked.
                }
                val model = if (language == ReplyLanguage.URDU)
                    "ur_PK-fasih-medium.onnx" else "en_US-lessac-medium.onnx"
                check(File(root, model).isFile && File(root, "tokens.txt").isFile) {
                    "Base voice model is missing"
                }
                val requiredFiles = listOf(model, "tokens.txt", "espeak-ng-data/phontab",
                    "espeak-ng-data/phonindex", "espeak-ng-data/phondata", "espeak-ng-data/intonations")
                requiredFiles.forEach { relative ->
                    check(File(root, relative).let { it.isFile && it.length() > 0L }) {
                        "Voice resource missing or empty: $relative"
                    }
                }
                ready.writeText("2")
                tts = OfflineTts(config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = File(root, model).absolutePath,
                            tokens = File(root, "tokens.txt").absolutePath,
                            dataDir = File(root, "espeak-ng-data").absolutePath,
                        ),
                        numThreads = 2,
                        debug = true,
                        provider = "cpu",
                    ),
                    maxNumSentences = 1,
                ))
                loadedLanguage = language
            }
            val audio = checkNotNull(tts).generateWithCallback(text, 0, 1.0f) {
                if (stopped.get()) 0 else 1
            }
            check(!stopped.get()) { "Stopped" }
            SynthesisResult(
                pcm16 = ShortArray(audio.samples.size) { i ->
                    (audio.samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                },
                sampleRateHz = audio.sampleRate,
            )
        }

    }

    fun stop() { stopped.set(true) }

    suspend fun release() = withContext(Dispatchers.IO) { mutex.withLock {
        tts?.release()
        tts = null
        loadedLanguage = null
    }

    }

    private fun copyAssets(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            children.forEach { copyAssets("$assetPath/$it", File(target, it)) }
        }
    }
}
