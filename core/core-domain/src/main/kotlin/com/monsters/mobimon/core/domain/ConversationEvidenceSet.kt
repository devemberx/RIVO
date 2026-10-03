package com.monsters.mobimon.core.domain

/** Created per reply; neither references nor tool protocol are conversation history. */
class ConversationEvidenceSet(
    initial: VehicleChatCapture? = null,
) {
    private val captures = linkedMapOf<String, VehicleChatCapture>()
    private val manual = linkedMapOf<String, ConversationEvidence>()
    val vehicle: List<VehicleChatCapture> get() = captures.values.toList()
    val manualSources: List<ConversationEvidence> get() = manual.values.toList()
    var manualLookupAttempted: Boolean = false
        private set

    init {
        initial?.let { captures[it.evidenceId] = it }
    }

    fun capture(id: String): VehicleChatCapture? = captures[id]

    fun add(result: ConversationToolResult.Found) {
        result.vehicleCapture?.let {
            require(it.evidenceId !in captures)
            captures[it.evidenceId] = it
        }
        result.evidence.forEach {
            require(manual[it.id]?.let { previous -> previous != it } != true)
            manual[it.id] = it
        }
        manualLookupAttempted = manualLookupAttempted || result.manualLookupAttempted || result.evidence.isNotEmpty()
    }

    override fun toString() = "ConversationEvidenceSet(REDACTED)"
}
