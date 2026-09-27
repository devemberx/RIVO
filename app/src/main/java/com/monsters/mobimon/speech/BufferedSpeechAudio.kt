package com.monsters.mobimon.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal interface SpeechAudio {
    val source: ParcelFileDescriptor

    fun start(
        onReady: () -> Unit,
        onFailure: () -> Unit,
    )

    fun stop()

    fun cancel()
}

/** One microphone capture feeds one recognition request; only EOF finishes a normal Stop. */
internal class BufferedSpeechAudio : SpeechAudio {
    private val pipe = ParcelFileDescriptor.createPipe()
    override val source: ParcelFileDescriptor = pipe[0]
    private val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

    // Exceeds the 20s startup + 60s recording bound; overflow is reported, never silently dropped.
    private val buffer = SpeechAudioBuffer(SAMPLE_RATE * 2 * 90)
    private val cancelled = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val recorder = AtomicReference<AudioRecord?>()
    private val handler = Handler(Looper.getMainLooper())

    override fun start(
        onReady: () -> Unit,
        onFailure: () -> Unit,
    ) {
        fun fail() {
            if (!cancelled.get() && failed.compareAndSet(false, true)) {
                handler.post { if (!cancelled.get()) onFailure() }
            }
        }
        thread(name = "speech-pcm-delivery") {
            try {
                output.use { stream ->
                    while (!cancelled.get()) {
                        val chunk = buffer.read() ?: break
                        try {
                            stream.write(chunk)
                        } finally {
                            chunk.fill(0)
                        }
                    }
                }
            } catch (_: IOException) {
                fail()
            } catch (_: RuntimeException) {
                fail()
            }
        }
        thread(name = "speech-microphone") {
            var microphone: AudioRecord? = null
            try {
                if (cancelled.get() || stopped.get()) return@thread
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val minimum =
                    AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                check(minimum > 0)
                microphone =
                    AudioRecord
                        .Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(
                            AudioFormat
                                .Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build(),
                        ).setBufferSizeInBytes(maxOf(minimum, 6400))
                        .setPrivacySensitive(true)
                        .build()
                recorder.set(microphone)
                if (cancelled.get() || stopped.get()) return@thread
                microphone.startRecording()
                check(microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                handler.post { if (!cancelled.get() && !stopped.get()) onReady() }
                captureSpeechPcm(
                    cancelled = cancelled::get,
                    stopping = stopped::get,
                    read = { chunk, draining ->
                        microphone.read(
                            chunk,
                            0,
                            chunk.size,
                            if (draining) AudioRecord.READ_NON_BLOCKING else AudioRecord.READ_BLOCKING,
                        )
                    },
                    append = buffer::append,
                    nativeBufferBytes = microphone.bufferSizeInFrames * 2,
                    onFailure = ::fail,
                )
            } catch (_: SecurityException) {
                // Permission can be revoked after the entry-point check, before capture starts.
                fail()
            } catch (_: RuntimeException) {
                fail()
            } finally {
                recorder.set(null)
                try {
                    microphone?.release()
                } finally {
                    buffer.finish()
                }
            }
        }
    }

    override fun stop() {
        stopped.set(true)
    }

    override fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        stop()
        try {
            recorder.get()?.stop()
        } catch (_: RuntimeException) {
            // Cancellation interrupts blocking reads; a concurrent release is harmless.
        }
        buffer.cancel()
        try {
            source.close()
        } catch (_: IOException) {
            // Both ends are independently owned and must be released.
        }
        try {
            output.close()
        } catch (_: IOException) {
            // The delivery thread may already have closed the descriptor.
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
