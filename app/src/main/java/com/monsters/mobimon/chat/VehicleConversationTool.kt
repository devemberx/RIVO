package com.monsters.mobimon.chat

import com.monsters.mobimon.core.auth.VehicleEvidenceJson
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import org.json.JSONArray
import org.json.JSONObject

/** Read-only snapshot lookup, never a sensor refresh, vehicle command or manual search. */
class VehicleConversationTool(
    private val source: VehicleChatEvidenceSource,
) : ConversationTool {
    override val definition =
        ConversationToolDefinition(
            "get_vehicle_context",
            "Read current app-observed vehicle fields by topic. Use for details absent from basic context. Unavailable fields remain unknown; this tool cannot read unregistered signals or control the vehicle.",
            "topic",
            "overview gives basic facts and category coverage; a specific topic returns all registered fields in that category.",
            32,
            VehicleChatTopic.entries.filter { it != VehicleChatTopic.BASIC }.map { it.name.lowercase() },
        )

    override suspend fun execute(call: ConversationToolCall): ConversationToolResult {
        if (call.name != definition.name ||
            call.argument !in definition.allowedValues
        ) {
            return ConversationToolResult.Unavailable
        }
        val topic = VehicleChatTopic.valueOf(call.argument.uppercase())
        val captured = source.capture(topic)
        val result =
            if (topic == VehicleChatTopic.OVERVIEW) {
                val basic =
                    setOf(
                        VehicleChatFieldCatalog.BATTERY,
                        VehicleChatFieldCatalog.TIME,
                        VehicleChatFieldCatalog.CONDITION,
                    ) +
                        captured.conditionReasons.map { it.signal }
                VehicleChatCapture(
                    captured.evidenceId,
                    captured.capturedAtElapsedMillis,
                    captured.sourceKind,
                    captured.sessionId,
                    captured.fields.filter { it.spec.id in basic },
                    captured.conditionReasons,
                )
            } else {
                captured
            }
        val json = JSONObject(VehicleEvidenceJson.encode(result)).put("topic", call.argument)
        if (topic == VehicleChatTopic.OVERVIEW) {
            val categories = JSONArray()
            captured.fields.groupBy { it.spec.topic }.forEach { (category, fields) ->
                categories.put(
                    JSONObject()
                        .put("topic", category.name.lowercase())
                        .put("availableFields", fields.count { it.value != null })
                        .put("registeredFields", fields.size),
                )
            }
            json.put("categories", categories)
        }
        val content = json.toString()
        if (content.length > 64_000) return ConversationToolResult.Limit
        return ConversationToolResult.Found(content, vehicleCapture = result)
    }
}
