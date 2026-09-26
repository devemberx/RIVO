package com.monsters.mobimon.feature.auth

/** Foreground microphone input. Calls and listener callbacks run on the main thread. */
interface ConversationSpeechInput {
    fun isAvailable(): Boolean

    fun start(listener: Listener)

    /** Finish the captured speech; a final result or failure follows. This never sends a chat turn. */
    fun stop()

    /** Release the microphone and reject callbacks from the cancelled session. */
    fun cancel()

    interface Listener {
        fun onReady()

        fun onLevel(level: Float)

        fun onPartial(text: String)

        fun onEndOfSpeech()

        fun onResult(text: String)

        fun onFailure(problem: VoiceInputProblem)
    }
}

object UnavailableConversationSpeechInput : ConversationSpeechInput {
    override fun isAvailable() = false

    override fun start(listener: ConversationSpeechInput.Listener) = listener.onFailure(VoiceInputProblem.UNAVAILABLE)

    override fun stop() = Unit

    override fun cancel() = Unit
}

enum class VoiceInputPhase { IDLE, PERMISSION, STARTING, LISTENING, STOPPING, REVIEW }

enum class VoiceInputProblem {
    UNAVAILABLE,
    PERMISSION,
    NO_MATCH,
    AUDIO,
    BUSY,
    LANGUAGE,
    NETWORK,
    SERVICE,
    TIMEOUT,
    TOO_LONG,
}

data class VoiceInputState(
    val available: Boolean = false,
    val phase: VoiceInputPhase = VoiceInputPhase.IDLE,
    val sessionId: Long = 0,
    val elapsedSeconds: Int = 0,
    val levels: List<Float> = emptyList(),
    val partial: String = "",
    val problem: VoiceInputProblem? = null,
) {
    val capturing: Boolean
        get() = phase in setOf(VoiceInputPhase.STARTING, VoiceInputPhase.LISTENING, VoiceInputPhase.STOPPING)
}
