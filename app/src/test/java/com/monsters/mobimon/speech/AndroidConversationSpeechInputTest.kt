package com.monsters.mobimon.speech

import android.os.Looper
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.VoiceInputProblem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidConversationSpeechInputTest {
    private val worker = Executors.newSingleThreadExecutor()
    private val capture = Executors.newSingleThreadExecutor()
    private val mic = FakeMicrophone()
    private val engine = FakeEngine()
    private val listener = RecordingListener()
    private var input: AndroidConversationSpeechInput? = null

    private fun create(
        permission: Boolean = true,
        models: Boolean = true,
    ): AndroidConversationSpeechInput =
        AndroidConversationSpeechInput({ permission }, { models }, { mic }, { engine }, worker, capture)
            .also { input = it }

    @After fun cleanup() {
        input?.cancel()
        engine.resume.countDown()
        worker.shutdown()
        capture.shutdown()
        worker.awaitTermination(5, TimeUnit.SECONDS)
        capture.awaitTermination(5, TimeUnit.SECONDS)
        worker.shutdownNow()
        capture.shutdownNow()
    }

    @Test fun permissionAndAvailabilityFailBeforeOpeningMicrophone() {
        create(permission = false).start(listener)
        assertEquals(listOf(VoiceInputProblem.PERMISSION), listener.failures)
        listener.failures.clear()
        create(models = false).start(listener)
        assertEquals(listOf(VoiceInputProblem.UNAVAILABLE), listener.failures)
        assertEquals(0, mic.starts)
    }

    @Test fun continuousCaptureKeepsBothPhrasesAndStopsOnce() {
        val speech = create()
        speech.start(listener)
        await { listener.ready }
        mic.feed(8, 1000)
        mic.feed(8, 0)
        await { listener.partials.size == 1 }
        mic.feed(8, 1000)
        mic.feed(8, 0)
        await { listener.partials.size == 2 }
        speech.stop()
        await { listener.results.isNotEmpty() }
        assertEquals(listOf("문장 1 문장 2"), listener.results)
        assertEquals(1, mic.starts)
        assertTrue(mic.closed)
    }

    @Test fun silenceDoesNotCallRecognizer() {
        val speech = create()
        speech.start(listener)
        await { listener.ready }
        mic.feed(8, 0)
        await { mic.reads == 8 }
        speech.stop()
        await { listener.failures.isNotEmpty() }
        assertEquals(listOf(VoiceInputProblem.NO_MATCH), listener.failures)
        assertEquals(0, engine.decodes)
    }

    @Test fun cancellationStopsMicrophoneAndRejectsBlockedNativeResult() {
        engine.block = true
        val speech = create()
        speech.start(listener)
        await { listener.ready }
        mic.feed(8, 1000)
        mic.feed(8, 0)
        assertTrue(engine.entered.await(5, TimeUnit.SECONDS))
        speech.cancel()
        assertTrue(mic.stopped)
        engine.resume.countDown()
        await { engine.closed && mic.closed }
        assertTrue(listener.results.isEmpty())
        assertTrue(listener.partials.isEmpty())
        assertTrue(listener.failures.isEmpty())
    }

    @Test fun microphoneReadErrorFailsWholeDraft() {
        mic.broken = true
        create().start(listener)
        await { listener.failures.isNotEmpty() }
        assertEquals(listOf(VoiceInputProblem.AUDIO), listener.failures)
        assertTrue(listener.results.isEmpty())
        assertTrue(mic.closed)
    }

    @Test fun microphoneKeepsReadingWhileInferenceIsBlocked() {
        engine.block = true
        val speech = create()
        speech.start(listener)
        await { listener.ready }
        mic.feed(8, 1000)
        mic.feed(8, 0)
        assertTrue(engine.entered.await(5, TimeUnit.SECONDS))
        mic.feed(20, 1000)
        await { mic.reads == 36 }
        speech.stop()
        engine.resume.countDown()
        await { listener.results.isNotEmpty() }
        assertEquals(listOf("문장 1 문장 2"), listener.results)
        assertEquals(1, mic.starts)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(2)
        }
        assertTrue("Condition did not become true", condition())
    }

    private class FakeMicrophone : MicrophoneSource {
        private val frames = LinkedBlockingQueue<ShortArray>()

        @Volatile var starts = 0

        @Volatile var reads = 0

        @Volatile var stopped = false

        @Volatile var closed = false
        var broken = false

        override fun start() {
            starts++
        }

        override fun read(buffer: ShortArray): Int {
            if (broken) return -3
            val frame = frames.take()
            frame.copyInto(buffer)
            if (frame.isNotEmpty()) reads++
            return frame.size
        }

        fun feed(
            count: Int,
            amplitude: Int,
        ) {
            repeat(count) { frames.put(ShortArray(1024) { amplitude.toShort() }) }
        }

        override fun stop() {
            stopped = true
            frames.offer(ShortArray(0))
        }

        override fun close() {
            stop()
            closed = true
        }
    }

    private class FakeEngine : LocalSpeechEngine {
        var block = false
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)

        @Volatile var decodes = 0

        @Volatile var closed = false

        override fun detector() =
            object : SpeechDetector {
                var size = 0
                var start: Int? = null
                val ranges = ArrayDeque<SpeechRange>()

                override fun accept(samples: FloatArray) {
                    if (samples.any { it != 0f }) {
                        if (start == null) start = size
                    } else {
                        flush()
                    }
                    size += samples.size
                }

                override fun speechDetected() = start != null

                override fun poll() = ranges.removeFirstOrNull()

                override fun flush() {
                    start?.let { ranges.add(SpeechRange(it, size)) }
                    start = null
                }

                override fun close() = Unit
            }

        override fun transcribe(samples: FloatArray): String {
            decodes++
            entered.countDown()
            if (block) check(resume.await(5, TimeUnit.SECONDS))
            return "문장 $decodes"
        }

        override fun close() {
            closed = true
        }
    }

    private class RecordingListener : ConversationSpeechInput.Listener {
        var ready = false
        val partials = mutableListOf<String>()
        val results = mutableListOf<String>()
        val failures = mutableListOf<VoiceInputProblem>()

        override fun onReady() {
            ready = true
        }

        override fun onLevel(level: Float) = Unit

        override fun onPartial(text: String) {
            partials += text
        }

        override fun onEndOfSpeech() = Unit

        override fun onResult(text: String) {
            results += text
        }

        override fun onFailure(problem: VoiceInputProblem) {
            failures += problem
        }
    }
}
