package com.monsters.mobimon.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.VoiceInputProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A bounded foreground recording streams PCM without microphone handoffs between phrases. */
@Singleton
class AndroidConversationSpeechInput internal constructor(
    private val context: Context,
    private val createAudio: () -> SpeechAudio,
) : ConversationSpeechInput {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context, ::BufferedSpeechAudio)

    private var recognizer: SpeechRecognizer? = null
    private var audio: SpeechAudio? = null
    private var listener: ConversationSpeechInput.Listener? = null
    private var generation = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val phrases = mutableListOf<String>()
    private var stopping = false
    private var ready = false
    private var microphoneReady = false
    private var serviceReady = false
    private var speechInProgress = false
    private var inactivity: Runnable? = null

    override fun isAvailable(): Boolean =
        try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) || SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: RuntimeException) {
            false
        }

    override fun start(listener: ConversationSpeechInput.Listener) {
        requireMainThread()
        cancel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            listener.onFailure(VoiceInputProblem.PERMISSION)
            return
        }
        if (!isAvailable()) {
            listener.onFailure(VoiceInputProblem.UNAVAILABLE)
            return
        }
        this.listener = listener
        val session = generation
        try {
            val engine =
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            recognizer = engine
            val stream = createAudio().also { audio = it }
            engine.setRecognitionListener(
                object : RecognitionListener {
                    private var speechDetected = false
                    private var partial = ""

                    private fun current() =
                        session == generation &&
                            recognizer === engine &&
                            this@AndroidConversationSpeechInput.listener != null

                    override fun onReadyForSpeech(params: Bundle?) {
                        if (!current() || stopping) return
                        serviceReady = true
                        publishReady(session)
                    }

                    override fun onBeginningOfSpeech() {
                        if (!current() || stopping) return
                        speechDetected = true
                        speechInProgress = true
                        clearWait()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (current() && rmsdB.isFinite()) {
                            listener.onLevel(if (speechDetected) ((rmsdB - 2f) / 10f).coerceIn(0f, 1f) else 0f)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        if (!current()) return
                        speechDetected = false
                        speechInProgress = false
                        listener.onLevel(0f)
                        if (!stopping) waitForNextPhrase(session)
                    }

                    override fun onSegmentResults(segmentResults: Bundle) {
                        if (!current()) return
                        partial = ""
                        val text = transcript(segmentResults).trim()
                        if (text.isNotEmpty()) {
                            phrases += text
                            listener.onCommitted(combined())
                            if (!stopping && !speechInProgress) waitForNextPhrase(session)
                        }
                        listener.onPartial(combined())
                    }

                    override fun onEndOfSegmentedSession() {
                        if (!current()) return
                        if (stopping) finishDraft(session) else fail(session, VoiceInputProblem.SERVICE)
                    }

                    override fun onError(error: Int) {
                        if (!current()) return
                        if (stopping &&
                            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        ) {
                            finishDraft(session)
                        } else {
                            fail(session, recognitionProblem(error))
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (!current()) return
                        val text = transcript(results).trim()
                        // A full result replaces this request's segments, never a deduplicated set of words.
                        if (text.isNotEmpty()) {
                            phrases.clear()
                            phrases += text
                            listener.onCommitted(combined())
                        }
                        if (stopping) finishDraft(session) else fail(session, VoiceInputProblem.SERVICE)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!current() || stopping) return
                        val text = transcript(partialResults).trim()
                        if (text.isNotEmpty() && text != partial) {
                            speechDetected = true
                            partial = text
                            if (!speechInProgress) waitForNextPhrase(session)
                        }
                        listener.onPartial(combined(text))
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?,
                    ) = Unit
                },
            )
            engine.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, stream.source)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, BufferedSpeechAudio.SAMPLE_RATE)
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                },
            )
            stream.start(
                onReady = {
                    if (session == generation && !stopping) {
                        microphoneReady = true
                        publishReady(session)
                    }
                },
                onFailure = { fail(session, VoiceInputProblem.AUDIO) },
            )
        } catch (_: SecurityException) {
            fail(session, VoiceInputProblem.PERMISSION)
        } catch (_: Exception) {
            fail(session, VoiceInputProblem.SERVICE)
        }
    }

    private fun publishReady(session: Long) {
        if (session != generation || ready || !microphoneReady || !serviceReady) return
        ready = true
        listener?.onReady()
        listener?.onLevel(0f)
        if (!speechInProgress) waitForNextPhrase(session)
    }

    private fun waitForNextPhrase(session: Long) {
        if (session != generation || stopping || listener == null) return
        clearWait()
        inactivity =
            Runnable {
                if (session == generation && !stopping && listener != null) {
                    listener?.onEndOfSpeech()
                    if (session == generation) stop()
                }
            }.also { handler.postDelayed(it, 12_000) }
    }

    private fun combined(partial: String = "") = (phrases + partial).filter(String::isNotBlank).joinToString(" ")

    private fun finishDraft(session: Long) {
        val text = combined()
        finish(session) {
            if (text.isBlank()) it.onFailure(VoiceInputProblem.NO_MATCH) else it.onResult(text)
        }
    }

    private fun fail(
        session: Long,
        problem: VoiceInputProblem,
    ) = finish(session) { it.onFailure(problem) }

    override fun stop() {
        requireMainThread()
        if (listener == null || stopping) return
        stopping = true
        clearWait()
        // stopListening would discard PCM still queued for delivery. Drain it and signal EOF instead.
        audio?.stop()
    }

    override fun cancel() {
        requireMainThread()
        generation++
        listener = null
        clearWait()
        phrases.clear()
        stopping = false
        ready = false
        serviceReady = false
        microphoneReady = false
        speechInProgress = false
        audio?.cancel()
        audio = null
        val previous = recognizer
        recognizer = null
        try {
            previous?.cancel()
        } catch (_: RuntimeException) {
            // Disconnected services still need local resources released.
        } finally {
            try {
                previous?.destroy()
            } catch (_: RuntimeException) {
                // The detached session cannot publish callbacks after service death.
            }
        }
    }

    private fun clearWait() {
        inactivity?.let(handler::removeCallbacks)
        inactivity = null
    }

    private fun finish(
        session: Long,
        deliver: (ConversationSpeechInput.Listener) -> Unit,
    ) {
        if (session != generation) return
        val recipient = listener ?: return
        cancel()
        deliver(recipient)
    }

    private fun requireMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    private fun transcript(results: Bundle?) =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
}

internal fun recognitionProblem(error: Int): VoiceInputProblem =
    when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputProblem.PERMISSION
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputProblem.NO_MATCH
        SpeechRecognizer.ERROR_AUDIO -> VoiceInputProblem.AUDIO
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceInputProblem.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> VoiceInputProblem.LANGUAGE
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceInputProblem.NETWORK
        else -> VoiceInputProblem.SERVICE
    }
