package com.monsters.mobimon.speech

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidConversationSpeechInputTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val input = AndroidConversationSpeechInput(context)

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
    fun segmentedResultsAccumulateWithoutRestartAndKeepRepeatedSentences() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        val request = engine.lastRecognizerIntent
        assertEquals(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            request.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION),
        )
        assertEquals(12_000, request.getIntExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0))
        val callbacks = callbacks(engine)
        callbacks.onReadyForSpeech(Bundle())
        callbacks.onPartialResults(results("첫"))
        callbacks.onPartialResults(results("첫 문장"))
        callbacks.onSegmentResults(results("첫 문장"))
        callbacks.onPartialResults(results("두 번째"))
        assertEquals("첫 문장 두 번째", listener.partials.last())
        callbacks.onSegmentResults(results("첫 문장"))
        assertEquals("첫 문장 첫 문장", listener.partials.last())
        assertTrue(listener.results.isEmpty())
        assertFalse(engine.isDestroyed)
        assertTrue(request === engine.lastRecognizerIntent)
        input.stop()
        callbacks.onSegmentResults(results("마지막 문장"))
        callbacks.onEndOfSegmentedSession()
        callbacks.onEndOfSegmentedSession()
        callbacks.onResults(results("늦은 결과"))
        assertEquals(listOf("첫 문장 첫 문장 마지막 문장"), listener.results)
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun ignoredSegmentedOptionReusesRecognizerAfterOneTerminalResult() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        val engine = shadowOf(recognizer)
        val old = callbacks(engine)
        old.onReadyForSpeech(Bundle())
        old.onResults(results("첫 문장"))
        // Duplicate terminal callbacks before the queued restart are ignored.
        old.onResults(results("중복 결과"))
        old.onError(SpeechRecognizer.ERROR_CLIENT)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(recognizer === ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertFalse(engine.isDestroyed)
        assertFalse(engine.lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        val next = callbacks(engine)
        next.onReadyForSpeech(Bundle())
        next.onResults(results("다음 문장"))
        input.stop()
        assertEquals(listOf("첫 문장 다음 문장"), listener.results)
        assertEquals(1, listener.ready)
        assertTrue(listener.problems.isEmpty())
    }

    @Test
    fun rejectedSegmentedRequestRetriesOnceWithPlainRecognition() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        engine.triggerOnError(SpeechRecognizer.ERROR_CLIENT)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(listener.problems.isEmpty())
        assertFalse(engine.lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        engine.triggerOnError(SpeechRecognizer.ERROR_CLIENT)
        assertEquals(listOf(VoiceInputProblem.SERVICE), listener.problems)
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun continuousSpeechWithoutPartialResultsIsNotCutOffByThePauseTimer() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        callbacks(engine).onReadyForSpeech(Bundle())
        callbacks(engine).onBeginningOfSpeech()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals(0, listener.ends)
        assertFalse(engine.isDestroyed)
        callbacks(engine).onEndOfSpeech()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(1, listener.ends)
        callbacks(engine).onResults(results("긴 문장을 계속 말합니다"))
        assertEquals(listOf("긴 문장을 계속 말합니다"), listener.results)
    }

    @Test
    fun stoppingWithNoNewMatchKeepsCommittedSegmentsButNeverCommitsPartialGuesses() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        callbacks(engine).onReadyForSpeech(Bundle())
        callbacks(engine).onSegmentResults(results("확정 문장"))
        callbacks(engine).onPartialResults(results("미확정"))
        input.stop()
        engine.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf("확정 문장"), listener.results)
        assertTrue(listener.problems.isEmpty())
    }

    @Test
    fun aggregateFinalAfterSegmentsDoesNotDuplicateCommittedText() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        callbacks(engine).onReadyForSpeech(Bundle())
        callbacks(engine).onSegmentResults(results("첫 문장"))
        input.stop()
        callbacks(engine).onResults(results("첫 문장"))
        assertEquals(listOf("첫 문장"), listener.results)
    }

    @Test
    fun fullResultRetainsThirdRepeatedPhraseAfterTwoSegmentsAndEarlierUtterances() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        callbacks(engine).onReadyForSpeech(Bundle())
        callbacks(engine).onResults(results("이전 문장"))
        shadowOf(Looper.getMainLooper()).idle()
        val current = callbacks(engine)
        current.onReadyForSpeech(Bundle())
        current.onSegmentResults(results("안녕하세요"))
        current.onSegmentResults(results("안녕하세요"))
        input.stop()
        current.onResults(results("안녕하세요 안녕하세요 안녕하세요"))
        assertEquals(listOf("이전 문장 안녕하세요 안녕하세요 안녕하세요"), listener.results)
    }

    @Test
    fun sparsePartialDoesNotRestartThePauseTimerDuringContinuousSpeech() {
        assertContinuousSpeechSurvivesResultCallback { it.onPartialResults(results("아직 말하는 중")) }
    }

    @Test
    fun segmentDoesNotRestartThePauseTimerDuringContinuousSpeech() {
        assertContinuousSpeechSurvivesResultCallback { it.onSegmentResults(results("확정된 첫 구간")) }
    }

    private fun assertContinuousSpeechSurvivesResultCallback(deliver: (RecognitionListener) -> Unit) {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        val callbacks = callbacks(engine)
        callbacks.onReadyForSpeech(Bundle())
        callbacks.onBeginningOfSpeech()
        deliver(callbacks)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals("Speech results are not silence evidence", 0, listener.ends)
        assertTrue(listener.results.isEmpty())
        callbacks.onEndOfSpeech()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(1, listener.ends)
    }

    private fun callbacks(engine: ShadowSpeechRecognizer): RecognitionListener {
        // Robolectric 4.13 exposes no segment triggers; obtain its actual application listener.
        val state: Any = ReflectionHelpers.callInstanceMethod(engine, "getState")
        return ReflectionHelpers.getField(state, "recognitionListener")
    }

    @Test
    fun separateUtterancesWaitForTheNextPhraseAndStopCommitsTheCombinedDraft() {
        val listener = RecordingListener()
        assertTrue(input.isAvailable())
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        val request = engine.lastRecognizerIntent
        assertEquals("ko-KR", request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL),
        )
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertFalse(request.hasExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE))
        engine.triggerOnReadyForSpeech(Bundle())
        engine.triggerOnRmsChanged(4f)
        engine.triggerOnPartialResults(results("안녕하세요"))
        engine.triggerOnRmsChanged(7f)
        engine.triggerOnPartialResults(results("안녕하세요"))
        engine.triggerOnEndOfSpeech()
        engine.triggerOnResults(results("안녕하세요 오늘 날씨가 좋습니다"))
        assertEquals(1, listener.ready)
        assertTrue(listener.levels.contains(0.5f))
        assertEquals(0f, listener.levels[1])
        assertEquals(0, listener.ends)
        assertTrue(listener.results.isEmpty())
        assertFalse(engine.isDestroyed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        val next = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        next.triggerOnReadyForSpeech(Bundle())
        next.triggerOnPartialResults(results("다음 문장입니다"))
        next.triggerOnResults(results("다음 문장입니다"))
        input.stop()
        assertEquals(listOf("안녕하세요 오늘 날씨가 좋습니다 다음 문장입니다"), listener.results)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(1, listener.results.size)
    }

    @Test
    fun cancellingReleasesTheOldRecognizerAndRejectsItsLateResultsInANewSession() {
        val first = RecordingListener()
        input.start(first)
        shadowOf(Looper.getMainLooper()).idle()
        val old = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        input.cancel()
        assertTrue(old.isDestroyed)
        val next = RecordingListener()
        input.start(next)
        shadowOf(Looper.getMainLooper()).idle()
        old.triggerOnPartialResults(results("old partial"))
        old.triggerOnResults(results("old result"))
        old.triggerOnError(SpeechRecognizer.ERROR_CLIENT)
        assertTrue(first.results.isEmpty())
        assertTrue(first.partials.isEmpty())
        assertTrue(first.problems.isEmpty())
        assertTrue(next.results.isEmpty())
        val current = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertFalse(current.isDestroyed)
        current.triggerOnResults(results("current result"))
        input.stop()
        assertEquals(listOf("current result"), next.results)
    }

    @Test
    fun deniedPermissionNeverCreatesAMicrophoneSession() {
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val listener = RecordingListener()
        input.start(listener)
        assertEquals(listOf(VoiceInputProblem.PERMISSION), listener.problems)
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test
    fun unavailableKoreanModelIsRecoverableAndTheRecognizerIsReleased() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        engine.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(listOf(VoiceInputProblem.LANGUAGE), listener.problems)
        assertTrue(engine.isDestroyed)
        engine.triggerOnResults(results("late result"))
        assertTrue(listener.results.isEmpty())
    }

    @Test
    fun ambientRmsNeverAnimatesBeforeSpeechDetectionAndSilenceWaitIsBounded() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val engine = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        engine.triggerOnReadyForSpeech(Bundle())
        listOf(-2f, 0f, 2f, 8f).forEach(engine::triggerOnRmsChanged)
        assertTrue(listener.levels.all { it == 0f })
        engine.triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertTrue(listener.problems.isEmpty())
        val next = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        next.triggerOnReadyForSpeech(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals(1, listener.ends)
        next.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf(VoiceInputProblem.NO_MATCH), listener.problems)
        assertTrue(next.isDestroyed)
    }

    @Test
    fun cancellationDuringThePhrasePauseRejectsRestartAndQueuedIdleCompletion() {
        val listener = RecordingListener()
        input.start(listener)
        shadowOf(Looper.getMainLooper()).idle()
        val current = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        val engine = shadowOf(current)
        engine.triggerOnReadyForSpeech(Bundle())
        engine.triggerOnResults(results("첫 문장"))
        input.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(current, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertTrue(listener.results.isEmpty())
        assertEquals(0, listener.ends)
    }

    private fun results(text: String) =
        Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        }

    private class RecordingListener : ConversationSpeechInput.Listener {
        var ready = 0
        var ends = 0
        val levels = mutableListOf<Float>()
        val partials = mutableListOf<String>()
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
