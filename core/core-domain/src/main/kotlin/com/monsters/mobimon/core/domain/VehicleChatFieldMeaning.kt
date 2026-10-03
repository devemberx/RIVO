package com.monsters.mobimon.core.domain

/** Signal metadata for the model, never a prewritten companion reply. */
object VehicleChatFieldMeaning {
    fun label(id: String): String =
        labels[id] ?: wheelLabel(id) ?: id
            .removePrefix("Vehicle.")
            .removePrefix("interpreted.")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('.', ' ')

    fun valueMeaning(field: VehicleChatField): String? =
        when (field.spec.id) {
            "interpreted.tirePressureStatus" ->
                when ((field.value as? VehicleValue.Text)?.value) {
                    "NG" -> "공기압 부족 경고가 있는 타이어가 있음. 바퀴 위치는 개별 타이어 신호로 확인."
                    "OK" -> "확인된 타이어 신호에 공기압 부족 경고 없음. 실제 압력 수치는 제공되지 않음."
                    else -> null
                }
            VehicleChatFieldCatalog.CONDITION ->
                when ((field.value as? VehicleValue.Text)?.value) {
                    "WARNING" -> "확인된 차량 경고로 아픈 표정 표시"
                    "LOW_BATTERY" -> "낮은 배터리 잔량으로 배고픈 표정 표시"
                    "NEEDS_REPLENISHMENT" -> "보충이 필요한 항목으로 배고픈 표정 표시"
                    "CHECKED" -> "확인된 항목에 경고 없음. 미확인 항목까지 정상이라는 의미는 아님."
                    "PARTIAL" -> "차량 상태의 일부 항목만 확인 가능"
                    else -> null
                }
            else -> null
        }

    private fun wheelLabel(id: String): String? {
        val position =
            when {
                ".Row1.Wheel.Left." in id -> "앞 왼쪽"
                ".Row1.Wheel.Right." in id -> "앞 오른쪽"
                ".Row2.Wheel.Left." in id -> "뒤 왼쪽"
                ".Row2.Wheel.Right." in id -> "뒤 오른쪽"
                else -> return null
            }
        val signal =
            when {
                id.endsWith("Tire.IsPressureLow") -> "타이어 공기압 부족 경고 여부"
                id.endsWith("Brake.PadWear") -> "브레이크 패드 마모율"
                id.endsWith("Brake.IsFluidLevelLow") -> "브레이크액 부족 경고 여부"
                id.endsWith("Brake.IsBrakesWorn") -> "브레이크 마모 경고 여부"
                else -> return null
            }
        return "$position $signal"
    }

    private val labels =
        mapOf(
            VehicleChatFieldCatalog.BATTERY to "배터리 잔량",
            VehicleChatFieldCatalog.CONDITION to "차량 신호에 따른 동반자 표정 상태",
            VehicleChatFieldCatalog.TIME to "조회 시점의 차량 시계 값",
            "interpreted.washerFluidLevel" to "워셔액 잔량",
            "interpreted.tirePressureStatus" to "타이어 공기압 부족 경고 종합 상태",
            "interpreted.speed" to "차량 속도",
            "interpreted.gear" to "기어",
            "interpreted.isMoving" to "차량 이동 여부",
            "interpreted.drivingState" to "주행 상태",
            "interpreted.isEngineOn" to "엔진 작동 여부",
            "interpreted.isCharging" to "충전 여부",
            "interpreted.outsideTemperature" to "외부 온도",
            "interpreted.isRaining" to "강우 감지 여부",
            "interpreted.attentionLevel" to "운전자 주의 수준",
            "interpreted.isDistracted" to "운전자 주의 분산 여부",
            "interpreted.isDrowsy" to "운전자 졸음 여부",
            "interpreted.isEmergencyBraking" to "급제동 감지 여부",
            "interpreted.distanceToFrontVehicle" to "앞 차량과의 거리",
            "interpreted.isFuelLevelLow" to "연료 부족 경고 여부",
            "interpreted.isEngineWarning" to "엔진 경고 여부",
            "interpreted.isNavigating" to "길 안내 여부",
            "interpreted.distanceToDestination" to "목적지까지 거리",
            "interpreted.timeOfDay" to "차량 시계에 따른 시간대",
        )
}
