package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Receipt/capture IDs and publication clocks do not represent new reasoning evidence. */
internal object VehicleEvidenceFingerprint {
    fun of(result: ConversationToolResult.Found): String {
        val capture = result.vehicleCapture
        val semantic =
            if (capture == null) {
                result.content
            } else {
                val fields = JSONArray()
                capture.fields.sortedBy { it.spec.id }.forEach { field ->
                    fields.put(
                        JSONArray()
                            .put(field.spec.id)
                            .put(field.value?.canonical() ?: JSONObject.NULL)
                            .put(field.spec.unit ?: JSONObject.NULL)
                            .put(field.validity.quality.name)
                            .put(field.validity.reason ?: JSONObject.NULL)
                            .put(field.validity.validityBasis)
                            .put(field.deliveryMode)
                            .put(field.observation?.derivation?.name ?: JSONObject.NULL)
                            .put(JSONArray(field.observation?.dependencyIds.orEmpty())),
                    )
                }
                JSONArray()
                    .put(capture.sourceKind.name)
                    .put(capture.sessionId)
                    .put(fields)
                    .toString()
            }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(semantic.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
