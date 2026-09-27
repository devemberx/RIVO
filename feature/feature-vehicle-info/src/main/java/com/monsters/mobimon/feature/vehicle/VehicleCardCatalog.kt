package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WarningSeverity
import com.monsters.mobimon.core.presentation.VehicleSignalConcern
import com.monsters.mobimon.core.presentation.VehicleSignalStatus

internal enum class VehicleCardStatus { INFO, NORMAL, CAUTION, UNAVAILABLE }

internal data class VehicleCardSpec(
    val id: String,
    val title: String,
    val supporting: String,
    val vssPaths: List<String>,
)

internal data class VehicleCardReading(
    val value: String?,
    val supporting: String,
)

/** The catalog names VSS candidates. Only values carried by a valid snapshot are displayed. */
internal object VehicleCardCatalog {
    private const val BATTERY = "Vehicle.Powertrain.TractionBattery."
    private const val CHASSIS = "Vehicle.Chassis."
    private val wheels = listOf("Row1.Wheel.Left", "Row1.Wheel.Right", "Row2.Wheel.Left", "Row2.Wheel.Right")

    private fun card(
        id: String,
        title: String,
        supporting: String,
        path: String,
    ) = VehicleCardSpec(id, title, supporting, listOf(path))

    private fun fourWheels(
        id: String,
        title: String,
        supporting: String,
        suffix: String,
    ) = VehicleCardSpec(id, title, supporting, wheels.map { "$CHASSIS" + "Axle.$it.$suffix" })

    val cards: List<VehicleCardSpec> =
        listOf(
            card("battery", "배터리 잔량", "구동 배터리 표시 잔량", "${BATTERY}StateOfCharge.Displayed"),
            card("battery-health", "배터리 건강도", "표준 조건에서 계산한 건강도", "${BATTERY}StateOfHealth"),
            card("battery-range", "주행 가능 거리", "배터리 기반 남은 거리", "${BATTERY}Range"),
            card("battery-time", "배터리 남은 시간", "방전까지 남은 예상 시간", "${BATTERY}TimeRemaining"),
            card("battery-error", "배터리 오류", "배터리 진단 코드", "${BATTERY}ErrorCodes"),
            card("driver-door", "운전석 문 잠금", "문 잠금 상태", "Vehicle.Cabin.Door.Row1.DriverSide.IsLocked"),
            card("service-due", "정비 필요", "차량 정비 알림", "Vehicle.Service.IsServiceDue"),
            VehicleCardSpec(
                "charging-time",
                "충전 완료 예상",
                "설정한 충전 목표까지",
                listOf("${BATTERY}Charging.TimeToComplete", "${BATTERY}Charging.IsCharging"),
            ),
            card("service-distance", "정비까지 거리", "다음 정비까지 남은 거리", "Vehicle.Service.DistanceToService"),
            card("service-time", "정비까지 시간", "다음 정비까지 남은 시간", "Vehicle.Service.TimeToService"),
            fourWheels("brake-fluid", "브레이크액 부족", "네 바퀴 부족 신호 종합", "Brake.IsFluidLevelLow"),
            card("low-beam", "하향등", "램프 고장 여부", "Vehicle.Body.Lights.Beam.Low.IsDefect"),
            card("brake-light", "브레이크등", "램프 고장 여부", "Vehicle.Body.Lights.Brake.IsDefect"),
            card("parking-brake", "주차 브레이크", "주차 브레이크 상태", "${CHASSIS}ParkingBrake.IsEngaged"),
            fourWheels("tire-low", "타이어 저압 경고", "네 바퀴 저압 신호 종합", "Tire.IsPressureLow"),
            card("driver-belt", "운전석 안전벨트", "안전벨트 착용 상태", "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted"),
            fourWheels("pad-wear", "브레이크 패드 마모", "네 바퀴 중 최대 마모율", "Brake.PadWear"),
            fourWheels("pad-warning", "패드 마모 경고", "네 바퀴 마모 신호 종합", "Brake.IsBrakesWorn"),
            card("abs", "ABS 점검", "제동 보조 시스템 오류 여부", "Vehicle.ADAS.ABS.IsError"),
            card("hood", "보닛", "보닛 개폐 상태", "Vehicle.Body.Hood.IsOpen"),
            card("trunk", "트렁크", "뒤 트렁크 개폐 상태", "Vehicle.Body.Trunk.Rear.IsOpen"),
            card("washer-low", "워셔액 부족", "앞유리 워셔액 부족 신호", "Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow"),
            card("air-temperature", "외기 온도", "차량 외부 공기 온도", "Vehicle.Exterior.AirTemperature"),
            card("rain-intensity", "비 감지 강도", "빗물 감지 센서", "Vehicle.Body.Raindetection.Intensity"),
            card("cabin-temperature", "실내 온도", "현재 실내 공기 온도", "Vehicle.Cabin.HVAC.AmbientAirTemperature"),
            card("distance", "누적 주행거리", "차량 누적 이동 거리", "Vehicle.TraveledDistance"),
            card("dtc-count", "진단 코드 수", "차량 진단 코드 개수", "Vehicle.Diagnostics.DTCCount"),
            card("fatigue", "운전자 피로도", "운전자 피로 추정 수준", "Vehicle.Driver.FatigueLevel"),
            card("distraction", "운전자 주의분산", "운전자 주의 분산 추정 수준", "Vehicle.Driver.DistractionLevel"),
            card("breakdown", "차량 고장 감지", "도로 위 고장 감지 여부", "Vehicle.IsBrokenDown"),
        )

