package com.monsters.mobimon.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

/** One recognizer per session; cancellation rejects delivery into a later session. */
@Singleton
class AndroidConversationSpeechInput
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ConversationSpeechInput {
        private var recognizer: SpeechRecognizer? = null
        private var listener: ConversationSpeechInput.Listener? = null
        private var generation = 0L

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
            val session = generation
            this.listener = listener
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
                        private fun current() = session == generation && recognizer === engine

                        override fun onReadyForSpeech(params: Bundle?) {
                            if (current()) listener.onReady()
                        }

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) {
                            if (current() && rmsdB.isFinite()) listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            if (current()) listener.onEndOfSpeech()
                        }

                        override fun onError(error: Int) {
                            if (current()) finish(session) { it.onFailure(recognitionProblem(error)) }
                        }

                        override fun onResults(results: Bundle?) {
                            if (current()) finish(session) { it.onResult(transcript(results)) }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            if (current()) listener.onPartial(transcript(partialResults))
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

        override fun stop() {
            requireMainThread()
            val session = generation
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

        private fun finish(
            session: Long,
            deliver: (ConversationSpeechInput.Listener) -> Unit,
        ) {
            if (session != generation) return
            val recipient = listener ?: return
            generation++
            listener = null
            val previous = recognizer
            recognizer = null
            previous?.destroy()
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
