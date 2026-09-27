package com.monsters.mobimon.speech

import com.monsters.mobimon.feature.auth.VoiceInputProblem

/** Native objects are created, used and closed on the same inference worker. */
internal interface LocalSpeechEngine : AutoCloseable {
    fun detector(): SpeechDetector

    fun transcribe(samples: FloatArray): String
}

internal interface SpeechDetector : AutoCloseable {
    fun accept(samples: FloatArray)

    fun speechDetected(): Boolean

    fun poll(): SpeechRange?

    fun flush()
}

internal data class SpeechRange(
    val start: Int,
    val end: Int,
)

internal class SpeechInputException(
    val problem: VoiceInputProblem,
) : RuntimeException()

internal object SpeechAudio {
    const val SAMPLE_RATE = 16_000
    const val MAX_SAMPLES = SAMPLE_RATE * 60
    const val CONTEXT_SAMPLES = SAMPLE_RATE / 4
    const val INACTIVITY_SAMPLES = SAMPLE_RATE * 12
    const val VAD_WINDOW = 512
}
