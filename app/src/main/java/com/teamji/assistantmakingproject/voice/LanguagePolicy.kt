package com.teamji.assistantmakingproject.voice

/**
 * Project rule:
 * - Urdu is the default reply language, regardless of input language.
 * - English is used only when the user explicitly requests it or the chat setting is changed.
 *
 * Demo 1 exposes the language selector manually. Demo 2 will resolve this from user intent/chat state.
 */
object LanguagePolicy {
    val defaultLanguage: ReplyLanguage = ReplyLanguage.URDU

    fun resolve(explicit: ReplyLanguage?): ReplyLanguage = explicit ?: defaultLanguage
}
