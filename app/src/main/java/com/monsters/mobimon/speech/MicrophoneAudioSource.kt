package com.monsters.mobimon.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.monsters.mobimon.feature.auth.VoiceInputProblem

internal interface MicrophoneSource : AutoCloseable {
    fun start()

    fun read(buffer: ShortArray): Int

    /** Unblocks a pending read. Safe when called from the cancellation thread. */
    fun stop()
}

internal class MicrophoneAudioSource : MicrophoneSource {
    @Volatile private var recorder: AudioRecord? = null

    @Volatile private var stopped = false

    @SuppressLint("MissingPermission") // The adapter checks permission; revocation is handled as a failure.
    override fun start() {
        if (stopped) return
        val minimum =
            AudioRecord.getMinBufferSize(
                SpeechAudio.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimum <= 0) throw SpeechInputException(VoiceInputProblem.AUDIO)
        val created =
            AudioRecord
                .Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(SpeechAudio.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                ).setBufferSizeInBytes(maxOf(minimum * 2, 8_192))
                .build()
        recorder = created
        if (stopped) return
        if (created.state != AudioRecord.STATE_INITIALIZED) throw SpeechInputException(VoiceInputProblem.AUDIO)
        created.startRecording()
        // Cancellation never waits for AudioRecord construction. Check again after the native call.
        if (stopped) {
            stop()
            return
        }
        if (created.recordingState !=
            AudioRecord.RECORDSTATE_RECORDING
        ) {
            throw SpeechInputException(VoiceInputProblem.AUDIO)
        }
    }

    override fun read(buffer: ShortArray): Int {
        val current = (if (stopped) null else recorder) ?: return 0
        return current.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
    }

    override fun stop() {
        stopped = true
        recorder?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
    }

    override fun close() {
        try {
            stop()
        } finally {
            recorder?.release()
            recorder = null
        }
    }
}
