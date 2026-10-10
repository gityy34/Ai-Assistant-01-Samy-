package com.teamji.assistantmakingproject.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechTextPlannerTest {
    @Test fun mixedScriptsUseTheirOwnVoiceWithoutTranslation() {
        val parts = SpeechTextPlanner.plan("Hello. السلام علیکم۔ English again.", ReplyLanguage.ENGLISH)
        assertEquals(listOf(ReplyLanguage.ENGLISH, ReplyLanguage.URDU, ReplyLanguage.ENGLISH), parts.map { it.language })
        assertEquals("السلام علیکم۔", parts[1].text)
    }
    @Test fun longTextIsBoundedAndWordsArePreserved() {
        val text = "پاکستان ".repeat(400).trim()
        val parts = SpeechTextPlanner.plan(text, ReplyLanguage.URDU)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.text.length <= 220 })
        assertEquals(text, parts.joinToString(" ") { it.text })
    }
    @Test fun emojiAreRemovedAndUrduPunctuationPreserved() {
        val parts = SpeechTextPlanner.plan("🌿 السلام علیکم۔ 🤲", ReplyLanguage.URDU)
        assertEquals("السلام علیکم۔", parts.single().text)
    }
    @Test fun pureEnglishUsesEnglishEvenWithUrduPreference() {
        assertEquals(ReplyLanguage.ENGLISH, SpeechTextPlanner.plan("Hello", ReplyLanguage.URDU).single().language)
    }
    @Test(expected = IllegalArgumentException::class) fun emptySymbolsAreRejected() {
        SpeechTextPlanner.plan("🌿🤲", ReplyLanguage.URDU)
    }
}
