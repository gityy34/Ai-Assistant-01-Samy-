package com.teamji.assistantmakingproject.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native TTS runs outside the UI process. Binder death becomes a recoverable error. */
class BaseVoiceClient(private val context: Context) {
    private var remote: Messenger? = null
    suspend fun synthesize(text: String, language: ReplyLanguage): SynthesisResult {
        require(text.isNotBlank()) { "Enter text to speak" }
        require(text.length <= 1000) { "Please use up to 1000 characters per voice test" }
        val id = UUID.randomUUID().toString()
        val output = File(context.cacheDir, "tts-$id.pcm")
        try {
            withTimeout(120_000) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        var bound = false
                        var cleaned = false
                        lateinit var connection: ServiceConnection
                        fun cleanup() {
                            if (cleaned) return
                            cleaned = true
                            remote = null
                            if (bound) runCatching { context.unbindService(connection) }
                        }
                        fun fail(message: String) {
                            cleanup()
                            if (continuation.isActive)
                                continuation.resumeWithException(IllegalStateException(message))
                        }
                        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                            if (message.what == BaseVoiceService.SUCCESS) {
                                cleanup()
                                if (continuation.isActive) continuation.resume(Unit)
                            } else {
                                fail(message.data.getString("error") ?: "Voice worker failed")
                            }
                            true
                        })
                        connection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                if (!continuation.isActive) { cleanup(); return }
                                val messenger = Messenger(binder)
                                remote = messenger
                                runCatching {
                                    messenger.send(Message.obtain(null, BaseVoiceService.SYNTHESIZE).apply {
                                        data = Bundle().apply {
                                            putString("text", text)
                                            putString("language", language.name)
                                            putString("id", id)
                                        }
                                        replyTo = reply
                                    })
                                }.onFailure { fail("Unable to contact voice worker: ${it.message}") }
                            }
                            override fun onServiceDisconnected(name: ComponentName?) {
                                fail("Voice engine stopped unexpectedly. The app is still open. Please retry.")
                            }
                            override fun onBindingDied(name: ComponentName?) {
                                fail("Voice engine connection was lost. Please retry.")
                            }
                            override fun onNullBinding(name: ComponentName?) {
                                fail("Voice engine could not start")
                            }
                        }
                        bound = context.bindService(
                            Intent(context, BaseVoiceService::class.java), connection, Context.BIND_AUTO_CREATE
                        )
                        if (!bound) fail("Voice worker could not be started")
                        continuation.invokeOnCancellation {
                            Handler(Looper.getMainLooper()).post {
                                runCatching { remote?.send(Message.obtain(null, BaseVoiceService.STOP)) }
                                cleanup()
                            }
                        }
                    }
                }
            }
            return withContext(Dispatchers.IO) {
                DataInputStream(output.inputStream().buffered()).use { input ->
                    val rate = input.readInt()
                    val count = input.readInt()
                    require(rate in 8000..48000 && count in 1..5_760_000) { "Invalid generated audio" }
                    require(output.length() == 8L + count.toLong() * 2) { "Incomplete generated audio" }
                    SynthesisResult(ShortArray(count) { input.readShort() }, rate)
                }
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { output.delete() }
        }
    }
    fun stop() {
        runCatching { remote?.send(Message.obtain(null, BaseVoiceService.STOP)) }
    }
    suspend fun release() { stop() }
}
