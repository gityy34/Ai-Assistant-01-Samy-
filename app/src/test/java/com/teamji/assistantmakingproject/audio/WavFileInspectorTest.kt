package com.teamji.assistantmakingproject.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class WavFileInspectorTest {
    @Test
    fun `accepts 16 kHz mono PCM16 WAV`() {
        val wav = createWav(sampleRate = 16_000, channels = 1, bitsPerSample = 16)
        try {
            val format = WavFileInspector.requireCloneReferenceFormat(wav)
            assertEquals(16_000, format.sampleRateHz)
            assertEquals(1, format.channels)
            assertEquals(16, format.bitsPerSample)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun `rejects stereo reference WAV`() {
        val wav = createWav(sampleRate = 16_000, channels = 2, bitsPerSample = 16)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                WavFileInspector.requireCloneReferenceFormat(wav)
            }
        } finally {
            wav.delete()
        }
    }

    private fun createWav(sampleRate: Int, channels: Int, bitsPerSample: Int): File {
        val file = File.createTempFile("voice_reference_", ".wav")
        RandomAccessFile(file, "rw").use { out ->
            val dataBytes = 320
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            out.writeBytes("RIFF")
            out.writeIntLE(dataBytes + 36)
            out.writeBytes("WAVEfmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)
            out.writeShortLE(channels)
            out.writeIntLE(sampleRate)
            out.writeIntLE(byteRate)
            out.writeShortLE(blockAlign)
            out.writeShortLE(bitsPerSample)
            out.writeBytes("data")
            out.writeIntLE(dataBytes)
            out.write(ByteArray(dataBytes))
        }
        return file
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }
}
