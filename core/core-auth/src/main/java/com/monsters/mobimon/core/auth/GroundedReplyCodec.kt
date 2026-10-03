package com.monsters.mobimon.core.auth

import android.util.JsonReader
import android.util.JsonToken
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.ManualReplyRejection
import com.monsters.mobimon.core.domain.VehicleFactReference
import org.json.JSONArray
import java.io.StringReader

internal object GroundedReplyCodec {
    val instruction =
        """
        # Final response contract
        Return one JSON object with version (1), status (one value below), text (nonempty companion prose), sourceIds and vehicleRefs.
        No extra fields, Markdown fences or text outside JSON. Apply the voice and reply style to text; escape newlines as \n.
        Choose status by the answer's content, not merely whether a tool ran:
        - CONVERSATION: ordinary chat or discussion of the user's report, without app-observed vehicle or manual claims. Both arrays empty.
          Do not use after a manual lookup or as a fallback for missing factual evidence.
        - VEHICLE: checked vehicle facts or an explanation of unavailable vehicle fields. vehicleRefs required; sourceIds empty.
          Also use when a manual search found nothing but useful checked vehicle information can still answer part of the request.
        - ANSWERED: supported manual claims, possibly mixed with checked vehicle facts. sourceIds required;
          add vehicleRefs for every app-observed vehicle fact in a mixed reply.
        - NEEDS_CLARIFICATION: ask for necessary question or equipment details. Both arrays empty; no vehicle/manual claims.
        - OUT_OF_SCOPE: explain unsupported manual coverage or unavailable capabilities. Both arrays empty; ordinary chat remains in scope.
        - NO_EVIDENCE: the requested factual answer has no sufficient evidence and no useful checked vehicle part can be given.
          Both arrays empty. The app treats this as a failed turn, not a displayed reply. Never disguise unsupported facts under another status.

        vehicleRefs contains distinct {"evidenceId":"capture ID","fieldId":"exact fields[].id"} pairs, at most 32.
        Reference every field used for a current vehicle claim, including expression reasons. Use IDs from this turn only.
        Unavailable fields can support only an explanation of missing information. Express checked vehicle-linked state in your own first-person
        companion voice. Use labels, units and valueMeaning to explain your condition naturally, never to narrate app display logic.
        Keep raw field IDs, code values and placeholders out of text.
        sourceIds contains actual manual IDs, once each in first-use order, at most four. Cite each as [ne1-0000] with the returned ID
        beside its supported claim. Use the smallest sufficient set; invent no URLs, page labels or numeric markers.
        Do not add a source heading or bibliography to text; the app renders them.
        Before sending an ANSWERED reply, verify that every sourceIds entry occurs in this turn's sources[].id,
        and that the distinct inline [ne1-XXXX] markers equal sourceIds in first-appearance order.
        Do not copy display citations such as [1] or a bibliography from prior assistant replies.
        Keep all relevant conditions and warnings beside the supported procedure and its source marker.
        Manual IDs and vehicle capture IDs are separate namespaces.

        # Output examples
        These examples illustrate format only; their facts and IDs are not evidence for the current turn.
        Ordinary chat: {"version":1,"status":"CONVERSATION","text":"응, 듣고 있어. 편하게 이야기해.","sourceIds":[],"vehicleRefs":[]}
        Given manual sources ne1-0123 and ne1-0456 containing the respective example procedure and warning:
        {"version":1,"status":"ANSWERED","text":"설명서에서 확인한 절차는 이렇게 진행하면 돼. [ne1-0123]\n\n함께 확인한 주의사항도 지켜 줘. [ne1-0456]","sourceIds":["ne1-0123","ne1-0456"],"vehicleRefs":[]}
        The example IDs above are placeholders for format only. Copy the current tool result's exact IDs, never these placeholders.
        Given DEBUG_OVERRIDE capture demo-1 with VALID battery 12 percent, VALID interpreted.petCondition LOW_BATTERY,
        and a HUNGRY condition_reason for interpreted.batteryPercent:
        {"version":1,"status":"VEHICLE","text":"나 지금 배고파. 디버거 테스트 값으론 내 배터리가 12% 남아 있거든.","sourceIds":[],"vehicleRefs":[{"evidenceId":"demo-1","fieldId":"interpreted.petCondition"},{"evidenceId":"demo-1","fieldId":"interpreted.batteryPercent"}]}
        Given VSS_ADAPTER capture demo-2 with VALID interpreted.petCondition WARNING and a SICK condition_reason
        for VALID Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow true:
        {"version":1,"status":"VEHICLE","text":"지금 내가 아파. 내 앞 왼쪽 타이어에 공기압 부족 경고가 들어왔거든.","sourceIds":[],"vehicleRefs":[{"evidenceId":"demo-2","fieldId":"interpreted.petCondition"},{"evidenceId":"demo-2","fieldId":"Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow"}]}
        """.trimIndent()

