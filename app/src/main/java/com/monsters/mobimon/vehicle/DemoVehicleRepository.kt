package com.monsters.mobimon.vehicle

import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleCardVssDefaults
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.vss.DefaultParkedVssRawVehicleSource
import com.monsters.mobimon.core.vss.ObservedVssRawVehicleSource
import com.monsters.mobimon.core.vss.VssInterpretationOverrides
import com.monsters.mobimon.core.vss.VssRawVehicleSource
import com.monsters.mobimon.core.vss.VssRawVehicleState
import com.monsters.mobimon.core.vss.VssVehicleInterpreter
import com.monsters.mobimon.core.vss.generated.VssSignals
import com.monsters.mobimon.debug.DebugInterpretationOverrides
import com.monsters.mobimon.debug.DebugRawVssState
import com.monsters.mobimon.debug.DebugVssProvider
import com.monsters.mobimon.debug.toCardSignalValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class DemoVehicleRepository(
    private val clock: Clock,
    private val ids: IdGenerator,
    private val settingsRepository: SettingsRepository,
    private val debugStore: DebugVssProvider,
    private val vssRawSource: VssRawVehicleSource,
    private val scope: CoroutineScope,
) : VehicleRepository {
    private var lastSimulatedTimestamp: String? = null
    private var simulatedTimeObservedAt: Long? = null
    private val mutableSnapshots =
        MutableStateFlow(
            initialSnapshot(),
        )
    override val snapshots = mutableSnapshots.asStateFlow()
    private var observation: Job? = null
    private var generation = 0L
    private var debugEvidence: DebugVehicleEvidence? = null
    private var debugSession = 0L

    @Synchronized
    override fun start() {
        if (observation?.isActive == true) return
        val currentGeneration = ++generation
        val epoch = ids.nextId()
        vssRawSource.start()
        observation =
            scope.launch {
                var sequence = 0L
                publicationRequests().collect {
                    val nextSequence = ++sequence
                    val observedAt = clock.nowMillis()
                    val isDebugOn = settingsRepository.settings.first().debugModeEnabled
                    val debugState = debugStore.state.value
                    val atomicSource = vssRawSource as? ObservedVssRawVehicleSource
                    val sourceFrame = atomicSource?.observationFrames?.value

                    val snapshot: VehicleSnapshot =
                        if (isDebugOn) {
                            val interpreted =
                                VssVehicleInterpreter.snapshot(
                                    raw = debugState.raw.toVssRawVehicleState(),
                                    id = "$epoch-$nextSequence",
                                    epoch = epoch,
                                    sequence = nextSequence,
                                    observedAtMillis = observedAt,
                                    source = SignalSource.SIMULATED,
                                    timeObservedAtMillis =
                                        simulatedTimeObservation(
                                            debugState.raw.toVssRawVehicleState(),
                                        ),
                                    overrides = debugState.overrides.toVssInterpretationOverrides(),
                                )
                            interpreted.copy(
                                isDebuggerOverride = true,
                                vssCardSignals = debugState.cardExtraSignals + debugState.raw.toCardSignalValues(),
                            )
                        } else {
                            val interpreted =
                                VssVehicleInterpreter.snapshot(
                                    raw = if (atomicSource != null) sourceFrame?.raw else vssRawSource.state.value,
                                    id = "$epoch-$nextSequence",
                                    epoch = epoch,
                                    sequence = nextSequence,
                                    observedAtMillis = observedAt,
                                    source = rawSourceSignalSource(),
                                    timeObservedAtMillis = sourceTimeObservation(),
                                    batteryObservedAtMillis = sourceBatteryObservation(observedAt),
                                )
                            interpreted.copy(
                                vssCardSignals = fallbackCardSignals(),
                                evidenceFrame = sourceFrame?.evidence,
                            )
                        }

                    synchronized(this@DemoVehicleRepository) {
                        if (generation == currentGeneration) {
                            mutableSnapshots.value =
                                if (isDebugOn) {
                                    val observer =
                                        debugEvidence
                                            ?: DebugVehicleEvidence("$epoch-debug-${++debugSession}").also {
                                                debugEvidence =
                                                    it
                                            }
                                    snapshot.copy(
                                        evidenceFrame =
                                            observer.capture(
                                                snapshot,
                                                debugState.receivedAtElapsedMillis ?: observedAt,
                                                observedAt,
                                                debugState,
                                            ),
                                    )
                                } else {
                                    debugEvidence = null
                                    snapshot
                                }
                        }
                    }
                }
            }
    }

    private fun publicationRequests() =
        merge(
            flow {
                emit(Unit)
                while (true) {
                    delay(2_000)
                    emit(Unit)
                }
            },
            vssRawSource.state.drop(1).map { Unit },
            (vssRawSource as? ObservedVssRawVehicleSource)?.observationFrames?.drop(1)?.map { Unit }
                ?: kotlinx.coroutines.flow.emptyFlow(),
            debugStore.state.drop(1).map { Unit },
            settingsRepository.settings
                .map { it.debugModeEnabled }
                .distinctUntilChanged()
                .drop(1)
                .map { Unit },
        )

    @Synchronized
    override fun stop() {
        generation++
        observation?.cancel()
        observation = null
        debugEvidence = null
        vssRawSource.stop()
        if (vssRawSource is DefaultParkedVssRawVehicleSource) {
            val current = mutableSnapshots.value
            val interpreted =
                VssVehicleInterpreter.snapshot(
                    raw = vssRawSource.state.value,
                    id = current.id,
                    epoch = current.epoch,
                    sequence = current.sequence,
                    observedAtMillis = clock.nowMillis(),
                    source = rawSourceSignalSource(),
                    timeObservedAtMillis = sourceTimeObservation(),
                )
            mutableSnapshots.value = interpreted.copy(vssCardSignals = fallbackCardSignals())
            return
        }
        mutableSnapshots.value =
            mutableSnapshots.value.copy(
                quality = SignalQuality.UNAVAILABLE,
                drivingState = DrivingState.UNKNOWN,
                batteryQuality = SignalQuality.UNAVAILABLE,
                evidenceFrame =
                    mutableSnapshots.value.evidenceFrame?.let {
                        it.copy(subscription = it.subscription.copy(connected = false, subscriptionValid = false))
                    },
            )
    }

    @Synchronized
    private fun simulatedTimeObservation(raw: VssRawVehicleState?): Long? {
        val timestamp = raw?.currentLocation?.timestamp
        if (timestamp != lastSimulatedTimestamp) {
            lastSimulatedTimestamp = timestamp
            simulatedTimeObservedAt = timestamp?.let { clock.nowMillis() }
        }
        return simulatedTimeObservedAt
    }

    private fun sourceTimeObservation(): Long? =
        if (vssRawSource is DefaultParkedVssRawVehicleSource) {
            simulatedTimeObservation(vssRawSource.state.value)
        } else {
            vssRawSource.timeObservedAtMillis
        }

    private fun rawSourceSignalSource(): SignalSource =
        if (vssRawSource is DefaultParkedVssRawVehicleSource) {
            SignalSource.SIMULATED
        } else {
            SignalSource.REAL
        }

    private fun sourceBatteryObservation(now: Long): Long? =
        if (vssRawSource is DefaultParkedVssRawVehicleSource) now else vssRawSource.batteryObservedAtMillis

    private fun initialSnapshot(): VehicleSnapshot {
        val observedAt = clock.nowMillis()
        val interpreted =
            VssVehicleInterpreter.snapshot(
                raw = vssRawSource.state.value,
                id = INITIAL_SNAPSHOT_ID,
                epoch = INITIAL_SNAPSHOT_EPOCH,
                sequence = 0,
                observedAtMillis = observedAt,
                source = rawSourceSignalSource(),
                timeObservedAtMillis = sourceTimeObservation(),
                batteryObservedAtMillis = sourceBatteryObservation(observedAt),
            )
        return interpreted.copy(vssCardSignals = fallbackCardSignals())
    }

    private fun fallbackCardSignals(): Map<String, String> =
        if (BuildConfig.DEBUG && vssRawSource is DefaultParkedVssRawVehicleSource) {
            VehicleCardVssDefaults.values
        } else {
            emptyMap()
        }
}

