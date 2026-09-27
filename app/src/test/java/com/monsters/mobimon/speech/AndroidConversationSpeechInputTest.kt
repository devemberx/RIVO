package com.monsters.mobimon.speech

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.VoiceInputProblem
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
        assertTrue(engine.isDestroyed)
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
