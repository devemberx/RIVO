package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.ManualReplyRejection
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import org.json.JSONArray
import org.json.JSONObject

/** Fixed rejection metadata; never includes dialogue, vehicle values or evidence IDs. */
enum class GroundedReplyRejection {
    ENVELOPE,
    MANUAL_LOOKUP_DISCARDED,
    VEHICLE_REFERENCES,
    VEHICLE_MARKERS,
    MANUAL_REFERENCES,
    MISSING_VEHICLE_EVIDENCE,
    MANUAL_ANSWER,
}

/** Revalidates referenced evidence and preserves AI-authored prose; does not prove its semantics. */
class GroundedConversationReplyPolicy(
    private val source: VehicleChatEvidenceSource,
    private val manual: ConversationReplyPolicy? = null,
    private val onRejected: (GroundedReplyRejection) -> Unit = {},
) : ConversationGroundedReplyPolicy {
    override suspend fun checkCurrent(evidence: ConversationEvidenceSet): Boolean =
        sameSession(evidence, source.capture(VehicleChatTopic.BASIC))

    override fun rejectionReason(
        reply: GroundedReply,
        evidence: ConversationEvidenceSet,
    ): ManualReplyRejection? =
        if (reply.status == "ANSWERED") {
            manual?.rejectionReason(manualReply(reply), manualEvidence(evidence))
        } else {
            null
        }

    override suspend fun accept(
        reply: GroundedReply,
        evidence: ConversationEvidenceSet,
    ): ConversationResult<String> {
        if (reply.version != 1 || reply.text.isBlank() || reply.text.length > 12_000) {
            return failure(GroundedReplyRejection.ENVELOPE)
        }
        if (reply.status == "CONVERSATION" && evidence.manualLookupAttempted) {
            return failure(GroundedReplyRejection.MANUAL_LOOKUP_DISCARDED)
        }
        val refs = reply.vehicleRefs
        if (refs.size > 32 ||
            refs.distinct().size != refs.size
        ) {
            return failure(GroundedReplyRejection.VEHICLE_REFERENCES)
        }
        // Obsolete protocol tokens must be corrected by the model, never rendered as canned prose.
        if (reply.text.contains("{{vehicle")) {
            return failure(GroundedReplyRejection.VEHICLE_MARKERS)
        }
        if (reply.sourceIds.distinct().size != reply.sourceIds.size || reply.sourceIds.size > 4) {
            return failure(GroundedReplyRejection.MANUAL_REFERENCES)
        }
        if (reply.status != "ANSWERED" &&
            (
                reply.sourceIds.isNotEmpty() ||
                    sourceReference.containsMatchIn(reply.text)
            )
        ) {
            return failure(GroundedReplyRejection.MANUAL_REFERENCES)
        }
        when (reply.status) {
            "VEHICLE" -> if (refs.isEmpty()) return failure(GroundedReplyRejection.VEHICLE_REFERENCES)
            "ANSWERED" ->
                if (reply.sourceIds.isEmpty() ||
                    manual == null
                ) {
                    return failure(GroundedReplyRejection.MANUAL_REFERENCES)
                }
            "CONVERSATION", "NEEDS_CLARIFICATION", "OUT_OF_SCOPE", "NO_EVIDENCE" -> {
                if (refs.isNotEmpty()) return failure(GroundedReplyRejection.VEHICLE_REFERENCES)
            }
            else -> return failure(GroundedReplyRejection.ENVELOPE)
        }
        val current = source.capture(VehicleChatTopic.OVERVIEW)
        if (!sameSession(evidence, current)) return ConversationResult.Failure(ConversationProblem.RESTRICTED)
        refs.forEach { reference ->
            val captured =
                evidence.capture(reference.evidenceId)
                    ?: return failure(GroundedReplyRejection.MISSING_VEHICLE_EVIDENCE)
            val original =
                captured.field(reference.fieldId) ?: return failure(GroundedReplyRejection.MISSING_VEHICLE_EVIDENCE)
            val latest =
                current.field(reference.fieldId) ?: return failure(GroundedReplyRejection.MISSING_VEHICLE_EVIDENCE)
            if (!compatible(
                    original,
                    latest,
                    captured,
                    current,
                )
            ) {
                return ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
            }
        }
        if (reply.status == "NO_EVIDENCE") return ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
        if (reply.status == "ANSWERED") {
            val result =
                manual!!.accept(
                    manualReply(reply),
                    manualEvidence(evidence),
                )
            if (result is ConversationResult.Failure && result.problem == ConversationProblem.PROVIDER) {
                onRejected(GroundedReplyRejection.MANUAL_ANSWER)
            }
            return result
        }
        return ConversationResult.Success(reply.text)
    }

    private fun manualReply(reply: GroundedReply): String =
        JSONObject()
            .put("status", "ANSWERED")
            .put("text", reply.text)
            .put("sourceIds", JSONArray(reply.sourceIds))
            .toString()

    private fun manualEvidence(evidence: ConversationEvidenceSet): ConversationToolResult.Found =
        ConversationToolResult.Found("", evidence.manualSources, manualLookupAttempted = evidence.manualLookupAttempted)

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
        if (old.spec.id == VehicleChatFieldCatalog.TIME && old.value != null && old.value != latest.value) {
            return latest.value != null &&
                old.validUntilElapsedMillis?.let { current.capturedAtElapsedMillis <= it } == true
        }
        return old.value == latest.value
    }

    private fun failure(reason: GroundedReplyRejection): ConversationResult.Failure {
        onRejected(reason)
        return ConversationResult.Failure(ConversationProblem.PROVIDER)
    }

    private companion object {
        // Android's ICU regex engine requires literal closing delimiters to be escaped.
        val sourceReference = Regex("\\[(?:ne1-|[0-9])[^\\]]*\\]")
    }
}
