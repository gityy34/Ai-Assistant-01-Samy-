package com.teamji.assistantmakingproject.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePolicyTest {
    @Test
    fun `default is Urdu`() {
        assertEquals(ReplyLanguage.URDU, LanguagePolicy.resolve(null))
    }

    @Test
    fun `explicit English overrides default`() {
        assertEquals(ReplyLanguage.ENGLISH, LanguagePolicy.resolve(ReplyLanguage.ENGLISH))
    }
}