private const val INITIAL_SNAPSHOT_ID = "initial"
private const val INITIAL_SNAPSHOT_EPOCH = "initial"

private fun DebugRawVssState.toVssRawVehicleState(): VssRawVehicleState =
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
        cabin =
            VssSignals.Cabin(
                infotainment =
                    VssSignals.Cabin.Infotainment(
                        navigation =
                            VssSignals.Cabin.Infotainment.Navigation(
                                destinationSet =
                                    VssSignals.Cabin.Infotainment.Navigation.DestinationSet(
                                        latitude = destinationLatitude.toFloat(),
                                        longitude = destinationLongitude.toFloat(),
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
                                        right =
                                            VssSignals.Chassis.Axle.Row1.Wheel.Right(
                                                tire =
                                                    VssSignals.Chassis.Axle.Row1.Wheel.Right.Tire(
                                                        isPressureLow = row1RightTirePressureLow,
                                                    ),
                                            ),
                                    ),
                            ),
                        row2 =
                            VssSignals.Chassis.Axle.Row2(
                                wheel =
                                    VssSignals.Chassis.Axle.Row2.Wheel(
                                        left =
                                            VssSignals.Chassis.Axle.Row2.Wheel.Left(
                                                tire =
                                                    VssSignals.Chassis.Axle.Row2.Wheel.Left.Tire(
                                                        isPressureLow = row2LeftTirePressureLow,
                                                    ),
                                            ),
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
                latitude = currentLatitude.toFloat(),
                longitude = currentLongitude.toFloat(),
                timestamp = currentLocationTimestamp,
            ),
        diagnostics =
            VssSignals.Diagnostics(
                dTCCount = if (obdMilOn && diagnosticsDtcCount == 0) 1 else diagnosticsDtcCount,
            ),
        driver =
            VssSignals.Driver(
                fatigueLevel = driverFatigueLevel,
                distractionLevel = driverDistractionLevel,
            ),
        exterior = VssSignals.Exterior(airTemperature = exteriorAirTemperature),
        isMoving = vehicleIsMoving,
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

private fun DebugInterpretationOverrides.toVssInterpretationOverrides(): VssInterpretationOverrides =
    VssInterpretationOverrides(
        isDistracted = isDistracted,
        isDrowsy = isDrowsy,
        attentionLevel = attentionLevel,
        isEmergencyBraking = isEmergencyBraking,
        distanceToFrontVehicle = distanceToFrontVehicle,
        isCharging = isCharging,
        batteryPercent = batteryPercent,
        outsideTemperature = outsideTemperature,
        isRaining = isRaining,
        washerFluidLevel = washerFluidLevel,
        isEngineWarning = isEngineWarning,
        tirePressureStatus = tirePressureStatus,
        isMoving = isMoving,
        speed = speed,
        gear = gear,
        isNavigating = isNavigating,
        distanceToDestination = distanceToDestination,
        isEngineOn = isEngineOn,
        timeOfDay = timeOfDay,
    )
