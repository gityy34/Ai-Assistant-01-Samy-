package com.teamji.assistantmakingproject.audio

import android.content.Context
import android.net.Uri
import java.io.File

class ImportedAudioStore(private val context: Context) {
    fun copyIntoPrivateStorage(uri: Uri): File {
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val output = File(dir, "imported_${System.currentTimeMillis()}.wav")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected audio" }
            output.outputStream().use { out -> input.copyTo(out) }
        }
        runCatching { WavFileInspector.requireCloneReferenceFormat(output) }
            .onFailure {
                output.delete()
                throw it
            }
        return output
    }
}
