package com.monsters.mobimon.speech

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.VoiceInputProblem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.util.ReflectionHelpers
import java.io.FileDescriptor
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidConversationSpeechInputTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val streams = mutableListOf<FakeAudio>()
    private val input = AndroidConversationSpeechInput(context) { FakeAudio().also(streams::add) }

    @Before
    fun setUp() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        packageName = "test.recognition"
                        name = "test.recognition.Service"
                    }
            },
        )
    }

    @After
    fun tearDown() = input.cancel()

    @Test
    fun externalPcmSourceKeepsOneRequestAndWaitsForMicrophoneAndServiceReadiness() {
        val listener = RecordingListener()
        val engine = start(listener)
        val request = engine.lastRecognizerIntent
        assertEquals(
            RecognizerIntent.EXTRA_AUDIO_SOURCE,
            request.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION),
        )
        assertTrue(request.hasExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE))
        assertEquals(16_000, request.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 0))
        assertEquals(1, request.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 0))
        assertEquals(
            AudioFormat.ENCODING_PCM_16BIT,
            request.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 0),
        )
        assertEquals("ko-KR", request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        callbacks(engine).onReadyForSpeech(Bundle())
        assertEquals(0, listener.ready)
        streams.single().ready()
        assertEquals(1, listener.ready)
        repeat(3) {
            callbacks(engine).onSegmentResults(results("안녕하세요"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        }
        assertEquals("안녕하세요 안녕하세요 안녕하세요", listener.committed.last())
        assertTrue(request === engine.lastRecognizerIntent)
        assertEquals(1, streams.size)
        input.stop()
        assertTrue(streams.single().stopped)
        assertFalse(streams.single().cancelled)
        assertTrue(listener.results.isEmpty())
        callbacks(engine).onSegmentResults(results("마지막 문장"))
        callbacks(engine).onEndOfSegmentedSession()
        assertEquals(listOf("안녕하세요 안녕하세요 안녕하세요 마지막 문장"), listener.results)
        assertTrue(streams.single().cancelled)
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun finalAggregateRetainsTheThirdRepeatedPhraseWithoutDuplicatingEarlierSegments() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        val callback = callbacks(engine)
        callback.onSegmentResults(results("안녕하세요"))
        callback.onSegmentResults(results("안녕하세요"))
        input.stop()
        callback.onResults(results("안녕하세요 안녕하세요 안녕하세요"))
        callback.onResults(results("늦은 결과"))
        callback.onEndOfSegmentedSession()
        assertEquals(listOf("안녕하세요 안녕하세요 안녕하세요"), listener.results)
        assertEquals("안녕하세요 안녕하세요 안녕하세요", listener.committed.last())
    }

    @Test
    fun stopWithNoNewMatchKeepsConfirmedSegmentsAndNeverCommitsPartialGuesses() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        callbacks(engine).onSegmentResults(results("확정 문장"))
        callbacks(engine).onPartialResults(results("미확정"))
        input.stop()
        callbacks(engine).onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf("확정 문장"), listener.results)
        assertTrue(listener.problems.isEmpty())
    }

    @Test
    fun serviceFailurePublishesConfirmedTextBeforeReleasingAudio() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        callbacks(engine).onSegmentResults(results("확정 문장"))
        callbacks(engine).onError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(listOf("확정 문장"), listener.committed)
        assertEquals(listOf(VoiceInputProblem.NETWORK), listener.problems)
        assertTrue(streams.single().cancelled)
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun microphoneFailureReleasesTheRecognizerAndKeepsConfirmedTextAvailable() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        callbacks(engine).onSegmentResults(results("확정 문장"))
        streams.single().failure()
        assertEquals(listOf("확정 문장"), listener.committed)
        assertEquals(listOf(VoiceInputProblem.AUDIO), listener.problems)
        assertTrue(streams.single().cancelled)
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun unsupportedOrPrematurelyEndedStreamDoesNotSilentlyRestartTheMicrophone() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        callbacks(engine).onResults(results("서비스가 먼저 끝낸 문장"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf("서비스가 먼저 끝낸 문장"), listener.committed)
        assertEquals(listOf(VoiceInputProblem.SERVICE), listener.problems)
        assertEquals(1, streams.size)
        assertTrue(streams.single().cancelled)
    }

    @Test
    fun rejectedAudioSourceIsReportedWithoutAnUnverifiedMicrophoneFallback() {
        val listener = RecordingListener()
        val engine = start(listener)
        callbacks(engine).onError(SpeechRecognizer.ERROR_CLIENT)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(VoiceInputProblem.SERVICE), listener.problems)
        assertEquals(1, streams.size)
        assertTrue(streams.single().cancelled)
    }

    @Test
    fun continuousSpeechAndSparseResultsDoNotRestartThePauseTimer() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        val callback = callbacks(engine)
        callback.onBeginningOfSpeech()
        callback.onPartialResults(results("말하는 중"))
        callback.onSegmentResults(results("첫 문장"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals(0, listener.ends)
        assertFalse(streams.single().stopped)
        callback.onEndOfSpeech()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(1, listener.ends)
        assertTrue(streams.single().stopped)
    }

    @Test
    fun delayedMicrophoneReadinessDoesNotArmAPauseTimerOverAlreadyDetectedSpeech() {
        val listener = RecordingListener()
        val engine = start(listener)
        callbacks(engine).onReadyForSpeech(Bundle())
        callbacks(engine).onBeginningOfSpeech()
        streams.single().ready()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals(1, listener.ready)
        assertEquals(0, listener.ends)
        assertFalse(streams.single().stopped)
    }

    @Test
    fun emptySegmentsDoNotExtendSilenceAndAmbientRmsCannotAnimateBeforeSpeech() {
        val listener = RecordingListener()
        val engine = startReady(listener)
        repeat(12) {
            callbacks(engine).onRmsChanged(8f)
            callbacks(engine).onSegmentResults(results(""))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
        assertTrue(listener.levels.all { it == 0f })
        assertEquals(1, listener.ends)
        callbacks(engine).onEndOfSegmentedSession()
        assertEquals(listOf(VoiceInputProblem.NO_MATCH), listener.problems)
    }

    @Test
    fun cancellationRejectsOldAudioReadinessErrorsAndRecognitionResults() {
        val oldListener = RecordingListener()
        val oldEngine = start(oldListener)
        val oldCallback = callbacks(oldEngine)
        val oldStream = streams.single()
        input.cancel()
        val listener = RecordingListener()
        val engine = startReady(listener)
        oldStream.ready()
        oldStream.failure()
        oldCallback.onSegmentResults(results("이전 녹음"))
        oldCallback.onResults(results("이전 녹음"))
        assertTrue(oldStream.cancelled)
        assertTrue(oldListener.committed.isEmpty())
        assertTrue(oldListener.problems.isEmpty())
        assertTrue(listener.committed.isEmpty())
        assertTrue(listener.problems.isEmpty())
        input.stop()
        callbacks(engine).onResults(results("새 녹음"))
        assertEquals(listOf("새 녹음"), listener.results)
    }

    @Test
    fun permissionDenialNeverCreatesAudioOrRecognitionResources() {
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val listener = RecordingListener()
        input.start(listener)
        assertEquals(listOf(VoiceInputProblem.PERMISSION), listener.problems)
        assertTrue(streams.isEmpty())
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    private fun start(listener: RecordingListener): ShadowSpeechRecognizer {
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        return shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    private fun startReady(listener: RecordingListener): ShadowSpeechRecognizer =
        start(listener).also {
            callbacks(it).onReadyForSpeech(Bundle())
            streams.last().ready()
        }

    private fun callbacks(engine: ShadowSpeechRecognizer): RecognitionListener {
        // Robolectric 4.13 exposes no segment triggers; obtain its actual application listener.
        val state: Any = ReflectionHelpers.callInstanceMethod(engine, "getState")
        return ReflectionHelpers.getField(state, "recognitionListener")
    }

    private fun results(text: String) =
        Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        }

    private class FakeAudio : SpeechAudio {
        // The speech shadow never consumes PCM; this descriptor owns no OS resource.
        override val source: ParcelFileDescriptor =
            ReflectionHelpers.callConstructor(
                ParcelFileDescriptor::class.java,
                ReflectionHelpers.ClassParameter.from(FileDescriptor::class.java, FileDescriptor()),
            )
        lateinit var ready: () -> Unit
        lateinit var failure: () -> Unit
        var stopped = false
        var cancelled = false

        override fun start(
            onReady: () -> Unit,
            onFailure: () -> Unit,
        ) {
            ready = onReady
            failure = onFailure
        }

        override fun stop() {
            stopped = true
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class RecordingListener : ConversationSpeechInput.Listener {
        var ready = 0
        var ends = 0
        val levels = mutableListOf<Float>()
        val partials = mutableListOf<String>()
        val committed = mutableListOf<String>()
        val results = mutableListOf<String>()
        val problems = mutableListOf<VoiceInputProblem>()

        override fun onReady() {
            ready++
        }

        override fun onLevel(level: Float) {
            levels += level
        }

        override fun onPartial(text: String) {
            partials += text
        }

        override fun onCommitted(text: String) {
            committed += text
        }

        override fun onEndOfSpeech() {
            ends++
        }

        override fun onResult(text: String) {
            results += text
        }

        override fun onFailure(problem: VoiceInputProblem) {
            problems += problem
        }
    }
}
