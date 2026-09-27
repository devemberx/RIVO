package com.monsters.mobimon.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPcmCaptureTest {
    @Test
    fun stopRetainsBothTheInflightReadAndPreviouslyBufferedMicrophoneTail() {
        var stopping = false
        var reads = 0
        val modes = mutableListOf<Boolean>()
        val output = mutableListOf<Byte>()
        captureSpeechPcm(
            cancelled = { false },
            stopping = { stopping },
            read = { bytes, nonBlocking ->
                modes += nonBlocking
                reads++
                stopping = true
                if (reads <= 3) {
                    bytes[0] = reads.toByte()
                    bytes[1] = reads.toByte()
                    2
                } else {
                    0
                }
            },
            append = { bytes, count ->
                output.addAll(bytes.take(count))
                true
            },
            nativeBufferBytes = 8,
            onFailure = { error("Unexpected capture failure") },
        )
        assertEquals(listOf<Byte>(1, 1, 2, 2, 3, 3), output)
        assertEquals(listOf(false, true, true, true), modes)
    }

    @Test
    fun drainIsBoundedEvenWhenTheMicrophoneKeepsProducingAudio() {
        var bytesRead = 0
        captureSpeechPcm(
            cancelled = { false },
            stopping = { true },
            read = { bytes, nonBlocking ->
                assertTrue(nonBlocking)
                bytes.fill(1)
                bytes.size
            },
            append = { _, count ->
                bytesRead += count
                true
            },
            nativeBufferBytes = 1280,
            onFailure = { error("Unexpected capture failure") },
        )
        assertEquals(1280, bytesRead)
    }

    @Test
    fun cancellationDuringAReadDiscardsItsAudio() {
        var cancelled = false
        var delivered = false
        captureSpeechPcm(
            cancelled = { cancelled },
            stopping = { false },
            read = { bytes, _ ->
                cancelled = true
                bytes.size
            },
            append = { _, _ ->
                delivered = true
                true
            },
            nativeBufferBytes = 1280,
            onFailure = { error("Cancellation is not a capture error") },
        )
        assertTrue(cancelled)
        assertEquals(false, delivered)
    }
}
