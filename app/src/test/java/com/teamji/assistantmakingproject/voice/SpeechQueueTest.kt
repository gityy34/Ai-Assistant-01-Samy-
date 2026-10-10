package com.teamji.assistantmakingproject.voice

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpeechQueueTest {
    @Test fun preparesAheadWhileTheFirstPartIsPlaying() = runBlocking {
        val events = mutableListOf<String>()
        val parts = List(4) { SpeechPart("Part $it", ReplyLanguage.ENGLISH) }
        SpeechQueue.play(parts, prepare = { index, _ ->
            delay(10)
            events.add("prepared$index")
            index
        }, consume = { index, _ ->
            events.add("start$index")
            delay(60)
            events.add("end$index")
        })
        assertTrue(events.indexOf("prepared1") < events.indexOf("start0"))
        assertTrue(events.indexOf("prepared2") < events.indexOf("end0"))
        assertEquals(listOf("start0", "start1", "start2", "start3"), events.filter { it.startsWith("start") })
    }
    @Test(expected = IllegalStateException::class) fun preparationFailureIsNotReportedAsCompletion() = runBlocking {
        SpeechQueue.play(listOf(SpeechPart("Hello", ReplyLanguage.ENGLISH)),
            prepare = { _, _ -> error("Preparation failed") }, consume = { _, _: Int -> })
    }
}