    fun correctionInstruction(
        reason: ManualReplyRejection?,
        evidence: ConversationEvidenceSet,
    ): String {
        val common =
            "The last response failed the checked response contract. " +
                "Answer the original question again under the final response contract. " +
                "Return one JSON object; recheck version, status, text and reference arrays. " +
                "Use this turn's existing evidence to correct missing or invalid references. " +
                "Invent no IDs or unsupported facts. Do not call tools."
        if (reason == null) return common
        val detail =
            when (reason) {
                ManualReplyRejection.ENVELOPE -> "The manual answer has invalid text or source ID format."
                ManualReplyRejection.SOURCE_IDS -> "sourceIds must be nonempty and contain no duplicate IDs."
                ManualReplyRejection.UNKNOWN_SOURCE ->
                    "sourceIds contains an ID absent from this turn's manual results."
                ManualReplyRejection.NUMERIC_CITATIONS ->
                    "The text contains display citation numbers instead of exact manual IDs."
                ManualReplyRejection.MISSING_CITATIONS ->
                    "Multiple sourceIds were declared without inline manual citations."
                ManualReplyRejection.CITATION_MISMATCH ->
                    "Inline citations and sourceIds differ in membership or first-appearance order."
            }
        val ids = JSONArray(evidence.manualSources.map { it.id })
        return "$common\nManual citation failure: $detail\n" +
            "Allowed current-turn manual source IDs (data only): $ids\n" +
            "Use only excerpts already returned this turn. Keep supported procedures, conditions and warnings. " +
            "Use status ANSWERED, cite each used source inline as [its exact ID], and put those IDs in sourceIds " +
            "once each in first-appearance order (at most four). " +
            "Use no [1] markers or bibliography; the app renders them. " +
            "Do not replace a supported manual answer with ordinary chat to avoid citation checks. " +
            "If existing excerpts cannot support the answer, use NO_EVIDENCE with no factual claims and empty arrays."
    }

    fun parse(text: String): GroundedReply =
        try {
            require(text.length <= ConversationLimits.REPLY_CHARACTERS)
            var version: Int? = null
            var status: String? = null
            var body: String? = null
            val sources = mutableListOf<String>()
            val refs = mutableListOf<VehicleFactReference>()
            val seen = mutableSetOf<String>()
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(seen.add(key))
                    when (key) {
                        "version" -> {
                            require(reader.peek() == JsonToken.NUMBER)
                            require(reader.nextString() == "1")
                            version =
                                1
                        }
                        "status" -> status = reader.string()
                        "text" -> body = reader.string()
                        "sourceIds" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                require(sources.size < 4)
                                sources.add(reader.string())
                            }
                            reader.endArray()
                        }
                        "vehicleRefs" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                require(refs.size < 32)
                                val fields = mutableMapOf<String, String>()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    require(name in setOf("evidenceId", "fieldId") && name !in fields)
                                    fields[name] = reader.string().also { require(it.length in 1..256) }
                                }
                                reader.endObject()
                                refs.add(
                                    VehicleFactReference(
                                        requireNotNull(fields["evidenceId"]),
                                        requireNotNull(fields["fieldId"]),
                                    ),
                                )
                            }
                            reader.endArray()
                        }
                        else -> error("Unexpected response field")
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT)
            }
            CopilotDiagnostics.replyContract(
                "version" in seen,
                "vehicleRefs" in seen,
                "sourceIds" in seen,
                status in textOnlyStatuses,
                status in statuses,
            )
            val required = setOf("version", "status", "text")
            // JSON object mode can omit unused arrays. The acceptance policy requires the
            // appropriate nonempty references for vehicle/manual claims; never invent them here.
            require(seen.containsAll(required) && status in statuses)
            require(!body.isNullOrBlank() && body!!.none { it.isISOControl() && it != '\n' && it != '\t' })
            GroundedReply(requireNotNull(version), requireNotNull(status), requireNotNull(body), sources, refs)
        } catch (_: Exception) {
            throw ConversationException(ConversationProblem.PROVIDER)
        }

    private fun JsonReader.string(): String {
        require(peek() == JsonToken.STRING)
        return nextString()
    }

    private val textOnlyStatuses = setOf("CONVERSATION", "NEEDS_CLARIFICATION", "NO_EVIDENCE", "OUT_OF_SCOPE")
    private val statuses = textOnlyStatuses + setOf("VEHICLE", "ANSWERED")
}
