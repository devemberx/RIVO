package com.monsters.mobimon.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechProcessorTest {
    @Test
    fun silenceNeverReachesTranscription() {
        val engine = RecordingEngine()
        SpeechProcessor(engine).use { processor ->
            repeat(160) { processor.accept(FloatArray(1_000)) }
            assertEquals("", processor.finish())
        }
        assertTrue(engine.inputs.isEmpty())
        assertTrue(engine.detector.closed)
    }

    @Test
    fun speechKeepsContextAndWaitsForTrailingAudioBeforeDecode() {
        val engine = RecordingEngine()
        val partials = mutableListOf<String>()
        SpeechProcessor(engine, partials::add).use { processor ->
            engine.detector.ranges += SpeechRange(4_000, 8_000)
            processor.accept(FloatArray(10_000) { it.toFloat() })
            assertTrue(engine.inputs.isEmpty())
            processor.accept(FloatArray(2_000) { (it + 10_000).toFloat() })
            assertArrayEquals(FloatArray(12_000) { it.toFloat() }, engine.inputs.single(), 0f)
            assertEquals(listOf("문장 1"), partials)
            assertEquals("문장 1", processor.finish())
        }
    }

    @Test
    fun separatePhrasesKeepOneDraftWithoutDuplicatingOverlappingContext() {
        val engine = RecordingEngine()
        SpeechProcessor(engine).use { processor ->
            engine.detector.ranges += SpeechRange(1_000, 6_000)
            processor.accept(FloatArray(10_000) { it.toFloat() })
            processor.accept(FloatArray(96_000) { (it + 10_000).toFloat() })
            engine.detector.ranges += SpeechRange(107_000, 110_000)
            processor.accept(FloatArray(8_000) { (it + 106_000).toFloat() })
            assertEquals("문장 1 문장 2", processor.finish())
            assertEquals(0f, engine.inputs.first().first())
            assertEquals(103_000f, engine.inputs.last().first())
        }
    }

    @Test
    fun stopFlushesLastPartialFrameAndIncludesLastRealSample() {
        val engine = RecordingEngine()
        engine.detector.atFlush = SpeechRange(10, 123)
        SpeechProcessor(engine).use { processor ->
            processor.accept(FloatArray(123) { it.toFloat() })
            assertEquals("문장 1", processor.finish())
            assertEquals(512, engine.detector.accepted)
            assertEquals(123, engine.inputs.single().size)
            assertEquals(122f, engine.inputs.single().last())
            assertEquals("문장 1", processor.finish())
            assertEquals(1, engine.inputs.size)
        }
    }

    @Test
    fun audioBoundRejectsOverflowInsteadOfDroppingSamples() {
        val engine = RecordingEngine()
        SpeechProcessor(engine).use { processor ->
            processor.accept(FloatArray(960_000))
            val failure = runCatching { processor.accept(floatArrayOf(1f)) }.exceptionOrNull()
            assertTrue(failure is SpeechInputException)
            assertEquals(960_000, engine.detector.accepted)
        }
    }

    @Test
    fun ongoingSpeechKeepsInactivityOpenAndPureSilenceReachesBound() {
        val engine = RecordingEngine()
        SpeechProcessor(engine).use { processor ->
            engine.detector.speech = true
            processor.accept(FloatArray(192_000))
            assertEquals(0, processor.samplesSinceSpeech)
            engine.detector.speech = false
            processor.accept(FloatArray(192_000))
            assertEquals(192_000, processor.samplesSinceSpeech)
        }
    }

    private class RecordingEngine : LocalSpeechEngine {
        val detector = RecordingDetector()
        val inputs = mutableListOf<FloatArray>()

        override fun detector(): SpeechDetector = detector

        override fun transcribe(samples: FloatArray): String {
            inputs += samples
            return "문장 ${inputs.size}"
        }

        override fun close() = Unit
    }

    private class RecordingDetector : SpeechDetector {
        val ranges = ArrayDeque<SpeechRange>()
        var atFlush: SpeechRange? = null
        var speech = false
        var accepted = 0
        var closed = false

        override fun accept(samples: FloatArray) {
            accepted += samples.size
        }

        override fun speechDetected() = speech

        override fun poll(): SpeechRange? = ranges.removeFirstOrNull()

        override fun flush() {
            atFlush?.let(ranges::addLast)
        }

        override fun close() {
            closed = true
        }
    }
}
