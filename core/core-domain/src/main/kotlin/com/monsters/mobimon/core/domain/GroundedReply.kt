package com.monsters.mobimon.core.domain

class GroundedReply(
    val version: Int,
    val status: String,
    val text: String,
    val sourceIds: List<String>,
    val vehicleRefs: List<VehicleFactReference>,
) {
    override fun toString() = "GroundedReply(REDACTED)"
}

data class VehicleFactReference(
    val evidenceId: String,
    val fieldId: String,
)

fun interface ConversationGroundedReplyPolicy {
    suspend fun accept(
        reply: GroundedReply,
        evidence: ConversationEvidenceSet,
    ): ConversationResult<String>

    suspend fun checkCurrent(evidence: ConversationEvidenceSet): Boolean = true
}
