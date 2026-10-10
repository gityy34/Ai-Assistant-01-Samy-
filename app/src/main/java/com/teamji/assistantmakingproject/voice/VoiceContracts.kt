package com.teamji.assistantmakingproject.voice

import java.io.File

enum class ReplyLanguage(val tag: String) {
    URDU("ur-PK"),
    ENGLISH("en-US")
}

data class VoiceReference(
    val audioFile: File,
    val displayName: String,
)

data class SynthesisRequest(
    val text: String,
    val language: ReplyLanguage,
    val reference: VoiceReference,
)

data class SynthesisResult(
    val pcm16: ShortArray,
    val sampleRateHz: Int,
)

interface VoiceCloneEngine {
    val engineName: String
    val isReady: Boolean

    suspend fun initialize()
    suspend fun synthesize(request: SynthesisRequest): SynthesisResult
    fun stop()
    fun release()
}

class VoiceEngineNotConfiguredException(message: String) : IllegalStateException(message)