    private val initialOnly =
        listOf(
            card("charging", "충전 상태", "현재 충전 상태", "${BATTERY}Charging.IsCharging"),
            fourWheels("tire", "타이어 공기압", "확인된 공기압 상태", "Tire.IsPressureLow"),
            VehicleCardSpec(
                "environment",
                "외부 환경",
                "외기 온도 및 비 감지",
                listOf("Vehicle.Exterior.AirTemperature", "Vehicle.Body.Raindetection.Intensity"),
            ),
            card("assist", "운전자 보조", "운전자 주의 상태", "Vehicle.Driver.DistractionLevel"),
            card("washer", "워셔액", "앞유리 워셔액 잔량", "Vehicle.Body.Windshield.Front.WasherFluid.Level"),
        )

    val defaultSlots =
        listOf("battery", "charging", "tire", "washer", "environment", "assist")
            .map { id -> requireNotNull(find(id)) }

    fun find(id: String): VehicleCardSpec? = (cards + initialOnly).firstOrNull { it.id == id }

    fun status(
        id: String,
        snapshot: VehicleSnapshot,
    ): VehicleCardStatus? {
        val spec = find(id) ?: return null
        val current = snapshot.quality == SignalQuality.VALID
        return when (id) {
            "battery" -> {
                val battery =
                    snapshot.batteryPercent?.takeIf {
                        (snapshot.batteryQuality ?: snapshot.quality) == SignalQuality.VALID && it in 0..100
                    }
                when {
                    battery == null -> VehicleCardStatus.UNAVAILABLE
                    battery < 20 -> VehicleCardStatus.CAUTION
                    else -> VehicleCardStatus.NORMAL
                }
            }
            "washer" -> {
                val level = snapshot.washerFluidLevel?.takeIf { current && it in 0..100 }
                when {
                    level == null -> VehicleCardStatus.UNAVAILABLE
                    level < 20 -> VehicleCardStatus.CAUTION
                    else -> VehicleCardStatus.NORMAL
                }
            }
            "tire" -> {
                val wheelWarning =
                    spec.vssPaths.any {
                        VehicleSignalConcern.assess(snapshot, it)?.status == VehicleSignalStatus.CAUTION
                    }
                when {
                    !current -> VehicleCardStatus.UNAVAILABLE
                    wheelWarning ||
                        snapshot.tirePressureStatus == "NG" ||
                        snapshot.warnings.any {
                            it.quality == SignalQuality.VALID &&
                                it.severity != WarningSeverity.NOTICE &&
                                (it.item.contains("타이어") || it.item.contains("바퀴"))
                        } -> VehicleCardStatus.CAUTION
                    snapshot.tirePressureStatus == "OK" || snapshot.tirePressureStatus == "정상" ->
                        VehicleCardStatus.NORMAL
                    else -> VehicleCardStatus.UNAVAILABLE
                }
            }
            "tire-low" -> {
                if (snapshot.vssCardSignals.isEmpty()) {
                    when {
                        !current -> VehicleCardStatus.UNAVAILABLE
                        snapshot.tirePressureStatus == "NG" -> VehicleCardStatus.CAUTION
                        snapshot.tirePressureStatus == "OK" -> VehicleCardStatus.NORMAL
                        else -> VehicleCardStatus.UNAVAILABLE
                    }
                } else {
                    signalStatus(spec, snapshot)
                }
            }
            "assist" -> {
                when {
                    !current -> VehicleCardStatus.UNAVAILABLE
                    snapshot.isEmergencyBraking == true || snapshot.isDrowsy == true || snapshot.isDistracted == true ->
                        VehicleCardStatus.CAUTION
                    snapshot.isEmergencyBraking != null && snapshot.isDrowsy != null && snapshot.isDistracted != null ->
                        VehicleCardStatus.NORMAL
                    else -> VehicleCardStatus.UNAVAILABLE
                }
            }
            "charging" ->
                if (current &&
                    snapshot.isCharging != null
                ) {
                    VehicleCardStatus.INFO
                } else {
                    VehicleCardStatus.UNAVAILABLE
                }
            "environment" ->
                if (current && (snapshot.outsideTemperature != null || snapshot.isRaining != null)) {
                    VehicleCardStatus.INFO
                } else {
                    VehicleCardStatus.UNAVAILABLE
                }
            else -> signalStatus(spec, snapshot)
        }
    }

