package com.monsters.mobimon.core.domain

/** Signal identity is independent of origin; derivation is independent of simulation. */
enum class VehicleObservationSource { VSS_ADAPTER, DEBUG_OVERRIDE, FALLBACK }

enum class VehicleDerivation { DIRECT, DERIVED }

sealed interface VehicleValue {
    data class Boolean(
        val value: kotlin.Boolean,
    ) : VehicleValue

    data class Number(
        val value: Double,
    ) : VehicleValue {
        init {
            require(value.isFinite())
        }
    }

    data class Text(
        val value: String,
    ) : VehicleValue

    fun canonical(): String =
        when (this) {
            is Boolean -> value.toString()
            is Number -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            is Text -> value
        }

    companion object {
        fun parse(
            type: CardVssType,
            value: String?,
        ): VehicleValue? =
            when (type) {
                CardVssType.BOOLEAN ->
                    when (value) {
                        "true" -> Boolean(true)
                        "false" -> Boolean(false)
                        else -> null
                    }
                CardVssType.NUMBER -> value?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::Number)
                CardVssType.TEXT -> value?.takeIf { it.length <= 2000 && it.none(Char::isISOControl) }?.let(::Text)
            }
    }
}

data class VehicleObservation(
    val fieldId: String,
    val value: VehicleValue?,
    val sourceKind: VehicleObservationSource,
    val sessionId: String,
    val receiptRevision: Long,
    val receivedAtElapsedMillis: Long?,
    val changedAtElapsedMillis: Long?,
    val sourceTimestamp: String? = null,
    val sourceQuality: SignalQuality = SignalQuality.VALID,
    val unavailableReason: String? = null,
    val derivation: VehicleDerivation = VehicleDerivation.DIRECT,
    val dependencyIds: List<String> = emptyList(),
) {
    override fun toString() = "VehicleObservation(REDACTED)"
}

sealed interface VehicleDeliveryPolicy {
    data class Periodic(
        val maxAgeMillis: Long,
    ) : VehicleDeliveryPolicy {
        init {
            require(maxAgeMillis >= 0)
        }
    }

    data object OnChange : VehicleDeliveryPolicy

    data object Unknown : VehicleDeliveryPolicy
}

data class VehicleSubscriptionState(
    val sessionId: String,
    val initialSyncComplete: Boolean,
    val connected: Boolean,
    val subscriptionValid: Boolean,
    val leaseExpiresAtElapsedMillis: Long? = null,
)

data class VehicleEvidenceFrame(
    val publicationRevision: Long,
    val publishedAtElapsedMillis: Long,
    val observations: Map<String, VehicleObservation>,
    val policies: Map<String, VehicleDeliveryPolicy>,
    val subscription: VehicleSubscriptionState,
) {
    override fun toString() = "VehicleEvidenceFrame(REDACTED)"
}

data class VehicleFieldValidity(
    val quality: SignalQuality,
    val reason: String?,
    val receiptAgeMillis: Long?,
    val validityBasis: String,
)
