package com.teamji.assistantmakingproject.voice

/**
 * Safety placeholder. It intentionally refuses to fake voice cloning.
 * Replace this with an actually tested Urdu+English offline engine.
 */
class UnconfiguredMultilingualVoiceEngine : VoiceCloneEngine {
    override val engineName: String = "Multilingual engine pending verification"
    override val isReady: Boolean = false

    override suspend fun initialize() = Unit

    override suspend fun synthesize(request: SynthesisRequest): SynthesisResult {
        throw VoiceEngineNotConfiguredException(
            "Voice cloning backend is not installed yet. " +
                "The selected backend must prove offline Urdu + English synthesis in the same cloned voice."
        )
    }

    override fun stop() = Unit
    override fun release() = Unit
}