    private fun signalStatus(
        spec: VehicleCardSpec,
        snapshot: VehicleSnapshot,
    ): VehicleCardStatus {
        val assessments = spec.vssPaths.map { VehicleSignalConcern.assess(snapshot, it) }
        if (assessments.all { it == null }) {
            return if (reading(spec.id, snapshot)?.value !=
                null
            ) {
                VehicleCardStatus.INFO
            } else {
                VehicleCardStatus.UNAVAILABLE
            }
        }
        return when {
            assessments.any { it?.status == VehicleSignalStatus.CAUTION } -> VehicleCardStatus.CAUTION
            assessments.all { it?.status == VehicleSignalStatus.NORMAL } -> VehicleCardStatus.NORMAL
            else -> VehicleCardStatus.UNAVAILABLE
        }
    }

    fun reading(
        id: String,
        snapshot: VehicleSnapshot,
    ): VehicleCardReading? {
        val spec = find(id) ?: return null
        val current = snapshot.quality == SignalQuality.VALID
        val batteryCurrent = (snapshot.batteryQuality ?: snapshot.quality) == SignalQuality.VALID
        if (snapshot.vssCardSignals.isNotEmpty()) {
            val value = if (current) readVssCard(spec, snapshot.vssCardSignals, batteryCurrent) else null
            return VehicleCardReading(value, spec.supporting)
        }
        val value =
            when (id) {
                "battery" -> snapshot.batteryPercent?.takeIf { batteryCurrent && it in 0..100 }?.let { "$it%" }
                "charging" -> snapshot.isCharging?.takeIf { current }?.let { if (it) "충전 중" else "충전 안 함" }
                "tire" -> snapshot.tirePressureStatus?.takeIf { current && it.isNotBlank() }
                "tire-low" ->
                    snapshot.tirePressureStatus?.takeIf { current }?.let {
                        when (it) {
                            "NG" -> "경고 있음"
                            "OK" -> "경고 없음"
                            else -> null
                        }
                    }
                "environment", "air-temperature" -> snapshot.outsideTemperature?.takeIf { current }?.let { "$it°" }
                "assist" -> snapshot.attentionLevel?.takeIf { current && it in 0..100 }?.toString()
                "washer" -> snapshot.washerFluidLevel?.takeIf { current && it in 0..100 }?.let { "$it%" }
                else -> null
            }
        return VehicleCardReading(value, spec.supporting)
    }

