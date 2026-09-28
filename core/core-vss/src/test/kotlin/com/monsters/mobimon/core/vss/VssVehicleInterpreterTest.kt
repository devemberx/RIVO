package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.SignalUnavailableReason
import com.monsters.mobimon.core.vss.generated.VssSignals
import org.junit.Assert.assertEquals
import org.junit.Test

class VssVehicleInterpreterTest {
    @Test fun invalidBatterySocIsUnavailableInsteadOfClampedToAValidPercentage() {
        for (soc in listOf(-1f, 101f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val snapshot =
                VssVehicleInterpreter.snapshot(
                    raw =
                        VssSignals(
                            powertrain =
                                VssSignals.Powertrain(
                                    tractionBattery =
                                        VssSignals.Powertrain.TractionBattery(
                                            stateOfCharge =
                                                VssSignals.Powertrain.TractionBattery.StateOfCharge(displayed = soc),
                                        ),
                                ),
                        ),
                    id = "vehicle",
                    epoch = "epoch",
                    sequence = 1,
                    observedAtMillis = 100,
                    source = SignalSource.REAL,
                    batteryObservedAtMillis = 100,
                )
            assertEquals(null, snapshot.batteryPercent)
            assertEquals(SignalQuality.UNAVAILABLE, snapshot.batteryQuality)
        }
    }

    @Test
    fun parkInterpretationRequiresNoMotionZeroSpeedAndParkGear() {
        val cases =
            listOf(
                Triple(false, 0f, 126) to DrivingState.PARKED,
                Triple(true, 0f, 126) to DrivingState.MOVING,
                Triple(false, 1f, 126) to DrivingState.MOVING,
                Triple(false, 0f, 127) to DrivingState.UNKNOWN,
            )
        cases.forEach { (signals, expected) ->
            val (moving, speed, gear) = signals
            val snapshot =
                VssVehicleInterpreter.snapshot(
                    raw = vssSignals(vehicleIsMoving = moving, vehicleSpeedKmh = speed, selectedGear = gear),
                    id = "vehicle",
                    epoch = "epoch",
                    sequence = 1,
                    observedAtMillis = 100,
                    source = SignalSource.SIMULATED,
                )
            assertEquals(expected, snapshot.drivingState)
        }
    }

    @Test
    fun unavailableRawSourceDefaultsToParkGearStandingStill() {
        val raw = DefaultParkedVssRawVehicleSource().state.value

        assertEquals(126, raw?.powertrain?.transmission?.selectedGear)
        assertEquals(false, raw?.isMoving)
        assertEquals(0f, raw?.speed)
    }

    @Test
    fun unavailableRawStateReportsNotReportedRealVehicleData() {
        val snapshot =
            VssVehicleInterpreter.snapshot(
                raw = null,
                id = "id-1",
                epoch = "epoch",
                sequence = 1,
                observedAtMillis = 100,
                source = SignalSource.REAL,
            )

        assertEquals(SignalSource.REAL, snapshot.source)
        assertEquals(DrivingState.UNKNOWN, snapshot.drivingState)
        assertEquals(SignalQuality.UNAVAILABLE, snapshot.quality)
        assertEquals(SignalQuality.UNAVAILABLE, snapshot.batteryQuality)
        assertEquals(SignalUnavailableReason.NOT_REPORTED, snapshot.parkingUnavailableReason)
        assertEquals(SignalUnavailableReason.NOT_REPORTED, snapshot.batteryUnavailableReason)
    }

    @Test
    fun rawVssSignalsComputeVehicleSnapshotInterpretation() {
        val snapshot =
            VssVehicleInterpreter.snapshot(
                raw =
                    vssSignals(
                        driverDistractionLevel = 72f,
                        driverFatigueLevel = 75f,
                        dmsIsWarning = true,
                        driverEmergencyBrakingDetected = true,
                        obstacleFrontCenterDistance = 16f,
                        tractionBatterySocDisplayed = 49f,
                        chargingCableConnected = true,
                        chargingAveragePowerKw = 3.5f,
                        exteriorAirTemperature = 27f,
                        rainIntensity = 5,
                        washerFluidLevel = 34,
                        row1LeftTirePressureLow = true,
                        diagnosticsDtcCount = 2,
                        vehicleIsMoving = false,
                        vehicleSpeedKmh = 20f,
                        selectedGear = 126,
                        combustionEngineRunning = true,
                        currentLocationTimestamp = "2026-10-08T09:00:00Z",
                    ),
                id = "id-1",
                epoch = "epoch",
                sequence = 1,
                observedAtMillis = 100,
                source = SignalSource.REAL,
            )

        assertEquals(SignalSource.REAL, snapshot.source)
        assertEquals(DrivingState.MOVING, snapshot.drivingState)
        assertEquals(SignalQuality.VALID, snapshot.quality)
        assertEquals(49, snapshot.batteryPercent)
        assertEquals(20, snapshot.speed)
        assertEquals("P", snapshot.gear)
        assertEquals(true, snapshot.isDistracted)
        assertEquals(true, snapshot.isDrowsy)
        assertEquals(28, snapshot.attentionLevel)
        assertEquals(true, snapshot.isEmergencyBraking)
        assertEquals(16, snapshot.distanceToFrontVehicle)
        assertEquals(true, snapshot.isCharging)
        assertEquals(27, snapshot.outsideTemperature)
        assertEquals(true, snapshot.isRaining)
        assertEquals(34, snapshot.washerFluidLevel)
        assertEquals("NG", snapshot.tirePressureStatus)
        assertEquals(true, snapshot.isEngineWarning)
        assertEquals(true, snapshot.isEngineOn)
        assertEquals("Morning", snapshot.timeOfDay)
    }

    @Test
    fun overridesWinOverRawVssInterpretation() {
        val snapshot =
            VssVehicleInterpreter.snapshot(
                raw =
                    vssSignals(
                        driverDistractionLevel = 100f,
                        vehicleIsMoving = true,
                        vehicleSpeedKmh = 80f,
                        selectedGear = 127,
                    ),
                id = "id-1",
                epoch = "epoch",
                sequence = 1,
                observedAtMillis = 100,
                source = SignalSource.SIMULATED,
                overrides =
                    VssInterpretationOverrides(
                        isDistracted = false,
                        isMoving = false,
                        speed = 0,
                        gear = "P",
                        batteryPercent = 11,
                        timeOfDay = "밤",
                    ),
            )

        assertEquals(SignalSource.SIMULATED, snapshot.source)
        assertEquals(DrivingState.PARKED, snapshot.drivingState)
        assertEquals(false, snapshot.isDistracted)
        assertEquals(0, snapshot.speed)
        assertEquals("P", snapshot.gear)
        assertEquals(11, snapshot.batteryPercent)
        assertEquals("Night", snapshot.timeOfDay)
    }

    @Test
    fun timeOfDayKeepsSevenBackgroundPeriodsFromVssTime() {
        val expected =
            listOf(
                "Midnight",
                "Midnight",
                "Midnight",
                "Midnight",
                "Midnight",
                "Sunrise",
                "Sunrise",
                "Morning",
                "Morning",
                "Morning",
                "Morning",
                "Morning",
                "Day",
                "Day",
                "Day",
                "Day",
                "Afternoon",
                "Afternoon",
                "Sunset",
                "Sunset",
                "Night",
                "Night",
                "Night",
                "Night",
            )

        expected.forEachIndexed { hour, period ->
            assertEquals("Hour $hour", period, snapshotTimeOfDay(hour.toString()))
        }
        mapOf(
            "sunrise" to "Sunrise",
            "일출" to "Sunrise",
            "afternoon" to "Afternoon",
            "오후" to "Afternoon",
            "늦은 오후" to "Afternoon",
            "sunset" to "Sunset",
            "노을" to "Sunset",
            "저녁" to "Sunset",
            "midnight" to "Midnight",
            "한밤중" to "Midnight",
            "16:00" to "Afternoon",
            "18시" to "Sunset",
        ).forEach { (input, period) ->
            assertEquals(input, period, snapshotTimeOfDay(input))
        }
    }
}

private fun snapshotTimeOfDay(input: String): String =
    VssVehicleInterpreter
        .snapshot(
            raw = vssSignals(),
            id = "id-$input",
            epoch = "epoch",
            sequence = 1,
            observedAtMillis = 100,
            source = SignalSource.REAL,
            overrides = VssInterpretationOverrides(timeOfDay = input),
        ).timeOfDay ?: error("timeOfDay should be interpreted")

private fun vssSignals(
    driverFatigueLevel: Float = 0f,
    driverDistractionLevel: Float = 0f,
    dmsIsWarning: Boolean = false,
    driverEmergencyBrakingDetected: Boolean = false,
    obstacleFrontCenterDistance: Float = 50f,
    tractionBatterySocDisplayed: Float = 72f,
    chargingCableConnected: Boolean = false,
    tractionBatteryChargingIsCharging: Boolean = false,
    chargingAveragePowerKw: Float = 0f,
    exteriorAirTemperature: Float = 20f,
    rainIntensity: Int = 0,
    washerFluidLevel: Int = 100,
    row1LeftTirePressureLow: Boolean = false,
    row2RightTirePressureLow: Boolean = false,
    diagnosticsDtcCount: Int = 0,
    vehicleIsMoving: Boolean = false,
    vehicleSpeedKmh: Float = 0f,
    selectedGear: Int = 126,
    destinationLatitude: Float = 0f,
    destinationLongitude: Float = 0f,
    currentLocationTimestamp: String = "2026-10-08T10:00:00Z",
    currentLatitude: Float = 0f,
    currentLongitude: Float = 0f,
    combustionEngineRunning: Boolean = false,
): VssSignals =
    VssSignals(
        adas =
            VssSignals.ADAS(
                dms = VssSignals.ADAS.DMS(isWarning = dmsIsWarning),
                obstacleDetection =
                    VssSignals.ADAS.ObstacleDetection(
                        front =
                            VssSignals.ADAS.ObstacleDetection.Front(
                                center =
                                    VssSignals.ADAS.ObstacleDetection.Front.Center(
                                        distance = obstacleFrontCenterDistance,
                                    ),
                            ),
                    ),
            ),
        body =
            VssSignals.Body(
                raindetection = VssSignals.Body.Raindetection(intensity = rainIntensity),
                windshield =
                    VssSignals.Body.Windshield(
                        front =
                            VssSignals.Body.Windshield.Front(
                                washerFluid =
                                    VssSignals.Body.Windshield.Front.WasherFluid(
                                        level = washerFluidLevel,
                                    ),
                            ),
                    ),
            ),
        chassis =
            VssSignals.Chassis(
                axle =
                    VssSignals.Chassis.Axle(
                        row1 =
                            VssSignals.Chassis.Axle.Row1(
                                wheel =
                                    VssSignals.Chassis.Axle.Row1.Wheel(
                                        left =
                                            VssSignals.Chassis.Axle.Row1.Wheel.Left(
                                                tire =
                                                    VssSignals.Chassis.Axle.Row1.Wheel.Left.Tire(
                                                        isPressureLow = row1LeftTirePressureLow,
                                                    ),
                                            ),
                                    ),
                            ),
                        row2 =
                            VssSignals.Chassis.Axle.Row2(
                                wheel =
                                    VssSignals.Chassis.Axle.Row2.Wheel(
                                        right =
                                            VssSignals.Chassis.Axle.Row2.Wheel.Right(
                                                tire =
                                                    VssSignals.Chassis.Axle.Row2.Wheel.Right.Tire(
                                                        isPressureLow = row2RightTirePressureLow,
                                                    ),
                                            ),
                                    ),
                            ),
                    ),
                brake =
                    VssSignals.Chassis.Brake(
                        isDriverEmergencyBrakingDetected = driverEmergencyBrakingDetected,
                    ),
            ),
        currentLocation =
            VssSignals.CurrentLocation(
                latitude = currentLatitude,
                longitude = currentLongitude,
                timestamp = currentLocationTimestamp,
            ),
        diagnostics = VssSignals.Diagnostics(dTCCount = diagnosticsDtcCount),
        driver =
            VssSignals.Driver(
                distractionLevel = driverDistractionLevel,
                fatigueLevel = driverFatigueLevel,
            ),
        exterior = VssSignals.Exterior(airTemperature = exteriorAirTemperature),
        isMoving = vehicleIsMoving,
        cabin =
            VssSignals.Cabin(
                infotainment =
                    VssSignals.Cabin.Infotainment(
                        navigation =
                            VssSignals.Cabin.Infotainment.Navigation(
                                destinationSet =
                                    VssSignals.Cabin.Infotainment.Navigation.DestinationSet(
                                        latitude = destinationLatitude,
                                        longitude = destinationLongitude,
                                    ),
                            ),
                    ),
            ),
        powertrain =
            VssSignals.Powertrain(
                combustionEngine = VssSignals.Powertrain.CombustionEngine(isRunning = combustionEngineRunning),
                tractionBattery =
                    VssSignals.Powertrain.TractionBattery(
                        charging =
                            VssSignals.Powertrain.TractionBattery.Charging(
                                averagePower = chargingAveragePowerKw,
                                chargingPort =
                                    VssSignals.Powertrain.TractionBattery.Charging.ChargingPort(
                                        anyPosition =
                                            VssSignals.Powertrain.TractionBattery.Charging.ChargingPort.AnyPosition(
                                                isChargingCableConnected = chargingCableConnected,
                                            ),
                                    ),
                                isCharging = tractionBatteryChargingIsCharging,
                            ),
                        stateOfCharge =
                            VssSignals.Powertrain.TractionBattery.StateOfCharge(
                                displayed = tractionBatterySocDisplayed,
                            ),
                    ),
                transmission = VssSignals.Powertrain.Transmission(selectedGear = selectedGear),
            ),
        speed = vehicleSpeedKmh,
    )
