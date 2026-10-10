package com.teamji.assistantmakingproject.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechPacingTest {
    @Test fun longInternalPauseBecomesShortWithoutChangingSpeech() {
        val voice = ShortArray(16_000) { if (it % 32 < 16) 2000 else -2000 }
        val audio = voice + ShortArray(48_000) + voice
        val result = SpeechPacing.compact(audio, 16_000)
        assertEquals(37_600, result.size)
        assertArrayEquals(voice, result.copyOfRange(0, 16_000))
        assertArrayEquals(voice, result.copyOfRange(result.size - 16_000, result.size))
    }
    @Test fun normalPauseAndQuietSpeechArePreserved() {
        val voice = ShortArray(16_000) { 200 }
        val audio = voice + ShortArray(6400) + voice
        assertArrayEquals(audio, SpeechPacing.compact(audio, 16_000))
        val quiet = ShortArray(64_000) { 40 }
        assertArrayEquals(quiet, SpeechPacing.compact(quiet, 16_000))
    }
    @Test fun leadingAndTrailingPaddingDoesNotDelayPlayback() {
        val voice = ShortArray(16_000) { 500 }
        val result = SpeechPacing.compact(ShortArray(16_000) + voice + ShortArray(16_000), 16_000)
        assertEquals(19_840, result.size)
        assertArrayEquals(voice, result.copyOfRange(1920, 17_920))
    }
}
