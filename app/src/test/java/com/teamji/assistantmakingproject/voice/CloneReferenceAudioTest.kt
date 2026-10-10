package com.teamji.assistantmakingproject.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class CloneReferenceAudioTest {
    @Test(expected = IllegalArgumentException::class) fun rejectsSilence() {
        CloneReferenceAudio.prepare(FloatArray(64_000), 16_000)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsClipping() {
        CloneReferenceAudio.prepare(FloatArray(64_000) { 1f }, 16_000)
    }
    @Test fun gainPreservesWaveformAndDoesNotChangePitch() {
        val samples = FloatArray(64_000) { (0.03 * sin(it * 0.04)).toFloat() }
        val output = CloneReferenceAudio.prepare(samples, 16_000)
        assertEquals(samples.size, output.size)
        val gain = output[1] / samples[1]
        for (i in samples.indices step 100) assertEquals(samples[i] * gain, output[i], 0.0001f)
    }
}
