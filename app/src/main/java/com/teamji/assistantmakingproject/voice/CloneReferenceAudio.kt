package com.teamji.assistantmakingproject.voice

import kotlin.math.abs
import kotlin.math.sqrt

object CloneReferenceAudio {
    fun prepare(samples: FloatArray, rate: Int): FloatArray {
        require(rate > 0 && samples.isNotEmpty()) { "Invalid voice recording" }
        val voiced = samples.count { abs(it) > 0.01f }
        val clipped = samples.count { abs(it) >= 0.99f }
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        require(rms >= 0.006 && voiced.toDouble() / samples.size >= 0.06) {
            "آواز بہت مدھم یا زیادہ خاموش ہے۔ مائیک کے قریب 8–12 سیکنڈ مسلسل بول کر دوبارہ ریکارڈ کریں۔"
        }
        require(clipped.toDouble() / samples.size < 0.05) {
            "ریکارڈنگ میں آواز پھٹ رہی ہے۔ مائیک سے تھوڑا دور ہو کر دوبارہ ریکارڈ کریں۔"
        }
        val first = samples.indexOfFirst { abs(it) > 0.01f }
        val last = samples.indexOfLast { abs(it) > 0.01f }
        val padding = rate / 5
        val result = samples.copyOfRange((first - padding).coerceAtLeast(0), (last + padding + 1).coerceAtMost(samples.size))
        require(result.size >= rate * 3) { "At least 3 seconds of speech is required" }
        val gain = (0.08 / rms).coerceIn(0.5, 4.0).toFloat()
        return FloatArray(result.size) { (result[it] * gain).coerceIn(-0.98f, 0.98f) }
    }
}
