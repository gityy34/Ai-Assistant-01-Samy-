package com.teamji.assistantmakingproject.voice

/** Remove only unusually long near-silent runs, never change voiced samples or reading speed. */
object SpeechPacing {
    fun compact(samples: ShortArray, rate: Int): ShortArray {
        require(rate in 8_000..48_000)
        if (samples.isEmpty()) return samples
        val frameSize = rate / 50 // 20 ms
        val frames = (samples.size + frameSize - 1) / frameSize
        val quiet = BooleanArray(frames) { frame ->
            val end = minOf(samples.size, (frame + 1) * frameSize)
            (frame * frameSize until end).all { kotlin.math.abs(samples[it].toInt()) < 80 }
        }
        if (quiet.all { it }) return samples // Preserve a signal that is uniformly very quiet.
        val output = ShortArray(samples.size)
        var count = 0
        var frame = 0
        fun append(start: Int, end: Int) {
            val length = end - start
            samples.copyInto(output, count, start, end)
            count += length
        }
        while (frame < frames) {
            val startFrame = frame
            if (!quiet[frame]) {
                frame++
                append(startFrame * frameSize, minOf(samples.size, frame * frameSize))
                continue
            }
            while (frame < frames && quiet[frame]) frame++
            val start = startFrame * frameSize
            val end = minOf(samples.size, frame * frameSize)
            val edge = start == 0 || end == samples.size
            val limit = if (edge) rate * 120 / 1000 else rate * 350 / 1000
            val threshold = if (edge) rate * 250 / 1000 else rate * 750 / 1000
            if (end - start > threshold) {
                if (start == 0) append(end - limit, end)
                else if (end == samples.size) append(start, start + limit)
                else {
                    append(start, start + limit / 2)
                    append(end - (limit - limit / 2), end)
                }
            } else append(start, end)
        }
        return if (count == samples.size) samples else output.copyOf(count)
    }
}
