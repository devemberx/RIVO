package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import kotlinx.coroutines.flow.StateFlow

/** Opt-in adapter contract: values and provenance belong to this exact source revision. */
interface ObservedVssRawVehicleSource : VssRawVehicleSource {
    val observationFrames: StateFlow<VssObservationFrame?>
}

data class VssObservationFrame(
    val raw: VssRawVehicleState?,
    val evidence: VehicleEvidenceFrame,
)
