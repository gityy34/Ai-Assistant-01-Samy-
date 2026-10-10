package com.teamji.assistantmakingproject.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class PcmPlayer {
    var speed: Float = 1f
    suspend fun playAndWait(samples: ShortArray, sampleRateHz: Int) {
        play(samples, sampleRateHz)
        val active = checkNotNull(track)
        try {
            kotlinx.coroutines.withTimeout(samples.size.toLong() * 1000 / sampleRateHz + 15_000) {
                while (track === active && active.playbackHeadPosition < samples.size) {
                    kotlinx.coroutines.delay(25)
                }
            }
        } finally {
            if (track === active) stop()
        }
    }

    private var track: AudioTrack? = null

    fun play(samples: ShortArray, sampleRateHz: Int) {
        require(samples.isNotEmpty()) { "Synthesizer returned no audio" }
        require(sampleRateHz in 8_000..48_000) { "Unsupported output sample rate: $sampleRateHz Hz" }
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "Unable to create audio output" }
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = audioTrack
        try {
            // MODE_STATIC is STATE_NO_STATIC_DATA until PCM has been written.
            check(audioTrack.state != AudioTrack.STATE_UNINITIALIZED) { "Audio output initialization failed" }
            val written = audioTrack.write(samples, 0, samples.size)
            check(written == samples.size) { "Audio output accepted only $written of ${samples.size} samples" }
            check(audioTrack.state == AudioTrack.STATE_INITIALIZED) { "Audio output buffer was not prepared" }
            audioTrack.playbackParams = android.media.PlaybackParams().setSpeed(speed).setPitch(1f)
            audioTrack.play()
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }
}
