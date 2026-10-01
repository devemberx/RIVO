package com.monsters.mobimon.core.domain

enum class VehicleChatTopic { BASIC, OVERVIEW, BATTERY, CHARGING, TIRES, BRAKES, BODY, ENVIRONMENT, DRIVER, SERVICE, MOTION, NAVIGATION, TIME, CONDITION }

data class VehicleFieldSpec(
    val id: String,
    val topic: VehicleChatTopic,
    val valueType: CardVssType,
    val unit: String? = null,
    val sourcePaths: List<String> = listOf(id),
    val dependencyIds: List<String> = emptyList(),
)

/** Only metadata is shared with the demo catalog; fixture values are never read here. */
object VehicleChatFieldCatalog {
    const val BATTERY = "interpreted.batteryPercent"
    const val TIME = "Vehicle.CurrentLocation.Timestamp"
    const val CONDITION = "interpreted.petCondition"

    val fields: List<VehicleFieldSpec> =
        buildList {
            VehicleCardVssDefaults.definitions.forEach { definition ->
                add(VehicleFieldSpec(definition.path, topic(definition.path), definition.type, unit(definition.path)))
            }

            fun interpreted(
                name: String,
                topic: VehicleChatTopic,
                type: CardVssType,
                unit: String? = null,
            ) {
                add(VehicleFieldSpec("interpreted.$name", topic, type, unit, emptyList()))
            }
            val number = CardVssType.NUMBER
            val boolean = CardVssType.BOOLEAN
            val text = CardVssType.TEXT
            interpreted("batteryPercent", VehicleChatTopic.BATTERY, number, "%")
            interpreted("petCondition", VehicleChatTopic.CONDITION, text)
            interpreted("washerFluidLevel", VehicleChatTopic.BODY, number, "%")
            interpreted("tirePressureStatus", VehicleChatTopic.TIRES, text)
            interpreted("speed", VehicleChatTopic.MOTION, number, "km/h")
            interpreted("gear", VehicleChatTopic.MOTION, text)
            interpreted("drivingState", VehicleChatTopic.MOTION, text)
            interpreted("isEngineOn", VehicleChatTopic.MOTION, boolean)
            interpreted("isCharging", VehicleChatTopic.CHARGING, boolean)
            interpreted("outsideTemperature", VehicleChatTopic.ENVIRONMENT, number, "C")
            interpreted("isRaining", VehicleChatTopic.ENVIRONMENT, boolean)
            interpreted("attentionLevel", VehicleChatTopic.DRIVER, number, "%")
            interpreted("isDistracted", VehicleChatTopic.DRIVER, boolean)
            interpreted("isDrowsy", VehicleChatTopic.DRIVER, boolean)
            interpreted("isEmergencyBraking", VehicleChatTopic.BRAKES, boolean)
            interpreted("distanceToFrontVehicle", VehicleChatTopic.MOTION, number, "m")
            interpreted("isFuelLevelLow", VehicleChatTopic.BATTERY, boolean)
            interpreted("isEngineWarning", VehicleChatTopic.SERVICE, boolean)
            interpreted("isNavigating", VehicleChatTopic.NAVIGATION, boolean)
            interpreted("distanceToDestination", VehicleChatTopic.NAVIGATION, number, "m")
            interpreted("timeOfDay", VehicleChatTopic.TIME, text)
            add(VehicleFieldSpec(TIME, VehicleChatTopic.TIME, text))
            add(VehicleFieldSpec("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow", VehicleChatTopic.BATTERY, boolean))
        }
    private val byId = fields.associateBy { it.id }

    fun find(id: String): VehicleFieldSpec? = byId[id]

    private fun topic(path: String): VehicleChatTopic =
        when {
            ".Charging." in path -> VehicleChatTopic.CHARGING
            ".TractionBattery." in path -> VehicleChatTopic.BATTERY
            ".Tire." in path -> VehicleChatTopic.TIRES
            ".Brake." in path || ".ABS." in path || ".ParkingBrake." in path -> VehicleChatTopic.BRAKES
            ".Service." in path || ".Diagnostics." in path || path.endsWith("IsBrokenDown") -> VehicleChatTopic.SERVICE
            ".Driver." in path || path.endsWith("IsBelted") -> VehicleChatTopic.DRIVER
            path.endsWith("TraveledDistance") -> VehicleChatTopic.MOTION
            "Temperature" in path || ".Raindetection." in path -> VehicleChatTopic.ENVIRONMENT
            else -> VehicleChatTopic.BODY
        }

    private fun unit(path: String): String? =
        when {
            path.endsWith("DistanceToService") -> "km"
            path.endsWith("Range") || path.endsWith("TraveledDistance") -> "m"
            path.endsWith("TimeRemaining") || path.endsWith("TimeToComplete") || path.endsWith("TimeToService") -> "s"
            "Temperature" in path -> "C"
            path.endsWith("Displayed") ||
                path.endsWith("StateOfHealth") ||
                path.endsWith("PadWear") ||
                path.endsWith("FatigueLevel") ||
                path.endsWith("DistractionLevel") ||
                path.endsWith("Intensity") ||
                path.endsWith("WasherFluid.Level") -> "%"
            else -> null
        }
}
