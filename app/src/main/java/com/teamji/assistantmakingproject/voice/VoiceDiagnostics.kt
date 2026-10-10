package com.teamji.assistantmakingproject.voice

import android.app.ActivityManager
import android.os.Build
import android.content.Context
import com.teamji.assistantmakingproject.BuildConfig

object VoiceDiagnostics {
    fun collect(context: Context): String {
        val header = "Assistant Voice Lab ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}\n"
        if (Build.VERSION.SDK_INT < 30) return header + "Native exit diagnostics require Android 11 or newer."
        return header + runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 20)
                .filter { it.processName.endsWith(":base_voice") }
            val exit = exits.firstOrNull()
            if (exit == null) "No voice-worker exit record is available."
            else {
                val summary = "Worker exit: reason=${exit.reason}, status=${exit.status}, time=${exit.timestamp}\n${exit.description}\n"
                val trace = runCatching {
                    exit.traceInputStream?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (total < buffer.size) {
                            val count = input.read(buffer, total, buffer.size - total)
                            if (count <= 0) break
                            total += count
                        }
                        String(buffer, 0, total, Charsets.UTF_8)
                    }.orEmpty()
                }.getOrDefault("Native trace is unavailable.")
                val note = if (exit.reason == android.app.ApplicationExitInfo.REASON_SIGNALED && exit.status == 9)
                    "Signal 9 may be the expected voice-worker shutdown after completion or Stop; it alone does not prove an app crash.\n"
                    else "Historical worker exit; compare with the current voice status.\n"
                note + summary + trace
            }
        }.getOrElse { "Unable to read voice diagnostics: ${it.message}" }
    }
}
