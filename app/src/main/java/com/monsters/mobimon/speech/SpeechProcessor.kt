package com.monsters.mobimon.speech

import com.monsters.mobimon.feature.auth.VoiceInputProblem

/** One bounded draft; original PCM supplies context omitted by VAD segment boundaries. */
internal class SpeechProcessor(
    private val engine: LocalSpeechEngine,
    private val partial: (String) -> Unit = {},
) : AutoCloseable {
    private val detector = engine.detector()
    private val audio = FloatArray(SpeechAudio.MAX_SAMPLES)
    private val pending = ArrayDeque<SpeechRange>()
    private val phrases = mutableListOf<String>()
    private var size = 0
    private var lastSpeech = 0
    private var decodedUntil = 0
    private var finished = false

    val samplesSinceSpeech: Int get() = size - lastSpeech

    fun accept(samples: FloatArray): Boolean {
        check(!finished)
        if (samples.size > audio.size - size) throw SpeechInputException(VoiceInputProblem.TOO_LONG)
        samples.copyInto(audio, size)
        size += samples.size
        detector.accept(samples)
        val speech = detector.speechDetected()
        if (speech) lastSpeech = size
        collectSegments()
        decodeReady(final = false)
        return speech
    }

    fun finish(): String {
        if (!finished) {
            // VAD uses full windows; padding is never added to the audio passed to ASR.
            val remainder = size % SpeechAudio.VAD_WINDOW
            if (remainder != 0) detector.accept(FloatArray(SpeechAudio.VAD_WINDOW - remainder))
            detector.flush()
            collectSegments()
            decodeReady(final = true)
            finished = true
        }
        return phrases.joinToString(" ")
    }

    private fun collectSegments() {
        while (true) {
            val range = detector.poll() ?: break
            pending.addLast(range)
            lastSpeech = maxOf(lastSpeech, range.end.coerceAtMost(size))
        }
    }

    private fun decodeReady(final: Boolean) {
        while (pending.isNotEmpty()) {
            val range = pending.first()
            if (!final && range.end + SpeechAudio.CONTEXT_SAMPLES > size) return
            pending.removeFirst()
            val start = maxOf(0, decodedUntil, range.start - SpeechAudio.CONTEXT_SAMPLES)
            val end = minOf(size, range.end + SpeechAudio.CONTEXT_SAMPLES)
            if (end <= start) continue
            val text = engine.transcribe(audio.copyOfRange(start, end)).trim()
            decodedUntil = end
            if (text.isNotBlank()) {
                phrases += text
                partial(phrases.joinToString(" "))
            }
        }
    }

    override fun close() {
        detector.close()
        audio.fill(0f)
        pending.clear()
        phrases.clear()
    }
}
