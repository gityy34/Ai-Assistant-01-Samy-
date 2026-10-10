package com.teamji.assistantmakingproject.voice

/**
 * Candidate architecture for Urdu + English cloning on Android:
 *
 * Text -> language-specific local base TTS -> base PCM
 * Reference voice -> target speaker embedding
 * base PCM + source speaker embedding + target speaker embedding
 * -> tone-color converter -> cloned PCM
 *
 * No concrete model is wired here until it passes the OPPO Reno11 F acceptance tests.
 */
interface BaseSpeechSynthesizer {
    val language: ReplyLanguage
    suspend fun initialize()
    suspend fun synthesizeBase(text: String): SynthesisResult
    fun release()
}

data class SpeakerEmbedding(val values: FloatArray)

interface ToneColorConverter {
    suspend fun initialize()
    suspend fun extractTargetEmbedding(reference: VoiceReference): SpeakerEmbedding
    suspend fun convert(
        baseSpeech: SynthesisResult,
        sourceSpeaker: SpeakerEmbedding,
        targetSpeaker: SpeakerEmbedding,
    ): SynthesisResult
    fun stop()
    fun release()
}

/**
 * Model-pack metadata is kept separate from the app so models can be replaced
 * without changing the UI or chat layers.
 */
data class VoiceModelPack(
    val id: String,
    val displayName: String,
    val version: String,
    val licenseId: String,
    val sha256: String,
    val supportedLanguages: Set<ReplyLanguage>,
)
