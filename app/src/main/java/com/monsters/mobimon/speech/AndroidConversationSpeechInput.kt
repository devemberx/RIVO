package com.monsters.mobimon.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.VoiceInputProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** Continuous foreground capture, with all native inference confined to one worker. */
@Singleton
class AndroidConversationSpeechInput internal constructor(
    private val permissionGranted: () -> Boolean,
    private val modelsAvailable: () -> Boolean,
    private val microphone: () -> MicrophoneSource,
    private val loadEngine: () -> LocalSpeechEngine,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
    private val capture: ExecutorService = Executors.newSingleThreadExecutor(),
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : ConversationSpeechInput {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        permissionGranted = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        },
        modelsAvailable = {
            context.assets.list("stt").orEmpty().toSet().containsAll(
                setOf("model.int8.onnx", "tokens.txt", "silero_vad.onnx"),
            )
        },
        microphone = { MicrophoneAudioSource() },
        loadEngine = { SherpaSpeechEngine(context.assets) },
    )

    private var active: Session? = null

    // Accessed only by worker. Retained briefly between successful drafts, never during cancellation.
    private var engine: LocalSpeechEngine? = null
    private var release: Runnable? = null

    override fun isAvailable(): Boolean =
        try {
            modelsAvailable()
        } catch (_: Exception) {
            false
        }

    override fun start(listener: ConversationSpeechInput.Listener) {
        requireMainThread()
        cancel()
        release?.let(handler::removeCallbacks)
        release = null
        if (!permissionGranted()) {
            listener.onFailure(VoiceInputProblem.PERMISSION)
            return
        }
        if (!isAvailable()) {
            listener.onFailure(VoiceInputProblem.UNAVAILABLE)
            return
        }
        val session = Session(listener)
        active = session
        worker.execute { recognize(session) }
    }

    private fun recognize(session: Session) {
        try {
            if (session.cancelled) return
            val currentEngine = engine ?: loadEngine().also { engine = it }
            if (session.cancelled) return
            SpeechProcessor(currentEngine) { text -> deliver(session) { it.onPartial(text) } }.use { processor ->
                capture.execute { record(session) }
                while (!session.cancelled) {
                    val frame = session.frames.poll(100, TimeUnit.MILLISECONDS)
                    if (frame != null) {
                        val speech = processor.accept(frame)
                        val level =
                            if (speech) {
                                sqrt(
                                    frame.sumOf { (it * it).toDouble() } / frame.size,
                                ).toFloat() * 8
                            } else {
                                0f
                            }
                        deliver(session) { it.onLevel(level.coerceIn(0f, 1f)) }
                        if (processor.samplesSinceSpeech >= SpeechAudio.INACTIVITY_SAMPLES) endCapture(session)
                    }
                    session.problem?.let { throw SpeechInputException(it) }
                    if (session.recordingFinished && session.frames.isEmpty()) break
                }
                if (!session.cancelled) {
                    val text = processor.finish()
                    finish(session) {
                        if (text.isBlank()) it.onFailure(VoiceInputProblem.NO_MATCH) else it.onResult(text)
                    }
                }
            }
        } catch (failure: SpeechInputException) {
            finish(session) { it.onFailure(failure.problem) }
        } catch (_: SecurityException) {
            finish(session) { it.onFailure(VoiceInputProblem.PERMISSION) }
        } catch (_: LinkageError) {
            finish(session) { it.onFailure(VoiceInputProblem.UNAVAILABLE) }
        } catch (_: Exception) {
            finish(session) { it.onFailure(VoiceInputProblem.SERVICE) }
        } finally {
            stopMicrophone(session)
            session.frames.clear()
            if (session.cancelled) releaseEngine()
        }
    }

    private fun record(session: Session) {
        try {
            val source = microphone()
            session.source = source
            if (session.stopping || session.cancelled) return
            source.start()
            if (session.stopping || session.cancelled) return
            deliver(session) { it.onReady() }
            val buffer = ShortArray(1024)
            var samples = 0
            while (!session.stopping && !session.cancelled) {
                val count = source.read(buffer)
                if (count < 0) {
                    if (!session.stopping && !session.cancelled) throw SpeechInputException(VoiceInputProblem.AUDIO)
                    break
                }
                if (count == 0) {
                    if (!session.stopping && !session.cancelled) throw SpeechInputException(VoiceInputProblem.AUDIO)
                    break
                }
                val accepted = minOf(count, SpeechAudio.MAX_SAMPLES - samples)
                val frame = FloatArray(accepted) { buffer[it] / 32768f }
                if (!session.frames.offer(frame)) throw SpeechInputException(VoiceInputProblem.AUDIO)
                samples += accepted
                if (samples == SpeechAudio.MAX_SAMPLES) endCapture(session)
            }
        } catch (failure: SpeechInputException) {
            session.problem = failure.problem
        } catch (_: SecurityException) {
            session.problem = VoiceInputProblem.PERMISSION
        } catch (_: Exception) {
            session.problem = VoiceInputProblem.AUDIO
        } finally {
            try {
                session.source?.close()
            } catch (_: Exception) {
                session.problem = VoiceInputProblem.AUDIO
            }
            session.recordingFinished = true
        }
    }

    private fun endCapture(session: Session) {
        if (!session.stopping) {
            stopMicrophone(session)
            deliver(session) { it.onEndOfSpeech() }
        }
    }

    override fun stop() {
        requireMainThread()
        active?.let(::stopMicrophone)
    }

    override fun cancel() {
        requireMainThread()
        val session = active ?: return
        active = null
        session.cancelled = true
        stopMicrophone(session)
        session.frames.clear()
        worker.execute { releaseEngine() }
    }

    private fun stopMicrophone(session: Session) {
        session.stopping = true
        try {
            session.source?.stop()
        } catch (_: Exception) {
            session.problem = VoiceInputProblem.AUDIO
        }
    }

    private fun deliver(
        session: Session,
        callback: (ConversationSpeechInput.Listener) -> Unit,
    ) {
        handler.post { if (active === session && !session.cancelled) callback(session.listener) }
    }

    private fun finish(
        session: Session,
        callback: (ConversationSpeechInput.Listener) -> Unit,
    ) {
        stopMicrophone(session)
        handler.post {
            if (active === session && !session.cancelled) {
                active = null
                release = Runnable { worker.execute { releaseEngine() } }.also { handler.postDelayed(it, 60_000) }
                callback(session.listener)
            }
        }
    }

    private fun releaseEngine() {
        val previous = engine
        engine = null
        previous?.close()
    }

    private fun requireMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    private class Session(
        val listener: ConversationSpeechInput.Listener,
    ) {
        // At most 16.4 seconds of backlog. Overflow fails instead of silently losing speech.
        val frames = LinkedBlockingQueue<FloatArray>(256)

        @Volatile var cancelled = false

        @Volatile var stopping = false

        @Volatile var recordingFinished = false

        @Volatile var source: MicrophoneSource? = null

        @Volatile var problem: VoiceInputProblem? = null
    }
}
