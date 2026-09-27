package com.monsters.mobimon.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/** A bounded voice draft can contain multiple recognizer utterances separated by pauses. */
@Singleton
class AndroidConversationSpeechInput
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ConversationSpeechInput {
        private var recognizer: SpeechRecognizer? = null
        private var listener: ConversationSpeechInput.Listener? = null
        private var generation = 0L
        private val handler = Handler(Looper.getMainLooper())
        private val phrases = mutableListOf<String>()
        private var stopping = false
        private var ready = false
        private var restart: Runnable? = null
        private var inactivity: Runnable? = null

        override fun isAvailable(): Boolean =
            try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ||
                    SpeechRecognizer.isRecognitionAvailable(context)
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
            startUtterance(generation)
        }

        private fun startUtterance(session: Long) {
            if (session != generation || stopping || listener == null) return
            try {
                val engine =
                    if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                recognizer = engine
                engine.setRecognitionListener(
                    object : RecognitionListener {
                        private var speechDetected = false
                        private var partial = ""

                        private fun current() = session == generation && recognizer === engine

                        override fun onReadyForSpeech(params: Bundle?) {
                            if (!current()) return
                            if (!ready) {
                                ready = true
                                listener?.onReady()
                                waitForNextPhrase(session)
                            }
                            listener?.onLevel(0f)
                        }

                        override fun onBeginningOfSpeech() {
                            if (!current() || stopping) return
                            speechDetected = true
                            waitForNextPhrase(session)
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            if (current() && rmsdB.isFinite()) {
                                // RMS includes ambient noise; speech detection gates the visual waveform.
                                listener?.onLevel(if (speechDetected) ((rmsdB - 2f) / 10f).coerceIn(0f, 1f) else 0f)
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            if (current()) {
                                speechDetected = false
                                listener?.onLevel(0f)
                            }
                        }

                        override fun onError(error: Int) {
                            if (!current()) return
                            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ) {
                                releaseUtterance()
                                if (stopping) finishDraft(session) else continueListening(session)
                            } else {
                                finish(session) { it.onFailure(recognitionProblem(error)) }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            if (!current()) return
                            val text = transcript(results).trim()
                            if (text.isNotEmpty()) phrases += text
                            releaseUtterance()
                            if (stopping) {
                                finishDraft(session)
                            } else {
                                listener?.onPartial(combined())
                                if (text.isNotEmpty()) waitForNextPhrase(session)
                                continueListening(session)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            if (!current() || stopping) return
                            val text = transcript(partialResults).trim()
                            if (text.isNotEmpty() && text != partial) {
                                speechDetected = true
                                partial = text
                                waitForNextPhrase(session)
                            }
                            listener?.onPartial(combined(text))
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
                    },
                )
            } catch (_: SecurityException) {
                finish(session) { it.onFailure(VoiceInputProblem.PERMISSION) }
            } catch (_: RuntimeException) {
                finish(session) { it.onFailure(VoiceInputProblem.SERVICE) }
            }
        }

        private fun continueListening(session: Long) {
            listener?.onLevel(0f)
            restart = Runnable { startUtterance(session) }.also { handler.postDelayed(it, 300) }
        }

        private fun waitForNextPhrase(session: Long) {
            inactivity?.let(handler::removeCallbacks)
            inactivity =
                Runnable {
                    if (session == generation && !stopping && listener != null) {
                        listener?.onEndOfSpeech()
                        stop()
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

        override fun stop() {
            requireMainThread()
            if (listener == null || stopping) return
            val session = generation
            stopping = true
            clearWaits()
            if (recognizer == null) {
                finishDraft(session)
                return
            }
            try {
                recognizer?.stopListening()
            } catch (_: RuntimeException) {
                finish(session) { it.onFailure(VoiceInputProblem.SERVICE) }
            }
        }

        override fun cancel() {
            requireMainThread()
            generation++
            listener = null
            clearWaits()
            phrases.clear()
            stopping = false
            ready = false
            val previous = recognizer
            recognizer = null
            if (previous != null) {
                try {
                    previous.cancel()
                } catch (_: RuntimeException) {
                    // A disconnected service still needs its local recognizer released.
                } finally {
                    previous.destroy()
                }
            }
        }

        private fun clearWaits() {
            restart?.let(handler::removeCallbacks)
            inactivity?.let(handler::removeCallbacks)
            restart = null
            inactivity = null
        }

        private fun releaseUtterance() {
            val previous = recognizer
            recognizer = null
            previous?.destroy()
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
