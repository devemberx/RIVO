package com.monsters.mobimon.core.domain

/** Chat-only observation policy. This cannot grant Park, commands, rewards or ownership. */
object VehicleObservationValidity {
    fun evaluate(
        observation: VehicleObservation?,
        policy: VehicleDeliveryPolicy,
        subscription: VehicleSubscriptionState,
        nowElapsedMillis: Long,
    ): VehicleFieldValidity {
        val received = observation?.receivedAtElapsedMillis
        val age = received?.takeIf { it >= 0 && nowElapsedMillis >= it }?.let { nowElapsedMillis - it }
        val basis =
            when (policy) {
                is VehicleDeliveryPolicy.Periodic -> "PERIODIC"
                VehicleDeliveryPolicy.OnChange -> "ON_CHANGE"
                VehicleDeliveryPolicy.Unknown -> "UNKNOWN"
            }

        fun unavailable(reason: String) = VehicleFieldValidity(SignalQuality.UNAVAILABLE, reason, age, basis)
        if (observation == null) return unavailable("NOT_REPORTED")
        if (observation.sessionId.isBlank() ||
            observation.sessionId != subscription.sessionId
        ) {
            return unavailable("SESSION_CHANGED")
        }
        if (!subscription.connected) return unavailable("DISCONNECTED")
        if (!subscription.subscriptionValid ||
            !subscription.initialSyncComplete ||
            subscription.leaseExpiresAtElapsedMillis?.let { nowElapsedMillis >= it } == true
        ) {
            return unavailable("SUBSCRIPTION_INVALID")
        }
        if (observation.value == null) return unavailable(observation.unavailableReason ?: "NOT_REPORTED")
        if (age == null ||
            observation.receiptRevision < 0 ||
            nowElapsedMillis < 0
        ) {
            return unavailable("MISSING_OR_INVALID_PROVENANCE")
        }
        if (observation.changedAtElapsedMillis?.let { it < 0 || it > received!! } ==
            true
        ) {
            return unavailable("INVALID_PROVENANCE")
        }
        if (observation.sourceQuality !=
            SignalQuality.VALID
        ) {
            return VehicleFieldValidity(
                observation.sourceQuality,
                observation.unavailableReason ?: "SOURCE_INVALID",
                age,
                basis,
            )
        }
        return when (policy) {
            VehicleDeliveryPolicy.Unknown -> unavailable("UNVERIFIED_VALIDITY")
            VehicleDeliveryPolicy.OnChange -> VehicleFieldValidity(SignalQuality.VALID, null, age, basis)
            is VehicleDeliveryPolicy.Periodic ->
                if (age <=
                    policy.maxAgeMillis
                ) {
                    VehicleFieldValidity(SignalQuality.VALID, null, age, basis)
                } else {
                    VehicleFieldValidity(SignalQuality.STALE, "EXPIRED", age, basis)
                }
        }
    }

    fun evaluate(
        frame: VehicleEvidenceFrame,
        fieldId: String,
        nowElapsedMillis: Long,
    ): VehicleFieldValidity {
        fun check(
            id: String,
            visited: Set<String>,
        ): VehicleFieldValidity {
            if (id in visited ||
                visited.size >= 32
            ) {
                return VehicleFieldValidity(SignalQuality.UNAVAILABLE, "INVALID_DEPENDENCIES", null, "DERIVED")
            }
            val observation = frame.observations[id]
            val own =
                evaluate(
                    observation,
                    frame.policies[id] ?: VehicleDeliveryPolicy.Unknown,
                    frame.subscription,
                    nowElapsedMillis,
                )
            if (own.quality != SignalQuality.VALID || observation?.derivation != VehicleDerivation.DERIVED) return own
            if (observation.dependencyIds.isEmpty()) {
                return own.copy(
                    quality = SignalQuality.UNAVAILABLE,
                    reason = "MISSING_DEPENDENCIES",
                )
            }
            val invalid =
                observation.dependencyIds.map { check(it, visited + id) }.firstOrNull {
                    it.quality !=
                        SignalQuality.VALID
                }
            return invalid?.copy(reason = "DEPENDENCY_${invalid.reason}", validityBasis = "DERIVED")
                ?: own.copy(validityBasis = "DERIVED")
        }
        return check(fieldId, emptySet())
    }
}