    private fun readVssCard(
        spec: VehicleCardSpec,
        signals: Map<String, String>,
        batteryCurrent: Boolean,
    ): String? {
        if (!batteryCurrent && spec.vssPaths.any { it.startsWith(BATTERY) }) return null

        fun number(path: String = spec.vssPaths.first()) =
            signals[path]?.toDoubleOrNull()?.takeIf {
                it.isFinite() &&
                    it >= 0
            }

        fun boolean(path: String = spec.vssPaths.first()) =
            when (signals[path]) {
                "true" -> true
                "false" -> false
                else -> null
            }

        fun percent() = number()?.takeIf { it <= 100 }?.let { "${it.toInt()}%" }

        fun anyWheelWarning(): String? {
            val values = spec.vssPaths.map { boolean(it) }
            return if (values.any { it == true }) {
                "경고 있음"
            } else if (values.any { it == null }) {
                null
            } else {
                "경고 없음"
            }
        }

        fun state(
            trueLabel: String,
            falseLabel: String,
        ) = boolean()?.let { if (it) trueLabel else falseLabel }

        return when (spec.id) {
            "battery" -> if (batteryCurrent) percent() else null
            "battery-health", "pad-wear", "fatigue", "distraction", "rain-intensity", "washer" ->
                if (spec.id == "pad-wear") {
                    spec.vssPaths
                        .map { number(it) }
                        .takeIf { it.all { value -> value != null } }
                        ?.maxOf { requireNotNull(it) }
                        ?.takeIf { it <= 100 }
                        ?.let { "${it.toInt()}%" }
                } else {
                    percent()
                }
            "battery-range", "distance" -> number()?.let { "${(it / 1000).toInt()} km" }
            "battery-time" -> number()?.let { "${(it / 3600).toInt()}시간" }
            "charging-time" ->
                boolean(spec.vssPaths[1])?.let { charging ->
                    if (charging) number()?.let { "${(it / 3600).toInt()}시간" } else "충전 안 함"
                }
            "service-time" -> number()?.let { "${(it / 86400).toInt()}일" }
            "service-distance" -> number()?.let { "${it.toInt()} km" }
            "battery-error" -> signals[spec.vssPaths.first()]?.let { if (it.isBlank()) "없음" else it }
            "driver-door" -> state("잠김", "열림")
            "service-due" -> state("정비 필요", "정상")
            "brake-fluid" -> anyWheelWarning()?.let { if (it == "경고 있음") "부족" else "정상" }
            "low-beam", "brake-light", "abs", "pad-warning", "tire-low" ->
                if (spec.vssPaths.size > 1) anyWheelWarning() else state("경고 있음", "경고 없음")
            "parking-brake" -> state("체결", "해제")
            "tire" -> anyWheelWarning()?.let { if (it == "경고 있음") "NG" else "OK" }
            "driver-belt" -> state("착용", "미착용")
            "hood", "trunk" -> state("열림", "닫힘")
            "washer-low" -> state("부족", "정상")
            "air-temperature", "cabin-temperature", "environment" ->
                signals[spec.vssPaths.first()]
                    ?.toDoubleOrNull()
                    ?.takeIf {
                        it.isFinite()
                    }?.let { "${it.toInt()}°" }
            "dtc-count" -> number()?.let { "${it.toInt()}개" }
            "breakdown" -> state("고장 감지", "정상")
            "charging" -> state("충전 중", "충전 안 함")
            "assist" -> number()?.takeIf { it <= 100 }?.let { "${(100 - it).toInt()}" }
            else -> null
        }
    }
}
