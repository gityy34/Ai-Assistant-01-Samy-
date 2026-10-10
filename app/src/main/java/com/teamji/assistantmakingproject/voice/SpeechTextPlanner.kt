package com.teamji.assistantmakingproject.voice

data class SpeechPart(val text: String, val language: ReplyLanguage)

/** Routes script runs to their own phonemizer; never translates or rewrites meaning. */
object SpeechTextPlanner {
    fun plan(input: String, preferred: ReplyLanguage): List<SpeechPart> {
        require(input.length <= 20_000) { "Please use up to 20,000 characters" }
        val cleaned = buildString {
            input.codePoints().forEach { cp ->
                if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) ||
                    cp in listOf(46, 44, 33, 63, 58, 59, 39, 45, 0x060C, 0x061B, 0x061F, 0x06D4) ||
                    Character.getType(cp) == Character.NON_SPACING_MARK.toInt()) appendCodePoint(cp)
                else append(' ')
            }
        }.replace('ك', 'ک').replace('ي', 'ی').replace(Regex("\\s+"), " ").trim()
        require(cleaned.isNotBlank()) { "Enter readable Urdu or English text" }
        val result = mutableListOf<SpeechPart>()
        var language = preferred
        val run = StringBuilder()
        fun flush() {
            var remaining = run.toString().trim()
            while (remaining.isNotEmpty()) {
                var end = minOf(remaining.length, 220)
                if (end < remaining.length) {
                    val boundary = remaining.lastIndexOf(' ', end)
                    if (boundary > 0) end = boundary
                }
                val part = remaining.substring(0, end).trim()
                if (part.isNotBlank()) result.add(SpeechPart(part, language))
                remaining = remaining.substring(end).trimStart()
            }
            run.clear()
        }
        cleaned.forEach { ch ->
            val detected = when {
                ch.isLetter() && ch.code in 0x0600..0x06FF -> ReplyLanguage.URDU
                ch.isLetter() && ch.code < 0x0250 -> ReplyLanguage.ENGLISH
                else -> null
            }
            if (detected != null && detected != language) { flush(); language = detected }
            run.append(ch)
            if (ch in ".!?۔؟\n") flush()
        }
        flush()
        return result
    }
}
