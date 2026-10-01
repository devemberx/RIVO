package com.monsters.mobimon.core.domain

class VehicleChatField(
    val spec: VehicleFieldSpec,
    val observation: VehicleObservation?,
    val validity: VehicleFieldValidity,
) {
    val value: VehicleValue? get() = observation?.value.takeIf { validity.quality == SignalQuality.VALID }

    override fun toString() = "VehicleChatField(REDACTED)"
}

class VehicleChatCapture(
    val evidenceId: String,
    val capturedAtElapsedMillis: Long,
    val sourceKind: VehicleObservationSource,
    val sessionId: String,
    fields: List<VehicleChatField>,
) {
    val fields = fields.toList()

    fun field(id: String): VehicleChatField? = fields.firstOrNull { it.spec.id == id }

    override fun toString() = "VehicleChatCapture(REDACTED)"
}

fun interface VehicleChatEvidenceSource {
    suspend fun capture(topic: VehicleChatTopic): VehicleChatCapture
}
