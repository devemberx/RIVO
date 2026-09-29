package com.monsters.mobimon.runtime

import com.monsters.mobimon.core.domain.VehicleRepository

/** Owns the single vehicle observation across every feature in this process. */
class CompanionRuntime(
    private val vehicleRepository: VehicleRepository,
    private val appUse: AppUseLifecycle,
) {
    private var appRunning = false
    private var overlayRunning = false
    private var vehicleRunning = false

    @Synchronized
    fun start() {
        if (!appRunning) {
            appUse.start()
            appRunning = true
            updateVehicleObservation()
        }
    }

    @Synchronized
    fun stop() {
        if (appRunning) {
            appUse.stop()
            appRunning = false
            updateVehicleObservation()
        }
    }

    @Synchronized
    fun startOverlay() {
        if (!overlayRunning) {
            overlayRunning = true
            updateVehicleObservation()
        }
    }

    @Synchronized
    fun stopOverlay() {
        if (overlayRunning) {
            overlayRunning = false
            updateVehicleObservation()
        }
    }

    private fun updateVehicleObservation() {
        val shouldRun = appRunning || overlayRunning
        if (shouldRun && !vehicleRunning) {
            vehicleRepository.start()
            vehicleRunning = true
        } else if (!shouldRun && vehicleRunning) {
            vehicleRepository.stop()
            vehicleRunning = false
        }
    }
}
