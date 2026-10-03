package com.monsters.mobimon.core.domain

/** Interpreter inputs. An explicit debugger override is a direct value instead. */
object VehicleChatDependencies {
    private const val BATTERY = "Vehicle.Powertrain.TractionBattery."
    private const val NAVIGATION = "Vehicle.Cabin.Infotainment.Navigation.DestinationSet."
    private val tires = listOf("Row1.Wheel.Left", "Row1.Wheel.Right", "Row2.Wheel.Left", "Row2.Wheel.Right")
    private val dependencies =
        mapOf(
            "batteryPercent" to listOf("${BATTERY}StateOfCharge.Displayed"),
            "washerFluidLevel" to listOf("Vehicle.Body.Windshield.Front.WasherFluid.Level"),
            "tirePressureStatus" to tires.map { "Vehicle.Chassis.Axle.$it.Tire.IsPressureLow" },
            "speed" to listOf("Vehicle.Speed"),
            "gear" to listOf("Vehicle.Powertrain.Transmission.SelectedGear"),
            "isMoving" to listOf("Vehicle.IsMoving", "Vehicle.Speed"),
            "drivingState" to listOf("interpreted.isMoving", "interpreted.speed", "interpreted.gear"),
            "isEngineOn" to listOf("Vehicle.Powertrain.CombustionEngine.IsRunning"),
            "isCharging" to
                listOf(
                    "${BATTERY}Charging.IsCharging",
                    "${BATTERY}Charging.AveragePower",
                    "${BATTERY}Charging.ChargingPort.AnyPosition.IsChargingCableConnected",
                ),
            "outsideTemperature" to listOf("Vehicle.Exterior.AirTemperature"),
            "isRaining" to listOf("Vehicle.Body.Raindetection.Intensity"),
            "attentionLevel" to listOf("Vehicle.Driver.DistractionLevel"),
            "isDistracted" to listOf("Vehicle.Driver.DistractionLevel"),
            "isDrowsy" to listOf("Vehicle.Driver.FatigueLevel", "Vehicle.ADAS.DMS.IsWarning"),
            "isEmergencyBraking" to listOf("Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected"),
            "distanceToFrontVehicle" to listOf("Vehicle.ADAS.ObstacleDetection.Front.Center.Distance"),
            "isFuelLevelLow" to listOf("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow"),
            "isEngineWarning" to listOf("Vehicle.Diagnostics.DTCCount", "Vehicle.OBD.Status.IsMILOn"),
            "distanceToDestination" to
                listOf(
                    "Vehicle.CurrentLocation.Latitude",
                    "Vehicle.CurrentLocation.Longitude",
                    "${NAVIGATION}Latitude",
                    "${NAVIGATION}Longitude",
                ),
            "isNavigating" to listOf("interpreted.distanceToDestination"),
            "timeOfDay" to listOf(VehicleChatFieldCatalog.TIME),
        )

    fun forField(name: String): List<String> = dependencies[name.removePrefix("interpreted.")].orEmpty()
}
