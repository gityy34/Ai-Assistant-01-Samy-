package com.teamji.assistantmakingproject.audio

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

data class WavFormat(
    val audioFormat: Int,
    val channels: Int,
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val dataBytes: Long,
)

object WavFileInspector {
    fun requireCloneReferenceFormat(file: File): WavFormat {
        val format = inspect(file)
        require(format.audioFormat == 1) { "Import must be an uncompressed PCM WAV file" }
        require(format.channels == 1) { "Import must be mono" }
        require(format.sampleRateHz == 16_000) { "Import must use a 16 kHz sample rate" }
        require(format.bitsPerSample == 16) { "Import must use 16-bit PCM audio" }
        require(format.dataBytes > 0) { "Imported WAV contains no audio" }
        return format
    }

    fun inspect(file: File): WavFormat = RandomAccessFile(file, "r").use { wav ->
        require(wav.length() >= 44L) { "Selected file is too small to be a WAV recording" }
        require(wav.readAscii(4) == "RIFF") { "Selected file is not a RIFF WAV file" }
        wav.skipBytes(4)
        require(wav.readAscii(4) == "WAVE") { "Selected file is not a WAVE file" }

        var audioFormat: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var dataBytes: Long? = null

        while (wav.filePointer + 8 <= wav.length()) {
            val chunkId = wav.readAscii(4)
            val chunkSize = wav.readUInt32LE()
            val chunkStart = wav.filePointer
            val chunkEnd = chunkStart + chunkSize
            require(chunkEnd <= wav.length()) { "WAV chunk is truncated" }

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16L) { "WAV format chunk is invalid" }
                    audioFormat = wav.readUInt16LE()
                    channels = wav.readUInt16LE()
                    sampleRate = wav.readUInt32LE().toInt()
                    wav.skipBytes(6)
                    bitsPerSample = wav.readUInt16LE()
                }
                "data" -> dataBytes = chunkSize
            }

            val paddedEnd = chunkEnd + (chunkSize and 1L)
            wav.seek(paddedEnd.coerceAtMost(wav.length()))
            if (audioFormat != null && dataBytes != null) break
        }

        WavFormat(
            audioFormat = requireNotNull(audioFormat) { "WAV format chunk is missing" },
            channels = requireNotNull(channels) { "WAV channel count is missing" },
            sampleRateHz = requireNotNull(sampleRate) { "WAV sample rate is missing" },
            bitsPerSample = requireNotNull(bitsPerSample) { "WAV bit depth is missing" },
            dataBytes = requireNotNull(dataBytes) { "WAV data chunk is missing" },
        )
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        if (read(bytes) != length) throw EOFException("Unexpected end of WAV file")
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUInt16LE(): Int {
        val b0 = read()
        val b1 = read()
        if (b0 < 0 || b1 < 0) throw EOFException("Unexpected end of WAV file")
        return b0 or (b1 shl 8)
    }

    private fun RandomAccessFile.readUInt32LE(): Long {
        var value = 0L
        repeat(4) { index ->
            val byte = read()
            if (byte < 0) throw EOFException("Unexpected end of WAV file")
            value = value or (byte.toLong() shl (index * 8))
        }
        return value
    }
}
