package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VssObservationFrameTest {
    @Test fun receiptAndValueChangeHaveIndependentClocks() {
        val recorder = VehicleObservationRecorder(VehicleObservationSource.VSS_ADAPTER, "session")
        val first =
            recorder.receive(
                mapOf("battery" to VehicleValue.Number(30.0), "door" to VehicleValue.Boolean(false)),
                100,
            )
        val second = recorder.receive(mapOf("battery" to VehicleValue.Number(30.0)), 200)
        assertEquals(200L, second.observations.getValue("battery").receivedAtElapsedMillis)
        assertEquals(100L, second.observations.getValue("battery").changedAtElapsedMillis)
        assertEquals(first.observations.getValue("door"), second.observations.getValue("door"))
        val changed = recorder.receive(mapOf("battery" to VehicleValue.Number(29.0)), 300)
        assertEquals(300L, changed.observations.getValue("battery").changedAtElapsedMillis)
        val published = recorder.publish(400)
        assertEquals(changed.observations, published.observations)
        assertEquals(300L, published.observations.getValue("battery").receivedAtElapsedMillis)
        assertEquals(VehicleDeliveryPolicy.Unknown, published.policies.getValue("battery"))
        assertFalse(recorder.disconnect(500).subscription.connected)
    }
}
