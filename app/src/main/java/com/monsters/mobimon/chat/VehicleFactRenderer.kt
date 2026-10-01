package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue

internal object VehicleFactRenderer {
    fun render(
        field: VehicleChatField,
        source: VehicleObservationSource,
    ): String {
        val prefix = if (source == VehicleObservationSource.DEBUG_OVERRIDE) "시뮬레이션 · " else ""
        val value = field.value ?: return "$prefix${label(field.spec.id)}: 확인할 수 없음 (${reason(field.validity.reason)})"
        val rendered =
            when (value) {
                is VehicleValue.Boolean -> if (value.value) "감지됨" else "감지되지 않음"
                is VehicleValue.Text ->
                    when (value.value) {
                        "WARNING" -> "경고 신호 있음"
                        "LOW_BATTERY" -> "배터리 잔량 낮음"
                        "CHECKED" -> "확인된 항목에 경고 없음"
                        "PARTIAL" -> "일부 항목만 확인됨"
                        else -> value.value.ifEmpty { "없음" }
                    }
                is VehicleValue.Number ->
                    value.canonical() +
                        when (field.spec.unit) {
                            null -> ""
                            "%" -> "%"
                            "C" -> " °C"
                            else -> " ${field.spec.unit}"
                        }
            }
        val asOf = if (field.spec.id == VehicleChatFieldCatalog.TIME) " (조회 시점 차량 시계)" else ""
        return "$prefix${label(field.spec.id)}: $rendered$asOf"
    }

    private fun reason(value: String?): String =
        when {
            value == "DISCONNECTED" -> "연결 끊김"
            value == "EXPIRED" || value?.contains("EXPIRED") == true -> "수신 유효시간 만료"
            value == "UNVERIFIED_VALIDITY" -> "수신 유효성 미확인"
            value == "INVALID_VALUE" -> "잘못된 신호 값"
            value == "SUBSCRIPTION_INVALID" -> "구독 유효성 미확인"
            else -> "관측 근거 없음"
        }

    private fun label(id: String): String =
        when (id) {
            VehicleChatFieldCatalog.BATTERY -> "배터리 잔량"
            VehicleChatFieldCatalog.CONDITION -> "차량 상태"
            VehicleChatFieldCatalog.TIME -> "차량 시각"
            "interpreted.speed" -> "차량 속도"
            "interpreted.washerFluidLevel" -> "워셔액 잔량"
            "interpreted.outsideTemperature" -> "외부 온도"
            "interpreted.tirePressureStatus" -> "타이어 공기압 상태"
            "interpreted.gear" -> "기어"
            "interpreted.drivingState" -> "주행 상태"
            "interpreted.isCharging" -> "충전 상태"
            else ->
                id
                    .removePrefix("Vehicle.")
                    .removePrefix("interpreted.")
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                    .replace('.', ' ')
        }
}
