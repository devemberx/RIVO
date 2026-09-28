package com.monsters.mobimon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.core.database.DebugPointRepository
import com.monsters.mobimon.core.domain.CardVssType
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestEvaluator
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.domain.DrivingQuestResult
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.WeatherCondition
import com.monsters.mobimon.core.navigation.LocalDebugSettingsAvailable
import com.monsters.mobimon.debug.DebugCardVssSignals
import com.monsters.mobimon.debug.DebugInterpretationOverrides
import com.monsters.mobimon.debug.DebugRawVssState
import com.monsters.mobimon.debug.DebugVssState
import com.monsters.mobimon.debug.deriveWeatherCondition
import com.monsters.mobimon.debug.toDriveEvaluationData
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugOverlayEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun debugStore(): com.monsters.mobimon.debug.DebugStore

    fun debugPoints(): DebugPointRepository

    fun pointEconomy(): PointEconomy

    fun vehicleRepository(): VehicleRepository
}

@Composable
fun DebugOverlay() {
    val debugSettingsAvailable = BuildConfig.DEBUG || LocalDebugSettingsAvailable.current
    val context = LocalContext.current
    val entryPoint =
        remember(context) {
            try {
                EntryPointAccessors.fromApplication(context, DebugOverlayEntryPoint::class.java)
            } catch (
                e: Exception,
            ) {
                null
            }
        }
    if (entryPoint == null) return
    val settingsRepository = entryPoint.settingsRepository()
    val debugStore = entryPoint.debugStore()
    val debugPoints = entryPoint.debugPoints()
    val pointEconomy = entryPoint.pointEconomy()
    val vehicleRepository = entryPoint.vehicleRepository()

    val isDebugEnabledFlow =
        remember(settingsRepository) { settingsRepository.settings.map { it.debugModeEnabled } }
    val isDebugEnabled by isDebugEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val state by debugStore.state.collectAsStateWithLifecycle()
    val safeDriveCount by debugStore.safeDriveCount.collectAsStateWithLifecycle()

    fun updateState(reducer: (DebugVssState) -> DebugVssState) {
        debugStore.updateState(reducer)
    }

    if (shouldShowDebugOverlay(isDebugEnabled, debugSettingsAvailable)) {
        val scope = rememberCoroutineScope()
        var pointUnitText by remember { mutableStateOf("100") }
        val pointUnit = pointUnitText.toIntOrNull() ?: 0

        var questWeather by remember { mutableStateOf(WeatherCondition.CLEAR) }
        var questStatusMessage by remember { mutableStateOf("") }
        var simDistanceKm by remember { mutableStateOf("35") }
        var simSafeBeltMinutes by remember { mutableStateOf("10") }
        var simSafeScore by remember { mutableStateOf("85") }
        var simTotalDistanceKm by remember { mutableStateOf("100") }
        var simSafeDays by remember { mutableStateOf("5") }
        var simTurnSignals by remember { mutableStateOf("5") }
        var simLaneDepartures by remember { mutableStateOf("0") }
        var simNoViolations by remember { mutableStateOf(true) }
        var simMaintenanceReached by remember { mutableStateOf(true) }
        var simBatteryChargedProperly by remember { mutableStateOf(true) }
        var simRestedDuringLongDrive by remember { mutableStateOf(true) }
        var simWasherFluidRefilled by remember { mutableStateOf(true) }
        var simTirePressureNormalWeekly by remember { mutableStateOf(true) }
        var evalResults by remember { mutableStateOf<List<DrivingQuestResult>>(emptyList()) }

        LaunchedEffect(state.isRaining, state.raw.rainIntensity, state.timeOfDay) {
            questWeather = state.deriveWeatherCondition()
        }

        // Live-link the simulated VSS signals to per-quest evidence so toggling a raw signal (seatbelt,
        // distraction, distance, turn signal, tire, …) advances the matching quest. The simulator below
        // stays a manual override for aggregates VSS cannot express (safe days, long-trip rest).
        LaunchedEffect(state, questWeather, safeDriveCount) {
            pointEconomy.updateDriveEvaluation(state.toDriveEvaluationData(questWeather, safeDriveCount))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DebugOverlayPlacement {
                DebugOverlayFrame(title = "디버깅 모드 (테스트 신호)") {
                    // App State / Points
                    DebugSection("Point") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text("+/-", color = Color.White, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .background(Color(0xFF203C58), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (pointUnitText.isEmpty()) {
                                        Text(
                                            text = "0",
                                            color = Color(0xFF627D98),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    BasicTextField(
                                        value = pointUnitText,
                                        onValueChange = { newText ->
                                            if (newText.isEmpty()) {
                                                pointUnitText = ""
                                            } else {
                                                val newVal = newText.toIntOrNull()
                                                if (newVal != null && newVal in 0..1000) {
                                                    pointUnitText =
                                                        if (pointUnitText == "0" &&
                                                            newText.length == 2 &&
                                                            newText.startsWith("0")
                                                        ) {
                                                            newText.drop(1)
                                                        } else {
                                                            newText
                                                        }
                                                }
                                            }
                                        },
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                        cursorBrush = SolidColor(Color(0xFF71E5C5)),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.testTag("debug-point-unit"),
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (pointUnit == 0) return@Button
                                    scope.launch { debugPoints.add(pointUnit.toLong()) }
                                },
                                modifier = Modifier.weight(0.8f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF203C58)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    if (pointUnit == 0) return@Button
                                    scope.launch { debugPoints.subtract(pointUnit.toLong()) }
                                },
                                modifier = Modifier.weight(0.8f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF203C58)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) { Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    scope.launch { debugPoints.reset() }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF802020)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) { Text("Reset", color = Color.White, fontSize = 12.sp) }
                        }
                    }

                    DebugSection("상점 (Store)") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "소유/착용 아이템 초기화",
                                color = Color(0xFFF4F7FC),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    scope.launch { debugPoints.resetStoreInventory() }
                                },
                                modifier = Modifier.testTag("debug-store-reset"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF802020)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("Reset", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    DebugSection("차량 신호 (VSS)") {
                        DebugVssRawSection(state.raw) { raw -> updateState { it.copy(raw = raw) } }
                        DebugCardVssSection(state.cardExtraSignals) { path, value ->
                            updateState { it.copy(cardExtraSignals = it.cardExtraSignals + (path to value)) }
                        }
                    }

                    DebugSection("차량 상태 (Interpretation)") {
                        DebugInterpretationSection(state) { overrides ->
                            updateState { it.copy(overrides = overrides) }
                        }
                    }

                    DebugSection("Quest") {
                        Text(
                            "1. 날씨 가중치 설정",
                            color = Color(0xFFF4F7FC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DebugSegmentedRow(
                            "Weather",
                            listOf("CLEAR", "CLOUDY", "RAIN/SNOW"),
                            when (questWeather) {
                                WeatherCondition.CLEAR -> "CLEAR"
                                WeatherCondition.CLOUDY_OR_NIGHT -> "CLOUDY"
                                WeatherCondition.RAIN_OR_SNOW -> "RAIN/SNOW"
                            },
                        ) { sel ->
                            val newWeather =
                                when (sel) {
                                    "CLOUDY" -> WeatherCondition.CLOUDY_OR_NIGHT
                                    "RAIN/SNOW" -> WeatherCondition.RAIN_OR_SNOW
                                    else -> WeatherCondition.CLEAR
                                }
                            questWeather = newWeather
                            when (newWeather) {
                                WeatherCondition.RAIN_OR_SNOW -> {
                                    updateState {
                                        it.copy(
                                            raw = it.raw.copy(rainIntensity = 3),
                                            overrides = it.overrides.copy(isRaining = true),
                                        )
                                    }
                                }
                                WeatherCondition.CLOUDY_OR_NIGHT -> {
                                    updateState {
                                        it.copy(
                                            raw = it.raw.copy(rainIntensity = 0),
                                            overrides = it.overrides.copy(isRaining = false, timeOfDay = "NIGHT"),
                                        )
                                    }
                                }
                                WeatherCondition.CLEAR -> {
                                    updateState {
                                        it.copy(
                                            raw = it.raw.copy(rainIntensity = 0),
                                            overrides = it.overrides.copy(isRaining = false, timeOfDay = "DAY"),
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "2. 퀘스트 제어",
                            color = Color(0xFFF4F7FC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    updateState {
                                        it.copy(
                                            raw =
                                                it.raw.copy(
                                                    selectedGear = 126,
                                                    vehicleSpeedKmh = 0f,
                                                    vehicleIsMoving = false,
                                                ),
                                            overrides =
                                                it.overrides.copy(
                                                    gear = null,
                                                    speed = null,
                                                    isMoving = null,
                                                ),
                                        )
                                    }
                                    questStatusMessage = "안전 정차 상태 (P, 속도 0) 설정됨"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF203C58)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("P단 정차 설정", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        debugPoints.resetQuestCompletions()
                                        questStatusMessage = "퀘스트 완료 이력 초기화 완료"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF802050)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("완료 이력 초기화", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        // Quest "안전 주행 5회": VSS judges each drive (5km+안전), the app accumulates the count.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val qualifies =
                                        DrivingQuestEvaluator()
                                            .evaluateSafeDriveCompletion(
                                                state.toDriveEvaluationData(questWeather, safeDriveCount),
                                            ).isSatisfied
                                    if (qualifies) {
                                        debugStore.recordSafeDrive()
                                        questStatusMessage = "안전 주행 1회 기록 (${safeDriveCount + 1}/5)"
                                    } else {
                                        questStatusMessage = "이번 주행은 안전 주행 조건 미충족 (5km↑·안전점수 80↑)"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5B42)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("안전 주행 1회 기록 ($safeDriveCount/5)", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    debugStore.resetSafeDriveCount()
                                    questStatusMessage = "안전 주행 횟수 초기화"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B3A2A)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("횟수 초기화", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val snapshot = vehicleRepository.snapshots.value
                                    // Shortcut: satisfy every driving condition so the gated award path grants all.
                                    pointEconomy.updateDriveEvaluation(
                                        DriveEvaluationData(
                                            distanceKm = 35f,
                                            safeBeltMinutes = 15,
                                            safeDriveScore = 95,
                                            totalDistanceKm = 150f,
                                            safeDriveCount = 5,
                                            turnSignalOnCount = 5,
                                            continuousDistanceKm = 35f,
                                            isDistracted = false,
                                            laneDepartureCount = 0,
                                            hardBrakeCount = 0,
                                            hardAccelCount = 0,
                                            overspeedCount = 0,
                                            isDestinationMaintenanceCenter = true,
                                            isDestinationReached = true,
                                            isBatteryChargedProperly = true,
                                            hasRestedDuringLongDrive = true,
                                            isWasherFluidRefilled = true,
                                            isTirePressureNormalWeekly = true,
                                            weather = questWeather,
                                        ),
                                    )
                                    val allQuests =
                                        listOf(
                                            DrivingQuestIds.SEATBELT,
                                            DrivingQuestIds.SAFE_DRIVE,
                                            DrivingQuestIds.DISTANCE_100KM,
                                            DrivingQuestIds.CLEAN_DRIVE,
                                            DrivingQuestIds.FIRST_DRIVE,
                                            DrivingQuestIds.FOCUS_DRIVE,
                                            DrivingQuestIds.LANE_KEEP,
                                            DrivingQuestIds.MAINTENANCE,
                                            DrivingQuestIds.TURN_SIGNAL,
                                            DrivingQuestIds.SAFE_5DAYS,
                                            DrivingQuestIds.BATTERY_CARE,
                                            DrivingQuestIds.LONG_TRIP_REST,
                                            DrivingQuestIds.WASHER_FLUID,
                                            DrivingQuestIds.TIRE_CHECK,
                                            DrivingQuestIds.HIDDEN_COSTUME,
                                            DrivingQuestIds.HIDDEN_BACKGROUND,
                                            DrivingQuestIds.HIDDEN_NEW_FRIEND,
                                        )
                                    var awardedCount = 0
                                    var alreadyCount = 0
                                    for (qid in allQuests) {
                                        when (pointEconomy.awardQuest(qid, snapshot)) {
                                            is PointAwardResult.Awarded -> awardedCount++
                                            is PointAwardResult.AlreadyAwarded -> alreadyCount++
                                            else -> Unit
                                        }
                                    }
                                    questStatusMessage = "지급: $awardedCount 건, 이미 완료: $alreadyCount 건"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5B42)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "전체 퀘스트 일괄 지급",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        if (questStatusMessage.isNotEmpty()) {
                            Text(
                                questStatusMessage,
                                color = Color(0xFF71E5C5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "3. 주행 조건 평가 시뮬레이터",
                            color = Color(0xFF87DAF5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        DebugInputRow("주행거리 (km)", simDistanceKm) { simDistanceKm = it }
                        DebugInputRow("안전벨트 착용 (분)", simSafeBeltMinutes) { simSafeBeltMinutes = it }
                        DebugInputRow("안전운전 점수 (0-100)", simSafeScore) { simSafeScore = it }
                        DebugInputRow("누적거리 (km)", simTotalDistanceKm) { simTotalDistanceKm = it }
                        DebugInputRow("안전 주행 횟수 (회)", simSafeDays) { simSafeDays = it }
                        DebugInputRow("방향지시등 (회)", simTurnSignals) { simTurnSignals = it }
                        DebugInputRow("차선이탈 (회)", simLaneDepartures) { simLaneDepartures = it }
                        DebugToggleRow("위반 없음 (급제동/급가속/과속 0)", simNoViolations) { simNoViolations = it }
                        DebugToggleRow("정비소 목적지 도착 완료", simMaintenanceReached) { simMaintenanceReached = it }
                        DebugToggleRow("배터리 적정 충전 완료", simBatteryChargedProperly) { simBatteryChargedProperly = it }
                        DebugToggleRow("장거리 주행 중 휴식 완료", simRestedDuringLongDrive) { simRestedDuringLongDrive = it }
                        DebugToggleRow("워셔액 보충 확인", simWasherFluidRefilled) { simWasherFluidRefilled = it }
                        DebugToggleRow(
                            label = "타이어 공기압 정상 유지",
                            checked = simTirePressureNormalWeekly,
                        ) {
                            simTirePressureNormalWeekly = it
                        }

                        Button(
                            onClick = {
                                val dist = simDistanceKm.toFloatOrNull() ?: 0f
                                val belt = simSafeBeltMinutes.toIntOrNull() ?: 0
                                val score = simSafeScore.toIntOrNull() ?: 0
                                val total = simTotalDistanceKm.toFloatOrNull() ?: 0f
                                val safeDays = simSafeDays.toIntOrNull() ?: 0
                                val signals = simTurnSignals.toIntOrNull() ?: 0
                                val lanes = simLaneDepartures.toIntOrNull() ?: 0
                                val violations = if (simNoViolations) 0 else 1

                                val evalData =
                                    DriveEvaluationData(
                                        distanceKm = dist,
                                        safeBeltMinutes = belt,
                                        safeDriveScore = score,
                                        totalDistanceKm = total,
                                        safeDriveCount = safeDays,
                                        turnSignalOnCount = signals,
                                        continuousDistanceKm = dist,
                                        isDistracted = state.isDistracted,
                                        laneDepartureCount = lanes,
                                        hardBrakeCount = violations,
                                        hardAccelCount = violations,
                                        overspeedCount = violations,
                                        isDestinationMaintenanceCenter = simMaintenanceReached,
                                        isDestinationReached = simMaintenanceReached,
                                        isBatteryChargedProperly = simBatteryChargedProperly,
                                        hasRestedDuringLongDrive = simRestedDuringLongDrive,
                                        isWasherFluidRefilled = simWasherFluidRefilled,
                                        isTirePressureNormalWeekly = simTirePressureNormalWeekly,
                                        weather = questWeather,
                                    )
                                evalResults = DrivingQuestEvaluator().evaluateAll(evalData)
                                pointEconomy.updateDriveEvaluation(evalData)
                                questStatusMessage = "조건 판정 완료: 만족 ${evalResults.count { it.isSatisfied }}건 반영됨"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C4A70)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "조건 판정 실행 및 화면 반영 (Evaluate All)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Button(
                                onClick = {
                                    simDistanceKm = "35"
                                    simSafeBeltMinutes = "15"
                                    simSafeScore = "95"
                                    simTotalDistanceKm = "150"
                                    simSafeDays = "5"
                                    simTurnSignals = "5"
                                    simLaneDepartures = "0"
                                    simNoViolations = true
                                    simMaintenanceReached = true
                                    simBatteryChargedProperly = true
                                    simRestedDuringLongDrive = true
                                    simWasherFluidRefilled = true
                                    simTirePressureNormalWeekly = true

                                    val allSatisfiedData =
                                        DriveEvaluationData(
                                            distanceKm = 35f,
                                            safeBeltMinutes = 15,
                                            safeDriveScore = 95,
                                            totalDistanceKm = 150f,
                                            safeDriveCount = 5,
                                            turnSignalOnCount = 5,
                                            continuousDistanceKm = 35f,
                                            isDistracted = false,
                                            laneDepartureCount = 0,
                                            hardBrakeCount = 0,
                                            hardAccelCount = 0,
                                            overspeedCount = 0,
                                            isDestinationMaintenanceCenter = true,
                                            isDestinationReached = true,
                                            isBatteryChargedProperly = true,
                                            hasRestedDuringLongDrive = true,
                                            isWasherFluidRefilled = true,
                                            isTirePressureNormalWeekly = true,
                                            weather = questWeather,
                                        )
                                    evalResults = DrivingQuestEvaluator().evaluateAll(allSatisfiedData)
                                    pointEconomy.updateDriveEvaluation(allSatisfiedData)
                                    questStatusMessage = "전체 주행 퀘스트 조건 만족 설정됨 (완료 가능)"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5B42)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("전체 조건 만족 (완료 가능)", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    simDistanceKm = "0"
                                    simSafeBeltMinutes = "0"
                                    simSafeScore = "0"
                                    simTotalDistanceKm = "0"
                                    simSafeDays = "0"
                                    simTurnSignals = "0"
                                    simLaneDepartures = "0"
                                    simNoViolations = false
                                    simMaintenanceReached = false
                                    simBatteryChargedProperly = false
                                    simRestedDuringLongDrive = false
                                    simWasherFluidRefilled = false
                                    simTirePressureNormalWeekly = false

                                    val emptyData = DriveEvaluationData()
                                    evalResults = DrivingQuestEvaluator().evaluateAll(emptyData)
                                    pointEconomy.updateDriveEvaluation(emptyData)
                                    questStatusMessage = "모든 주행 조건 초기화됨 (진행 중)"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B3A2A)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text("조건 초기화 (진행 중)", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        if (evalResults.isNotEmpty()) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0D1B2A), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                evalResults.forEach { r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        val icon = if (r.isSatisfied) "[O]" else "[X]"
                                        val color = if (r.isSatisfied) Color(0xFF71E5C5) else Color(0xFFE57373)
                                        Text(
                                            "$icon ${r.questId}: ${if (r.isSatisfied) "+${r.earnedPoints}P" else "0P"}",
                                            color = color,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            r.reason,
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldShowDebugOverlay(
    debugModeEnabled: Boolean,
    debugSettingsAvailable: Boolean,
): Boolean = debugModeEnabled && debugSettingsAvailable

@Composable
fun DebugOverlayFrame(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var closed by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    if (closed) return

    Column(
        modifier =
            modifier
                .heightIn(min = 64.dp, max = 600.dp)
                .then(if (minimized) Modifier else Modifier.height(600.dp))
                .background(Color(0xFF091525), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF142A42), RoundedCornerShape(12.dp)),
    ) {
        DebugOverlayHeader(
            title = title,
            minimized = minimized,
            onMinimizedChange = { minimized = !minimized },
            onClose = { closed = true },
        )
        if (!minimized) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DebugOverlayHeader(
    title: String,
    minimized: Boolean,
    onMinimizedChange: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF091525), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color(0xFFF4F7FC),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        DebugHeaderButton(
            label = if (minimized) "+" else "-",
            contentDescription = if (minimized) "디버그 창 펼치기" else "디버그 창 축소",
            onClick = onMinimizedChange,
        )
        DebugHeaderButton(
            label = "x",
            contentDescription = "디버그 창 닫기",
            onClick = onClose,
        )
    }
}

@Composable
private fun DebugHeaderButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(44.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            color = Color(0xFFF4F7FC),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DebugVssRawSection(
    raw: DebugRawVssState,
    onRawChange: (DebugRawVssState) -> Unit,
) {
    DebugSection("A. 운전자 상태") {
        DebugRawNumberRow("Vehicle.Driver.FatigueLevel", raw.driverFatigueLevel) {
            onRawChange(raw.copy(driverFatigueLevel = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.Driver.DistractionLevel", raw.driverDistractionLevel) {
            onRawChange(raw.copy(driverDistractionLevel = it.toFloatOrNull() ?: 0f))
        }
        DebugToggleRow("Vehicle.ADAS.DMS.IsWarning", raw.dmsIsWarning) {
            onRawChange(raw.copy(dmsIsWarning = it))
        }
    }

    DebugSection("B. 안전/ADAS") {
        DebugToggleRow("Vehicle.ADAS.LaneDepartureDetection.IsWarning", raw.laneDepartureWarning) {
            onRawChange(raw.copy(laneDepartureWarning = it))
        }
        DebugToggleRow("Vehicle.ADAS.ObstacleDetection.IsWarning", raw.obstacleDetectionWarning) {
            onRawChange(raw.copy(obstacleDetectionWarning = it))
        }
        DebugToggleRow("Vehicle.ADAS.ESC.IsStrongCrossWindDetected", raw.strongCrossWindDetected) {
            onRawChange(raw.copy(strongCrossWindDetected = it))
        }
        DebugRawNumberRow("Vehicle.ADAS.ESC.RoadFriction.MostProbable", raw.roadFrictionMostProbable) {
            onRawChange(raw.copy(roadFrictionMostProbable = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.Acceleration.Longitudinal", raw.accelerationLongitudinal) {
            onRawChange(raw.copy(accelerationLongitudinal = it.toFloatOrNull() ?: 0f))
        }
        DebugToggleRow("Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected", raw.driverEmergencyBrakingDetected) {
            onRawChange(raw.copy(driverEmergencyBrakingDetected = it))
        }
        DebugRawNumberRow("Vehicle.ADAS.ObstacleDetection.Front.Center.Distance", raw.obstacleFrontCenterDistance) {
            onRawChange(raw.copy(obstacleFrontCenterDistance = it.toFloatOrNull() ?: 0f))
        }
    }

    DebugSection("C. 에너지") {
        DebugToggleRow("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow", raw.fuelLevelLow) {
            onRawChange(raw.copy(fuelLevelLow = it))
        }
        DebugRawNumberRow(
            "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed",
            raw.tractionBatterySocDisplayed,
        ) {
            onRawChange(raw.copy(tractionBatterySocDisplayed = it.toFloatOrNull() ?: 0f))
        }
        DebugToggleRow(
            "Vehicle.Powertrain.TractionBattery.Charging.ChargingPort.AnyPosition.IsChargingCableConnected",
            raw.chargingCableConnected,
        ) {
            onRawChange(raw.copy(chargingCableConnected = it))
        }
        DebugToggleRow(
            "Vehicle.Powertrain.TractionBattery.Charging.IsCharging",
            raw.tractionBatteryChargingIsCharging,
        ) {
            onRawChange(raw.copy(tractionBatteryChargingIsCharging = it))
        }
        DebugRawNumberRow("Vehicle.Powertrain.TractionBattery.Charging.AveragePower", raw.chargingAveragePowerKw) {
            onRawChange(raw.copy(chargingAveragePowerKw = it.toFloatOrNull() ?: 0f))
        }
    }

    DebugSection("D. 환경") {
        DebugRawNumberRow("Vehicle.Cabin.HVAC.AmbientAirTemperature", raw.cabinAmbientAirTemperature) {
            onRawChange(raw.copy(cabinAmbientAirTemperature = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.Exterior.AirTemperature", raw.exteriorAirTemperature) {
            onRawChange(raw.copy(exteriorAirTemperature = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.Body.Raindetection.Intensity", raw.rainIntensity) {
            onRawChange(raw.copy(rainIntensity = it.toIntOrNull() ?: 0))
        }
    }

    DebugSection("E. 소모품") {
        DebugToggleRow("Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow", raw.washerFluidLow) {
            onRawChange(raw.copy(washerFluidLow = it))
        }
        DebugRawNumberRow("Vehicle.Body.Windshield.Front.WasherFluid.Level", raw.washerFluidLevel) {
            onRawChange(raw.copy(washerFluidLevel = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Body.Windshield.Front.Wiping.WiperWear", raw.frontWiperWear) {
            onRawChange(raw.copy(frontWiperWear = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Body.Windshield.Rear.Wiping.WiperWear", raw.rearWiperWear) {
            onRawChange(raw.copy(rearWiperWear = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.PadWear", raw.row1LeftBrakePadWear) {
            onRawChange(raw.copy(row1LeftBrakePadWear = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.PadWear", raw.row1RightBrakePadWear) {
            onRawChange(raw.copy(row1RightBrakePadWear = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Chassis.Axle.Row2.Wheel.Left.Brake.PadWear", raw.row2LeftBrakePadWear) {
            onRawChange(raw.copy(row2LeftBrakePadWear = it.toIntOrNull() ?: 0))
        }
        DebugRawNumberRow("Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.PadWear", raw.row2RightBrakePadWear) {
            onRawChange(raw.copy(row2RightBrakePadWear = it.toIntOrNull() ?: 0))
        }
    }

    DebugSection("F. 차량건강") {
        DebugToggleRow("Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow", raw.row1LeftTirePressureLow) {
            onRawChange(raw.copy(row1LeftTirePressureLow = it))
        }
        DebugToggleRow("Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire.IsPressureLow", raw.row1RightTirePressureLow) {
            onRawChange(raw.copy(row1RightTirePressureLow = it))
        }
        DebugToggleRow("Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.IsPressureLow", raw.row2LeftTirePressureLow) {
            onRawChange(raw.copy(row2LeftTirePressureLow = it))
        }
        DebugToggleRow("Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow", raw.row2RightTirePressureLow) {
            onRawChange(raw.copy(row2RightTirePressureLow = it))
        }
        DebugRawNumberRow("Vehicle.Diagnostics.DTCCount", raw.diagnosticsDtcCount) {
            onRawChange(raw.copy(diagnosticsDtcCount = it.toIntOrNull() ?: 0))
        }
        DebugToggleRow("Vehicle.OBD.Status.IsMILOn", raw.obdMilOn) {
            onRawChange(raw.copy(obdMilOn = it))
        }
        DebugToggleRow("Vehicle.Service.IsServiceDue", raw.serviceDue) {
            onRawChange(raw.copy(serviceDue = it))
        }
    }

    DebugSection("G. 주행/활동") {
        DebugToggleRow("Vehicle.IsMoving", raw.vehicleIsMoving) {
            onRawChange(raw.copy(vehicleIsMoving = it))
        }
        DebugRawNumberRow("Vehicle.Speed", raw.vehicleSpeedKmh) {
            onRawChange(raw.copy(vehicleSpeedKmh = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.Powertrain.Transmission.SelectedGear", raw.selectedGear) {
            onRawChange(raw.copy(selectedGear = it.toIntOrNull() ?: 126))
        }
        DebugRawNumberRow("Vehicle.TraveledDistance (m)", raw.traveledDistanceMeters) {
            onRawChange(raw.copy(traveledDistanceMeters = it.toFloatOrNull() ?: 0f))
        }
        DebugToggleRow("Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted", raw.driverSeatBelted) {
            onRawChange(raw.copy(driverSeatBelted = it))
        }
    }

    DebugSection("H. 방향지시등/내비게이션/위치") {
        DebugToggleRow("Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling", raw.leftIndicatorSignaling) {
            onRawChange(raw.copy(leftIndicatorSignaling = it))
        }
        DebugToggleRow("Vehicle.Body.Lights.DirectionIndicator.Right.IsSignaling", raw.rightIndicatorSignaling) {
            onRawChange(raw.copy(rightIndicatorSignaling = it))
        }
        DebugRawNumberRow("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Latitude", raw.destinationLatitude) {
            onRawChange(raw.copy(destinationLatitude = it.toDoubleOrNull() ?: 0.0))
        }
        DebugRawNumberRow("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Longitude", raw.destinationLongitude) {
            onRawChange(raw.copy(destinationLongitude = it.toDoubleOrNull() ?: 0.0))
        }
        DebugRawNumberRow("Vehicle.CurrentLocation.Timestamp", raw.currentLocationTimestamp, wideInput = true) {
            onRawChange(raw.copy(currentLocationTimestamp = it))
        }
        DebugRawNumberRow("Vehicle.CurrentLocation.Latitude", raw.currentLatitude) {
            onRawChange(raw.copy(currentLatitude = it.toDoubleOrNull() ?: 0.0))
        }
        DebugRawNumberRow("Vehicle.CurrentLocation.Longitude", raw.currentLongitude) {
            onRawChange(raw.copy(currentLongitude = it.toDoubleOrNull() ?: 0.0))
        }
    }

    DebugSection("I. 트립/주행 시작") {
        DebugToggleRow("Vehicle.Powertrain.CombustionEngine.IsRunning", raw.combustionEngineRunning) {
            onRawChange(raw.copy(combustionEngineRunning = it))
        }
        DebugRawNumberRow("Vehicle.TraveledDistanceSinceStart", raw.traveledDistanceSinceStartKm) {
            onRawChange(raw.copy(traveledDistanceSinceStartKm = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.TripDuration", raw.tripDurationSeconds) {
            onRawChange(raw.copy(tripDurationSeconds = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.TripMeterReading", raw.tripMeterReadingKm) {
            onRawChange(raw.copy(tripMeterReadingKm = it.toFloatOrNull() ?: 0f))
        }
        DebugRawNumberRow("Vehicle.AverageSpeed", raw.averageSpeedKmh) {
            onRawChange(raw.copy(averageSpeedKmh = it.toFloatOrNull() ?: 0f))
        }
    }
}

@Composable
private fun DebugCardVssSection(
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
) {
    DebugSection("J. 차량상태 카드 추가 신호") {
        DebugCardVssSignals.additional.forEach { definition ->
            val value = values[definition.path] ?: definition.defaultValue
            when (definition.type) {
                CardVssType.BOOLEAN ->
                    DebugToggleRow(definition.path, value == "true") {
                        onChange(definition.path, it.toString())
                    }
                CardVssType.NUMBER ->
                    DebugRawNumberRow(definition.path, value) { input ->
                        if (input.toDoubleOrNull()?.isFinite() == true) onChange(definition.path, input)
                    }
                CardVssType.TEXT ->
                    DebugRawNumberRow(definition.path, value, wideInput = true) {
                        onChange(definition.path, it)
                    }
            }
        }
    }
}

@Composable
private fun DebugRawNumberRow(
    label: String,
    value: Any,
    wideInput: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    DebugInputRow(label, value.toString(), isNumber = false, wideInput = wideInput, onValueChange = onValueChange)
}

@Composable
private fun DebugInterpretationSection(
    state: DebugVssState,
    onOverridesChange: (DebugInterpretationOverrides) -> Unit,
) {
    val overrides = state.overrides
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DebugInterpretationRow(
            label = "isDistracted",
            value = state.isDistracted.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isDistracted?.toString().orEmpty(),
            formula = "Vehicle.Driver.DistractionLevel >= 70",
            onManualValueChange = { onOverridesChange(overrides.copy(isDistracted = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isDistracted = null)) },
        )
        DebugInterpretationRow(
            label = "isDrowsy",
            value = state.isDrowsy.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isDrowsy?.toString().orEmpty(),
            formula = "Vehicle.Driver.FatigueLevel >= 70 || Vehicle.ADAS.DMS.IsWarning",
            onManualValueChange = { onOverridesChange(overrides.copy(isDrowsy = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isDrowsy = null)) },
        )
        DebugInterpretationRow(
            label = "attentionLevel",
            value = state.attentionLevel.toString(),
            manualValue = overrides.attentionLevel?.toString().orEmpty(),
            formula = "(100 - Vehicle.Driver.DistractionLevel).coerceIn(0, 100)",
            onManualValueChange = { onOverridesChange(overrides.copy(attentionLevel = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(attentionLevel = null)) },
        )
        DebugInterpretationRow(
            label = "isEmergencyBraking",
            value = state.isEmergencyBraking.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isEmergencyBraking?.toString().orEmpty(),
            formula = "Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected",
            onManualValueChange = { onOverridesChange(overrides.copy(isEmergencyBraking = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isEmergencyBraking = null)) },
        )
        DebugInterpretationRow(
            label = "distanceToFrontVehicle",
            value = state.distanceToFrontVehicle.toString(),
            manualValue = overrides.distanceToFrontVehicle?.toString().orEmpty(),
            formula = "Vehicle.ADAS.ObstacleDetection.Front.Center.Distance.roundToInt()",
            onManualValueChange = { onOverridesChange(overrides.copy(distanceToFrontVehicle = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(distanceToFrontVehicle = null)) },
        )
        DebugInterpretationRow(
            label = "isCharging",
            value = state.isCharging.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isCharging?.toString().orEmpty(),
            formula = "Charging.IsCharging || (ChargingCableConnected && Charging.AveragePower > 0)",
            onManualValueChange = { onOverridesChange(overrides.copy(isCharging = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isCharging = null)) },
        )
        DebugInterpretationRow(
            label = "batteryPercent",
            value = state.batteryPercent.toString(),
            manualValue = overrides.batteryPercent?.toString().orEmpty(),
            formula = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed.roundToInt()",
            onManualValueChange = { onOverridesChange(overrides.copy(batteryPercent = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(batteryPercent = null)) },
        )
        DebugInterpretationRow(
            label = "outsideTemperature",
            value = state.outsideTemperature.toString(),
            manualValue = overrides.outsideTemperature?.toString().orEmpty(),
            formula = "Vehicle.Exterior.AirTemperature.roundToInt()",
            onManualValueChange = { onOverridesChange(overrides.copy(outsideTemperature = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(outsideTemperature = null)) },
        )
        DebugInterpretationRow(
            label = "isRaining",
            value = state.isRaining.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isRaining?.toString().orEmpty(),
            formula = "Vehicle.Body.Raindetection.Intensity > 0",
            onManualValueChange = { onOverridesChange(overrides.copy(isRaining = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isRaining = null)) },
        )
        DebugInterpretationRow(
            label = "washerFluidLevel",
            value = state.washerFluidLevel.toString(),
            manualValue = overrides.washerFluidLevel?.toString().orEmpty(),
            formula = "Vehicle.Body.Windshield.Front.WasherFluid.Level",
            onManualValueChange = { onOverridesChange(overrides.copy(washerFluidLevel = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(washerFluidLevel = null)) },
        )
        DebugInterpretationRow(
            label = "isEngineWarning",
            value = state.isEngineWarning.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isEngineWarning?.toString().orEmpty(),
            formula = "Vehicle.OBD.Status.IsMILOn || Vehicle.Diagnostics.DTCCount > 0",
            onManualValueChange = { onOverridesChange(overrides.copy(isEngineWarning = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isEngineWarning = null)) },
        )
        DebugInterpretationRow(
            label = "tirePressureStatus",
            value = state.tirePressureStatus,
            manualValue = overrides.tirePressureStatus.orEmpty(),
            formula = "if (any Tire.IsPressureLow) \"NG\" else \"OK\"",
            onManualValueChange = { onOverridesChange(overrides.copy(tirePressureStatus = it.ifBlank { null })) },
            onClearManualValue = { onOverridesChange(overrides.copy(tirePressureStatus = null)) },
        )
        DebugInterpretationRow(
            label = "isMoving",
            value = state.isMoving.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isMoving?.toString().orEmpty(),
            formula = "Vehicle.IsMoving || Vehicle.Speed > 0",
            onManualValueChange = { onOverridesChange(overrides.copy(isMoving = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isMoving = null)) },
        )
        DebugInterpretationRow(
            label = "speed",
            value = state.speed.toString(),
            manualValue = overrides.speed?.toString().orEmpty(),
            formula = "Vehicle.Speed.roundToInt()",
            onManualValueChange = { onOverridesChange(overrides.copy(speed = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(speed = null)) },
        )
        DebugInterpretationRow(
            label = "gear",
            value = state.gear,
            manualValue = overrides.gear.orEmpty(),
            formula = "SelectedGear: 126=P, 127=D, 0=N, negative=R",
            onManualValueChange = { onOverridesChange(overrides.copy(gear = it.ifBlank { null })) },
            onClearManualValue = { onOverridesChange(overrides.copy(gear = null)) },
        )
        DebugInterpretationRow(
            label = "isNavigating",
            value = state.isNavigating.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isNavigating?.toString().orEmpty(),
            formula = "distance(currentLocation, destinationSet) > 100m",
            onManualValueChange = { onOverridesChange(overrides.copy(isNavigating = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isNavigating = null)) },
        )
        DebugInterpretationRow(
            label = "distanceToDestination",
            value = state.distanceToDestination.toString(),
            manualValue = overrides.distanceToDestination?.toString().orEmpty(),
            formula = "haversine(CurrentLocation, DestinationSet).roundToInt()",
            onManualValueChange = { onOverridesChange(overrides.copy(distanceToDestination = it.toIntOrNull())) },
            onClearManualValue = { onOverridesChange(overrides.copy(distanceToDestination = null)) },
        )
        DebugInterpretationRow(
            label = "isEngineOn",
            value = state.isEngineOn.toString(),
            inputType = DebugInterpretationInputType.Boolean,
            manualValue = overrides.isEngineOn?.toString().orEmpty(),
            formula = "Vehicle.Powertrain.CombustionEngine.IsRunning",
            onManualValueChange = { onOverridesChange(overrides.copy(isEngineOn = it.toBooleanOverride())) },
            onClearManualValue = { onOverridesChange(overrides.copy(isEngineOn = null)) },
        )
        DebugInterpretationRow(
            label = "timeOfDay",
            value = state.timeOfDay,
            manualValue = overrides.timeOfDay.orEmpty(),
            formula =
                "VSS hour: 00-04=Midnight, 05-06=Sunrise, 07-11=Morning, 12-15=Day, " +
                    "16-17=Afternoon, 18-19=Sunset, 20-23=Night",
            onManualValueChange = { onOverridesChange(overrides.copy(timeOfDay = it.ifBlank { null })) },
            onClearManualValue = { onOverridesChange(overrides.copy(timeOfDay = null)) },
            presets =
                listOf(
                    "Midnight (01시)" to "01:00",
                    "Sunrise (06시)" to "06:00",
                    "Morning (09시)" to "09:00",
                    "Day (14시)" to "14:00",
                    "Afternoon (16시)" to "16:00",
                    "Sunset (18시)" to "18:00",
                    "Night (20시)" to "20:00",
                ),
        )
    }
}

enum class DebugInterpretationInputType {
    Text,
    Boolean,
}

@Composable
fun DebugInterpretationRow(
    label: String,
    value: String,
    inputType: DebugInterpretationInputType = DebugInterpretationInputType.Text,
    formula: String,
    onManualValueChange: (String) -> Unit,
    onClearManualValue: () -> Unit,
    manualValue: String = "",
    presets: List<Pair<String, String>> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1B2A), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(190.dp),
            )
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF203C58)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.semantics { contentDescription = "$label 계산식 보기" },
            ) {
                Text("?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                value,
                color = Color(0xFF71E5C5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(72.dp),
            )

            when (inputType) {
                DebugInterpretationInputType.Boolean -> {
                    val checked =
                        manualValue.toBooleanStrictOrNull()
                            ?: value.toBooleanStrictOrNull()
                            ?: false
                    Switch(
                        checked = checked,
                        onCheckedChange = { onManualValueChange(it.toString()) },
                        modifier = Modifier.testTag("debug-interpretation-toggle-$label"),
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF71E5C5),
                            ),
                    )
                }

                DebugInterpretationInputType.Text -> {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .background(Color(0xFF203C58), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF42658A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        BasicTextField(
                            value = manualValue,
                            onValueChange = onManualValueChange,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFF71E5C5)),
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("debug-interpretation-input-$label"),
                        )
                    }
                }
            }

            Button(
                onClick = onClearManualValue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF203C58)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text("자동", color = Color.White, fontSize = 12.sp)
            }
        }
        if (expanded) {
            Text(formula, color = Color(0xFFBFD7EA), fontSize = 12.sp)
        }
        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "빠른 선택:",
                    color = Color(0xFF8DA9C4),
                    fontSize = 11.sp,
                )
                presets.forEach { (title, valToSet) ->
                    val isSelected =
                        manualValue == valToSet ||
                            (manualValue.isBlank() && value.equals(title.substringBefore(" "), ignoreCase = true))
                    Button(
                        onClick = { onManualValueChange(valToSet) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF71E5C5) else Color(0xFF203C58),
                            ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("debug-interpretation-preset-$label-$valToSet"),
                    ) {
                        Text(
                            title,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun String.toBooleanOverride(): Boolean? =
    when (trim().lowercase()) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> null
    }

@Composable
fun DebugSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF142A42), RoundedCornerShape(8.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription =
                            if (expanded) {
                                "$title 접기"
                            } else {
                                "$title 펼치기"
                            }
                    }.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "-" else "+",
                color = Color(0xFF87DAF5),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.width(18.dp),
            )
            Text(
                title,
                color = Color(0xFF87DAF5),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
fun DebugToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFFFFFFF), fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF71E5C5),
                ),
        )
    }
}

@Composable
fun DebugSegmentedRow(
    label: String,
    options: List<String>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFFFFFFF), fontSize = 16.sp)
        Row(
            modifier = Modifier.background(Color(0xFF203C58), RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selectedValue
                Box(
                    modifier =
                        Modifier
                            .clickable { onValueChange(option) }
                            .background(
                                if (isSelected) Color(0xFF71E5C5) else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            ).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun DebugInputRow(
    label: String,
    value: String,
    isNumber: Boolean = true,
    wideInput: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (isNumber) {
            val currentParsed = text.toIntOrNull() ?: 0
            val externalParsed = value.toIntOrNull() ?: 0
            if (currentParsed != externalParsed) {
                text = value
            }
        } else {
            if (text != value) {
                text = value
            }
        }
    }

    @Composable
    fun InputBox(modifier: Modifier) {
        Box(
            modifier =
                modifier
                    .background(Color(0xFF203C58), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF42658A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isNumber && text.isEmpty()) {
                Text(
                    text = "0",
                    color = Color(0xFF627D98),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    if (isNumber) {
                        if (newText.isNotEmpty() && newText != "-" && newText.toIntOrNull() == null) {
                            return@BasicTextField
                        }
                    }
                    val sanitized =
                        if (isNumber &&
                            text == "0" &&
                            newText.length == 2 &&
                            newText.startsWith("0") &&
                            newText[1].isDigit()
                        ) {
                            newText.drop(1)
                        } else {
                            newText
                        }
                    text = sanitized
                    onValueChange(sanitized)
                },
                textStyle =
                    TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                    ),
                cursorBrush = SolidColor(Color(0xFF71E5C5)),
                keyboardOptions =
                    if (isNumber) {
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        )
                    } else {
                        KeyboardOptions.Default
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("debug-input-$label"),
            )
        }
    }

    if (wideInput) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, color = Color(0xFFFFFFFF), fontSize = 16.sp)
            InputBox(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color(0xFFFFFFFF), fontSize = 16.sp)
            InputBox(Modifier.width(100.dp))
        }
    }
}
