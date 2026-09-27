package com.monsters.mobimon.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioBufferTest {
    @Test
    fun slowConsumerReceivesEverySampleInOrderIncludingTheLastShortRead() {
        val buffer = SpeechAudioBuffer(12)
        val capture = byteArrayOf(1, 2, 3, 4)
        assertTrue(buffer.append(capture, 4))
        capture.fill(9)
        assertTrue(buffer.append(byteArrayOf(5, 6, 99, 99), 2))
        buffer.finish()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), buffer.read())
        assertArrayEquals(byteArrayOf(5, 6), buffer.read())
        assertNull(buffer.read())
    }

    @Test
    fun overflowIsExplicitAndNeverOverwritesQueuedSpeech() {
        val buffer = SpeechAudioBuffer(4)
        assertTrue(buffer.append(byteArrayOf(1, 2, 3, 4), 4))
        assertFalse(buffer.append(byteArrayOf(5, 6), 2))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), buffer.read())
        assertTrue(buffer.append(byteArrayOf(5, 6), 2))
        buffer.finish()
        assertArrayEquals(byteArrayOf(5, 6), buffer.read())
        assertNull(buffer.read())
    }

    @Test
    fun cancellationDropsPendingAudioAndRejectsLateProducerData() {
        val buffer = SpeechAudioBuffer(4)
        assertTrue(buffer.append(byteArrayOf(1, 2), 2))
        buffer.cancel()
        assertFalse(buffer.append(byteArrayOf(3, 4), 2))
        assertNull(buffer.read())
    }
}
