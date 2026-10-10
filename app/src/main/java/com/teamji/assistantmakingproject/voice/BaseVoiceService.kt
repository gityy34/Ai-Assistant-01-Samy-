package com.teamji.assistantmakingproject.voice

import android.app.Service
import android.content.Intent
import android.os.*
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.io.File

class BaseVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val voice by lazy { PiperBaseVoice(this) }
    private var busy = false
    private val clone by lazy { PocketCloneVoice(this) }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        when (request.what) {
            STOP -> { android.os.Process.killProcess(android.os.Process.myPid()); true }
            SYNTHESIZE -> {
                val reply = request.replyTo
                if (busy) {
                    sendError(reply, "Voice engine is already busy")
                } else {
                    busy = true
                    val data = Bundle(request.data)
                    scope.launch {
                        var output: File? = null
                        try {
                            val id = data.getString("id").orEmpty()
                            require(Regex("[a-f0-9-]{36}").matches(id)) { "Invalid request" }
                            val text = data.getString("text").orEmpty()
                            require(text.isNotBlank() && text.length <= 1000) { "Use 1–1000 characters" }
                            val language = ReplyLanguage.valueOf(data.getString("language").orEmpty())
                            val gender = VoiceGender.valueOf(data.getString("gender") ?: "MALE")
                            val referencePath = data.getString("reference")
                            val generated = if (referencePath == null) voice.synthesize(text, language, gender) else {
                                require(language == ReplyLanguage.ENGLISH) { "Urdu cloning is not available yet" }
                                val reference = File(referencePath).canonicalFile
                                val sampleDir = File(filesDir, "voice_samples").canonicalFile
                                require(reference.parentFile == sampleDir && reference.isFile) { "Invalid voice sample" }
                                clone.synthesize(text, reference)
                            }
                            val audio = SynthesisResult(SpeechPacing.compact(generated.pcm16, generated.sampleRateHz), generated.sampleRateHz)
                            require(audio.pcm16.isNotEmpty()) { "Voice engine returned no audio" }
                            output = File(cacheDir, "tts-$id.pcm")
                            DataOutputStream(output.outputStream().buffered()).use { stream ->
                                stream.writeInt(audio.sampleRateHz)
                                stream.writeInt(audio.pcm16.size)
                                audio.pcm16.forEach { stream.writeShort(it.toInt()) }
                            }
                            withContext(Dispatchers.Main) { busy = false; reply.send(Message.obtain(null, SUCCESS).apply { this.data = Bundle().apply { putInt("workerPid", android.os.Process.myPid()) } }) }
                        } catch (error: Throwable) {
                            output?.delete()
                            android.util.Log.e("BaseVoiceWorker", "Synthesis failed", error)
                            withContext(Dispatchers.Main) {
                                busy = false
                                sendError(reply, "${error.javaClass.simpleName}: ${error.message}")
                            }
                        }
                    }
                }
                true
            }
            else -> false
        }
    })
    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onDestroy() {
        scope.cancel()
        // Release native state by terminating only this dedicated worker process.
        android.os.Process.killProcess(android.os.Process.myPid())
        super.onDestroy()
    }
    private fun sendError(reply: Messenger?, error: String) {
        runCatching {
            reply?.send(Message.obtain(null, ERROR).apply {
                data = Bundle().apply { putString("error", error) }
            })
        }
    }
    companion object {
        const val SYNTHESIZE = 1
        const val STOP = 2
        const val SUCCESS = 3
        const val ERROR = 4
    }
}
