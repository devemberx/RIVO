package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleValue
import org.json.JSONArray
import org.json.JSONObject

/** Shared bounded wire representation for the basic context and local VSS tool. */
object VehicleEvidenceJson {
    fun encode(capture: VehicleChatCapture): String = json(capture).toString()

    internal fun json(capture: VehicleChatCapture): JSONObject =
        JSONObject()
            .put("evidenceId", capture.evidenceId)
            .put("source", capture.sourceKind.name)
            .put(
                "fields",
                JSONArray().apply {
                    capture.fields.forEach { field ->
                        val value =
                            when (val value = field.value) {
                                is VehicleValue.Boolean -> value.value
                                is VehicleValue.Number -> value.value
                                is VehicleValue.Text -> value.value
                                null -> JSONObject.NULL
                            }
                        put(
                            JSONObject()
                                .put("id", field.spec.id)
                                .put("value", value)
                                .put("unit", field.spec.unit ?: JSONObject.NULL)
                                .put("quality", field.validity.quality.name)
                                .put("unavailableReason", field.validity.reason ?: JSONObject.NULL)
                                .put("receiptAgeMs", field.validity.receiptAgeMillis ?: JSONObject.NULL)
                                .put("validityBasis", field.validity.validityBasis)
                                .put("deliveryMode", field.deliveryMode)
                                .put("derivation", field.observation?.derivation?.name ?: JSONObject.NULL),
                        )
                    }
                },
            ).put(
                "vss_time_value",
                capture.field(VehicleChatFieldCatalog.TIME)?.value?.canonical() ?: JSONObject.NULL,
            ).put(
                "condition_reasons",
                JSONArray().apply {
                    capture.conditionReasons.forEach { reason ->
                        put(
                            JSONObject()
                                .put("concern", reason.concern)
                                .put("signal", reason.signal)
                                .put("description", reason.description.take(200)),
                        )
                    }
                },
            )
}
