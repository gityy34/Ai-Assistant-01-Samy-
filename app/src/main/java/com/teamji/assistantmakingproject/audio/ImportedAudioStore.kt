package com.teamji.assistantmakingproject.audio

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ImportedAudioStore(private val context: Context) {
    suspend fun copyIntoPrivateStorage(uri: Uri): File {
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val output = File(dir, "imported_${System.currentTimeMillis()}.wav")
        val temp = File(dir, output.name + ".partial")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "منتخب فائل نہیں کھل سکی" }
                temp.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 2_000_000) { "چھوٹا 8–12 سیکنڈ کا WAV نمونہ منتخب کریں" }
                        out.write(buffer, 0, read)
                    }
                }
            }
            val format = WavFileInspector.requireCloneReferenceFormat(temp)
            require(format.dataBytes in 96_000L..480_000L) { "نمونہ 3–15 سیکنڈ ہونا چاہیے؛ بہتر مدت 8–12 سیکنڈ ہے" }
            currentCoroutineContext().ensureActive()
            check(temp.renameTo(output)) { "نمونہ محفوظ نہیں ہوا" }
            return output
        } finally { temp.delete() }
    }
}
