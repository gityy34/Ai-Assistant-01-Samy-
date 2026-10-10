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
    var lastWorkerPid: Int = 0
        private set
    private var remote: Messenger? = null
    private var sessionRemote: Messenger? = null
    private val sessionMutex = kotlinx.coroutines.sync.Mutex()

    /** A keeper binding preserves loaded native models for a whole reading session. */
    suspend fun <T> withSession(block: suspend () -> T): T {
        sessionMutex.lock()
        try {
            return withContext(Dispatchers.Main.immediate) {
                var bound = false
                var messenger: Messenger? = null
                lateinit var connection: ServiceConnection
                try {
                    withTimeout(15_000) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            connection = object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                    if (continuation.isActive) {
                                        messenger = Messenger(binder)
                                        sessionRemote = messenger
                                        continuation.resume(Unit)
                                    }
                                }
                                override fun onServiceDisconnected(name: ComponentName?) {
                                    if (sessionRemote === messenger) sessionRemote = null
                                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Voice worker disconnected"))
                                }
                                override fun onNullBinding(name: ComponentName?) {
                                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Voice worker unavailable"))
                                }
                            }
                            bound = context.bindService(Intent(context, BaseVoiceService::class.java), connection, Context.BIND_AUTO_CREATE)
                            if (!bound && continuation.isActive) continuation.resumeWithException(IllegalStateException("Voice worker could not start"))
                        }
                    }
                    block()
                } finally {
                    if (sessionRemote === messenger) sessionRemote = null
                    if (bound) runCatching { context.unbindService(connection) }
                }
            }
        } finally { sessionMutex.unlock() }
    }

    suspend fun synthesize(text: String, language: ReplyLanguage, gender: VoiceGender = VoiceGender.MALE, reference: File? = null): SynthesisResult {
        require(text.isNotBlank()) { "Enter text to speak" }
        require(text.length <= 1000) { "Please use up to 1000 characters per voice test" }
        val id = UUID.randomUUID().toString()
        val output = File(context.cacheDir, "tts-$id.pcm")
        try {
            withTimeout(if (reference == null) 120_000L else 180_000L) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        var bound = false
                        var cleaned = false
                        var requestRemote: Messenger? = null
                        lateinit var connection: ServiceConnection
                        fun cleanup() {
                            if (cleaned) return
                            cleaned = true
                            if (remote === requestRemote) remote = null
                            if (bound) runCatching { context.unbindService(connection) }
                        }
                        fun fail(message: String) {
                            cleanup()
                            if (continuation.isActive)
                                continuation.resumeWithException(IllegalStateException(message))
                        }
                        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                            if (message.what == BaseVoiceService.SUCCESS) {
                                lastWorkerPid = message.data.getInt("workerPid")
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
                                requestRemote = messenger
                                remote = messenger
                                runCatching {
                                    messenger.send(Message.obtain(null, BaseVoiceService.SYNTHESIZE).apply {
                                        data = Bundle().apply {
                                            putString("text", text)
                                            putString("language", language.name)
                                            putString("gender", gender.name)
                                            putString("reference", reference?.absolutePath)
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
                                runCatching { requestRemote?.send(Message.obtain(null, BaseVoiceService.STOP)) }
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
        runCatching { (remote ?: sessionRemote)?.send(Message.obtain(null, BaseVoiceService.STOP)) }
    }
    suspend fun release() { stop() }
}
