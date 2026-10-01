package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import org.json.JSONArray
import org.json.JSONObject

/** Checks provenance and renders referenced facts, not arbitrary prose semantics. */
class GroundedConversationReplyPolicy(
    private val source: VehicleChatEvidenceSource,
    private val manual: ConversationReplyPolicy? = null,
) : ConversationGroundedReplyPolicy {
    override suspend fun checkCurrent(evidence: ConversationEvidenceSet): Boolean =
        sameSession(evidence, source.capture(VehicleChatTopic.BASIC))

    override suspend fun accept(
        reply: GroundedReply,
        evidence: ConversationEvidenceSet,
    ): ConversationResult<String> {
        if (reply.version != 1 || reply.text.isBlank() || reply.text.length > 12_000) return failure()
        val refs = reply.vehicleRefs
        if (refs.size > 32 || refs.distinct().size != refs.size) return failure()
        val markers = Regex("\\{\\{vehicle:([0-9]+)}}").findAll(reply.text).toList()
        if (markers.map { it.groupValues[1] }.sorted() != refs.indices.map(Int::toString).sorted()) return failure()
        if (reply.text.replace(Regex("\\{\\{vehicle:([0-9]+)}}"), "").contains("{{vehicle")) return failure()
        if (reply.sourceIds.distinct().size != reply.sourceIds.size || reply.sourceIds.size > 4) return failure()
        if (reply.status != "ANSWERED" &&
            (
                reply.sourceIds.isNotEmpty() ||
                    Regex("\\[(?:ne1-|[0-9])[^\\]]*]").containsMatchIn(reply.text)
            )
        ) {
            return failure()
        }
        when (reply.status) {
            "VEHICLE" -> if (refs.isEmpty()) return failure()
            "ANSWERED" -> if (reply.sourceIds.isEmpty() || manual == null) return failure()
            "CONVERSATION", "NEEDS_CLARIFICATION", "OUT_OF_SCOPE", "NO_EVIDENCE" -> {
                if (refs.isNotEmpty()) return failure()
            }
            else -> return failure()
        }
        val current = source.capture(VehicleChatTopic.OVERVIEW)
        if (!sameSession(evidence, current)) return ConversationResult.Failure(ConversationProblem.RESTRICTED)
        val facts =
            refs.map { reference ->
                val captured = evidence.capture(reference.evidenceId) ?: return failure()
                val original = captured.field(reference.fieldId) ?: return failure()
                val latest = current.field(reference.fieldId) ?: return failure()
                if (!compatible(
                        original,
                        latest,
                        captured,
                        current,
                    )
                ) {
                    return ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                }
                VehicleFactRenderer.render(original, captured.sourceKind)
            }
        val rendered = Regex("\\{\\{vehicle:([0-9]+)}}").replace(reply.text) { facts[it.groupValues[1].toInt()] }
        if (reply.status == "NO_EVIDENCE") return ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
        if (reply.status == "ANSWERED") {
            return manual!!.accept(
                JSONObject()
                    .put(
                        "status",
                        "ANSWERED",
                    ).put("text", rendered)
                    .put("sourceIds", JSONArray(reply.sourceIds))
                    .toString(),
                ConversationToolResult.Found("", evidence.manualSources),
            )
        }
        return ConversationResult.Success(rendered)
    }

    private fun sameSession(
        evidence: ConversationEvidenceSet,
        current: VehicleChatCapture,
    ): Boolean = evidence.vehicle.all { it.sourceKind == current.sourceKind && it.sessionId == current.sessionId }

    private fun compatible(
        old: VehicleChatField,
        latest: VehicleChatField,
        capture: VehicleChatCapture,
        current: VehicleChatCapture,
    ): Boolean {
        if (current.capturedAtElapsedMillis < capture.capturedAtElapsedMillis ||
            old.validUntilElapsedMillis?.let { current.capturedAtElapsedMillis > it } == true ||
            old.spec != latest.spec ||
            old.validity.quality != latest.validity.quality ||
            old.validity.reason != latest.validity.reason ||
            old.deliveryMode != latest.deliveryMode ||
            old.observation?.derivation != latest.observation?.derivation ||
            old.observation?.dependencyIds != latest.observation?.dependencyIds
        ) {
            return false
        }
        // An advancing vehicle clock is explicitly an as-of fact, not evidence receipt time.
        if (old.spec.id == VehicleChatFieldCatalog.TIME && old.value != null) return latest.value != null
        return old.value == latest.value
    }

    private fun failure() = ConversationResult.Failure(ConversationProblem.PROVIDER)
}
