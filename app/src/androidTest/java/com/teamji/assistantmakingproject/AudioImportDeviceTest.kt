package com.teamji.assistantmakingproject

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.teamji.assistantmakingproject.audio.ImportedAudioStore
import com.teamji.assistantmakingproject.audio.WavFileInspector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioImportDeviceTest {
    @Test fun invalidAndOversizedFilesDoNotBecomeRestoredSamples() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val before = dir.listFiles()?.map { it.name }?.toSet().orEmpty()
        val source = File(context.cacheDir, "invalid-import.wav")
        try {
            for (data in listOf("not a wave".toByteArray(), ByteArray(2_000_001))) {
                source.writeBytes(data)
                assertTrue(runCatching { ImportedAudioStore(context).copyIntoPrivateStorage(Uri.fromFile(source)) }.isFailure)
                assertEquals(before, dir.listFiles()?.map { it.name }?.toSet().orEmpty())
            }
        } finally { source.delete() }
    }
    @Test fun validImportIsPublishedOnlyAfterFormatValidation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "valid-import.wav")
        var imported: File? = null
        try {
            val count = 64_000
            val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
            bytes.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16_000).putInt(32_000)
            bytes.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(count * 2)
            repeat(count) { bytes.putShort(if (it % 32 < 16) 1000.toShort() else (-1000).toShort()) }
            source.writeBytes(bytes.array())
            imported = ImportedAudioStore(context).copyIntoPrivateStorage(Uri.fromFile(source))
            assertEquals(128_000L, WavFileInspector.requireCloneReferenceFormat(imported!!).dataBytes)
            assertEquals("wav", imported!!.extension)
        } finally { source.delete(); imported?.delete() }
    }
}
