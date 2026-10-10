package com.teamji.assistantmakingproject.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class WavAudioRecorder(private val context: Context) {
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, output: File) {
        check(!recording.get()) { "Already recording" }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is required" }

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        check(minBuffer > 0) { "Unsupported audio configuration" }
        val bufferSize = maxOf(minBuffer * 2, 4096)

        @Suppress("MissingPermission")
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            encoding,
            bufferSize,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            error("AudioRecord failed to initialize")
        }

        val raf = try {
            output.parentFile?.mkdirs()
            RandomAccessFile(output, "rw").also { file ->
                try { file.setLength(0); writeWavHeader(file, sampleRate, 1, 16, 0) }
                catch (error: Throwable) { file.close(); throw error }
            }
        } catch (error: Throwable) { audioRecord.release(); output.delete(); throw error }

        recorder = audioRecord
        recording.set(true)
        try {
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone did not enter the recording state"
            }
        } catch (error: Throwable) {
            recording.set(false)
            audioRecord.release()
            raf.close()
            output.delete()
            recorder = null
            throw error
        }

        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            var pcmBytes = 0L
            var failed = false
            try {
                while (isActive && recording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read < 0 && recording.get()) error("Microphone read failed: $read")
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                        pcmBytes += read
                    }
                }
            } catch (error: Throwable) {
                failed = true
                android.util.Log.e("VoiceRecorder", "Recording failed", error)
            } finally {
                recording.set(false)
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
                runCatching { raf.seek(0); writeWavHeader(raf, sampleRate, 1, 16, pcmBytes) }.onFailure { failed = true }
                runCatching { raf.close() }
                if (failed || pcmBytes == 0L) output.delete()
            }
        }
    }

    suspend fun stop() {
        recording.set(false)
        runCatching { recorder?.stop() }
        job?.join()
        job = null
        recorder = null
    }

    fun isRecording(): Boolean = recording.get()

    private fun writeWavHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmBytes: Long,
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffSize = pcmBytes + 36

        raf.writeBytes("RIFF")
        writeIntLE(raf, riffSize.toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1)
        writeShortLE(raf, channels)
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, blockAlign)
        writeShortLE(raf, bitsPerSample)
        raf.writeBytes("data")
        writeIntLE(raf, pcmBytes.toInt())
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
        raf.write((value shr 16) and 0xff)
        raf.write((value shr 24) and 0xff)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
    }
}
