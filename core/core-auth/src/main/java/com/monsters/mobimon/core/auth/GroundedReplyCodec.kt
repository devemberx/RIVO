package com.monsters.mobimon.core.auth

import android.util.JsonReader
import android.util.JsonToken
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.VehicleFactReference
import java.io.StringReader

internal object GroundedReplyCodec {
    val instruction =
        """
        # Checked response contract
        Return exactly one JSON object: {"version":1,"status":"CONVERSATION|VEHICLE|ANSWERED|NEEDS_CLARIFICATION|NO_EVIDENCE|OUT_OF_SCOPE","text":"...","sourceIds":[],"vehicleRefs":[]}.
        For vehicle claims use status VEHICLE, or ANSWERED when also citing a manual. Each vehicleRefs item is
        {"evidenceId":"ID from this turn's context or tool","fieldId":"exact field ID"}.
        Put {{vehicle:0}}, {{vehicle:1}}, etc. exactly once each in text, in place of every vehicle fact.
        The app renders the actual label, value, unit, availability and simulation source. Do not restate or invent these facts in prose.
        Unavailable fields may be referenced to explain missing evidence; never treat them as false, zero or normal.
        CONVERSATION is ordinary companion chat, without vehicle claims or citations. ANSWERED requires valid manual sourceIds.
        For ANSWERED, put each manual source inline as [ne1-0000] using the actual returned ID beside its claim.
        sourceIds lists those IDs once each in order of first appearance, at most four. The app renders the bibliography.
        sourceIds are manual references only. Vehicle evidenceIds are not manual sources. No vehicle command is available.
        Use ONLY the current turn's evidence; older conversation text is not current vehicle evidence.
        Vehicle.CurrentLocation.Timestamp is the vehicle clock value at capture, not an observation timestamp or phone time.
        Follow the user's language and persona in prose. Keep text concise; no extra JSON fields or markdown fences.
        """.trimIndent()

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
            require(seen == setOf("version", "status", "text", "sourceIds", "vehicleRefs"))
            require(!body.isNullOrBlank() && body!!.none { it.isISOControl() && it != '\n' && it != '\t' })
            GroundedReply(requireNotNull(version), requireNotNull(status), requireNotNull(body), sources, refs)
        } catch (_: Exception) {
            throw ConversationException(ConversationProblem.PROVIDER)
        }

    private fun JsonReader.string(): String {
        require(peek() == JsonToken.STRING)
        return nextString()
    }
}
