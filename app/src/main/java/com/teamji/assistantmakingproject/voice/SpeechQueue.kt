package com.teamji.assistantmakingproject.voice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Two chunks are ready before playback; synthesis then overlaps reading. */
object SpeechQueue {
    suspend fun <T> play(parts: List<SpeechPart>, prepare: suspend (Int, SpeechPart) -> T,
                        consume: suspend (Int, T) -> Unit) = coroutineScope {
        val channel = Channel<Pair<Int, T>>(2)
        val producer = launch {
            try {
                parts.forEachIndexed { index, part -> channel.send(index to prepare(index, part)) }
            } finally { channel.close() }
        }
        try {
            val first = channel.receiveCatching().getOrNull() ?: return@coroutineScope
            val second = channel.receiveCatching().getOrNull()
            consume(first.first, first.second)
            second?.let { consume(it.first, it.second) }
            for ((index, audio) in channel) consume(index, audio)
        } finally { producer.cancel(); channel.cancel() }
    }
}
