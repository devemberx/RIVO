package com.monsters.mobimon.core.domain

/** Read-only local tools accept one bounded string; wire schemas belong to the provider adapter. */
data class ConversationToolDefinition(
    val name: String,
    val description: String,
    val parameter: String,
    val parameterDescription: String,
    val maxArgumentCharacters: Int = 400,
    val allowedValues: List<String> = emptyList(),
) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9_]{0,63}")))
        require(parameter.matches(Regex("[a-z][a-z0-9_]{0,63}")))
        require(description.isNotBlank() && description.length <= 2000)
        require(parameterDescription.isNotBlank() && parameterDescription.length <= 1000)
        require(maxArgumentCharacters in 1..2000)
        require(allowedValues.size <= 32 && allowedValues.distinct().size == allowedValues.size)
        require(
            allowedValues.all { it.isNotBlank() && it.length <= maxArgumentCharacters && it.none(Char::isISOControl) },
        )
    }
}

class ConversationToolCall(
    val id: String,
    val name: String,
    val argument: String,
) {
    override fun toString() = "ConversationToolCall(REDACTED)"
}

data class ConversationEvidence(
    val id: String,
    val citation: String,
)

sealed interface ConversationToolResult {
    class Found(
        val content: String,
        val evidence: List<ConversationEvidence> = emptyList(),
        val vehicleCapture: VehicleChatCapture? = null,
        val manualLookupAttempted: Boolean = false,
    ) : ConversationToolResult {
        override fun toString() = "ConversationToolResult.Found(REDACTED)"
    }

    data object NoEvidence : ConversationToolResult

    data object Unavailable : ConversationToolResult

    data object Limit : ConversationToolResult
}

interface ConversationTool {
    val definition: ConversationToolDefinition

    /** Implementations must cooperate with coroutine cancellation and avoid blocking the caller thread. */
    suspend fun execute(call: ConversationToolCall): ConversationToolResult
}

fun interface ConversationReplyPolicy {
    /** Evidence belongs only to this turn. Rejected replies must never enter conversation storage. */
    fun accept(
        text: String,
        result: ConversationToolResult.Found?,
    ): ConversationResult<String>

    /** Fixed metadata for a rejected answer; never contains provider prose or source IDs. */
    fun rejectionReason(
        text: String,
        result: ConversationToolResult.Found?,
    ): ManualReplyRejection? = null
}

enum class ManualReplyRejection {
    ENVELOPE,
    SOURCE_IDS,
    UNKNOWN_SOURCE,
    NUMERIC_CITATIONS,
    MISSING_CITATIONS,
    CITATION_MISMATCH,
}

data class ConversationToolUsage(
    val modelRequests: Int,
    val toolExecutions: Int,
    val estimatedPromptTokens: Long,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val elapsedMillis: Long,
)

class ConversationTools(
    tools: List<ConversationTool>,
    val instruction: String = "",
    val replyPolicy: ConversationReplyPolicy = ConversationReplyPolicy { text, _ -> ConversationResult.Success(text) },
    val onUsage: (ConversationToolUsage) -> Unit = {},
    val groundedReplyPolicy: ConversationGroundedReplyPolicy? = null,
    private val selectTools: ((List<ConversationTurn>) -> Set<String>?)? = null,
) {
    val tools: List<ConversationTool> = tools.toList()

    init {
        require(tools.size <= 4 && tools.map { it.definition.name }.distinct().size == tools.size)
        require(instruction.length <= 8000)
    }

    /** Null preserves the registry; a selection can only remove registered tools for this turn. */
    fun forTurn(messages: List<ConversationTurn>): ConversationTools {
        val selected = selectTools?.invoke(messages.toList()) ?: return this
        require(selected.all { name -> tools.any { it.definition.name == name } })
        return ConversationTools(
            tools.filter { it.definition.name in selected },
            instruction,
            replyPolicy,
            onUsage,
            groundedReplyPolicy,
        )
    }

    companion object {
        val None = ConversationTools(emptyList())
    }
}
