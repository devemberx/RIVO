package com.monsters.mobimon.core.domain

/** Metadata for raw fields currently exposed by the app debugger and interpreter; no demo values. */
object VehicleRawChatFields {
    val fields: List<VehicleFieldSpec> =
        listOf(
            VehicleFieldSpec("Vehicle.Driver.FatigueLevel", VehicleChatTopic.DRIVER, CardVssType.NUMBER, "%"),
            VehicleFieldSpec("Vehicle.Driver.DistractionLevel", VehicleChatTopic.DRIVER, CardVssType.NUMBER, "%"),
            VehicleFieldSpec("Vehicle.ADAS.DMS.IsWarning", VehicleChatTopic.DRIVER, CardVssType.BOOLEAN),
            VehicleFieldSpec(
                "Vehicle.ADAS.LaneDepartureDetection.IsWarning",
                VehicleChatTopic.MOTION,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec("Vehicle.ADAS.ObstacleDetection.IsWarning", VehicleChatTopic.MOTION, CardVssType.BOOLEAN),
            VehicleFieldSpec(
                "Vehicle.ADAS.ESC.IsStrongCrossWindDetected",
                VehicleChatTopic.ENVIRONMENT,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.ADAS.ESC.RoadFriction.MostProbable",
                VehicleChatTopic.ENVIRONMENT,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec("Vehicle.Acceleration.Longitudinal", VehicleChatTopic.MOTION, CardVssType.NUMBER, "m/s2"),
            VehicleFieldSpec(
                "Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected",
                VehicleChatTopic.BRAKES,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.ADAS.ObstacleDetection.Front.Center.Distance",
                VehicleChatTopic.MOTION,
                CardVssType.NUMBER,
                "m",
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.FuelSystem.IsFuelLevelLow",
                VehicleChatTopic.BATTERY,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed",
                VehicleChatTopic.BATTERY,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.TractionBattery.Charging.ChargingPort.AnyPosition.IsChargingCableConnected",
                VehicleChatTopic.CHARGING,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.TractionBattery.Charging.IsCharging",
                VehicleChatTopic.CHARGING,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.TractionBattery.Charging.AveragePower",
                VehicleChatTopic.CHARGING,
                CardVssType.NUMBER,
                "kW",
            ),
            VehicleFieldSpec(
                "Vehicle.Cabin.HVAC.AmbientAirTemperature",
                VehicleChatTopic.ENVIRONMENT,
                CardVssType.NUMBER,
                "C",
            ),
            VehicleFieldSpec("Vehicle.Exterior.AirTemperature", VehicleChatTopic.ENVIRONMENT, CardVssType.NUMBER, "C"),
            VehicleFieldSpec(
                "Vehicle.Body.Raindetection.Intensity",
                VehicleChatTopic.ENVIRONMENT,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow",
                VehicleChatTopic.BODY,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Windshield.Front.WasherFluid.Level",
                VehicleChatTopic.BODY,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Windshield.Front.Wiping.WiperWear",
                VehicleChatTopic.BODY,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Windshield.Rear.Wiping.WiperWear",
                VehicleChatTopic.BODY,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.PadWear",
                VehicleChatTopic.BRAKES,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.PadWear",
                VehicleChatTopic.BRAKES,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row2.Wheel.Left.Brake.PadWear",
                VehicleChatTopic.BRAKES,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.PadWear",
                VehicleChatTopic.BRAKES,
                CardVssType.NUMBER,
                "%",
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow",
                VehicleChatTopic.TIRES,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire.IsPressureLow",
                VehicleChatTopic.TIRES,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.IsPressureLow",
                VehicleChatTopic.TIRES,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow",
                VehicleChatTopic.TIRES,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec("Vehicle.Diagnostics.DTCCount", VehicleChatTopic.SERVICE, CardVssType.NUMBER),
            VehicleFieldSpec("Vehicle.OBD.Status.IsMILOn", VehicleChatTopic.SERVICE, CardVssType.BOOLEAN),
            VehicleFieldSpec("Vehicle.Service.IsServiceDue", VehicleChatTopic.SERVICE, CardVssType.BOOLEAN),
            VehicleFieldSpec("Vehicle.IsMoving", VehicleChatTopic.MOTION, CardVssType.BOOLEAN),
            VehicleFieldSpec("Vehicle.Speed", VehicleChatTopic.MOTION, CardVssType.NUMBER, "km/h"),
            VehicleFieldSpec(
                "Vehicle.Powertrain.Transmission.SelectedGear",
                VehicleChatTopic.MOTION,
                CardVssType.NUMBER,
            ),
            VehicleFieldSpec("Vehicle.TraveledDistance", VehicleChatTopic.MOTION, CardVssType.NUMBER, "m"),
            VehicleFieldSpec(
                "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted",
                VehicleChatTopic.DRIVER,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling",
                VehicleChatTopic.BODY,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Body.Lights.DirectionIndicator.Right.IsSignaling",
                VehicleChatTopic.BODY,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec(
                "Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Latitude",
                VehicleChatTopic.NAVIGATION,
                CardVssType.NUMBER,
                "deg",
            ),
            VehicleFieldSpec(
                "Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Longitude",
                VehicleChatTopic.NAVIGATION,
                CardVssType.NUMBER,
                "deg",
            ),
            VehicleFieldSpec("Vehicle.CurrentLocation.Timestamp", VehicleChatTopic.TIME, CardVssType.TEXT),
            VehicleFieldSpec(
                "Vehicle.CurrentLocation.Latitude",
                VehicleChatTopic.NAVIGATION,
                CardVssType.NUMBER,
                "deg",
            ),
            VehicleFieldSpec(
                "Vehicle.CurrentLocation.Longitude",
                VehicleChatTopic.NAVIGATION,
                CardVssType.NUMBER,
                "deg",
            ),
            VehicleFieldSpec(
                "Vehicle.Powertrain.CombustionEngine.IsRunning",
                VehicleChatTopic.MOTION,
                CardVssType.BOOLEAN,
            ),
            VehicleFieldSpec("Vehicle.TraveledDistanceSinceStart", VehicleChatTopic.MOTION, CardVssType.NUMBER, "km"),
            VehicleFieldSpec("Vehicle.TripDuration", VehicleChatTopic.MOTION, CardVssType.NUMBER, "s"),
            VehicleFieldSpec("Vehicle.TripMeterReading", VehicleChatTopic.MOTION, CardVssType.NUMBER, "km"),
            VehicleFieldSpec("Vehicle.AverageSpeed", VehicleChatTopic.MOTION, CardVssType.NUMBER, "km/h"),
        )
}
