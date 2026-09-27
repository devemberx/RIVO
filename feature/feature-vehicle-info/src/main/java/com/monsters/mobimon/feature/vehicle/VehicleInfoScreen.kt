package com.monsters.mobimon.feature.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.SignalUnavailableReason
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleWarning
import com.monsters.mobimon.core.domain.WarningSeverity
import com.monsters.mobimon.core.presentation.parkingBadgeConfirmed
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonDimensions
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.R as CoreUiR

private val LocalVehicleDesignScale = compositionLocalOf { 1f }

/** Displays vehicle readings without owning quest or interaction commands. */
@Composable
fun VehicleInfoScreen(
    snapshot: VehicleSnapshot,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    friendId: String = "friend:mobi",
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
    selectedCards: List<String> = VehicleCardCatalog.defaultSlots.map { it.id },
    onCardSelectionConfirmed: (List<String>) -> Unit = {},
) {
    val readings = snapshot.toVehicleInfoUiState()
    val mood =
        if (readings.condition == VehicleCondition.CHECKED &&
            (
                snapshot.isCharging == null ||
                    snapshot.washerFluidLevel == null ||
                    readings.outsideTemperature == null ||
                    readings.isRaining == null ||
                    readings.attentionLevel == null
            )
        ) {
            VehicleMood.PARTIAL
        } else {
            readings.condition.mood()
        }
    val title = stringResource(R.string.vehicle_destination_title)
    var currentCards by remember(selectedCards) {
        mutableStateOf(
            selectedCards.takeIf { it.size == 6 && it.all { id -> VehicleCardCatalog.find(id) != null } }
                ?: VehicleCardCatalog.defaultSlots.map { it.id },
        )
    }
    var dialogSlot by remember { mutableStateOf<Int?>(null) }
    var draftCardId by remember { mutableStateOf<String?>(null) }

    fun openSelector(slot: Int) {
        dialogSlot = slot
        draftCardId = null
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(VehicleScreenBackground)
                .semantics { paneTitle = title },
    ) {
        val fontScale = LocalDensity.current.fontScale
        val reference = maxWidth >= 1400.dp && maxHeight >= 760.dp && fontScale <= 1.2f
        val scale = if (reference) maxWidth.value / 2560f else 0.75f
        val contentHeight = maxHeight

        if (reference) {
            CompositionLocalProvider(LocalVehicleDesignScale provides scale) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().testTag("vehicle-reference")) {
                        VehicleHeader(
                            snapshot = snapshot,
                            friendId = friendId,
                            onBack = onBack,
                            onHome = onHome,
                            scale = scale,
                            modifier =
                                Modifier
                                    .offset(72.dp * scale, 36.dp * scale)
                                    .size(2416.dp * scale, 104.dp * scale),
                        )
                        Column(
                            modifier =
                                Modifier
                                    .offset(72.dp * scale, 156.dp * scale)
                                    .size(2416.dp * scale, contentHeight - 172.dp * scale)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(28.dp * scale),
                        ) {
                            val metricsPanelHeight = 872.dp * scale
                            VehicleStatusBanner(snapshot, mood, friendId)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(metricsPanelHeight),
                                horizontalArrangement = Arrangement.spacedBy(28.dp * scale),
                                verticalAlignment = Alignment.Top,
                            ) {
                                CompanionStatusPanel(
                                    mood = mood,
                                    friendId = friendId,
                                    accessoryId = accessoryId,
                                    outfitId = outfitId,
                                    backgroundId = backgroundId,
                                    tireWarning =
                                        VehicleCardCatalog.status("tire", snapshot) == VehicleCardStatus.CAUTION,
                                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                                    panelHeight = metricsPanelHeight,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    VehicleCardGrid(
                                        snapshot = snapshot,
                                        readings = readings,
                                        cards = currentCards,
                                        onLongPress = ::openSelector,
                                        modifier = Modifier.fillMaxWidth(),
                                        targetHeight = 800.dp * scale,
                                    )
                                    Spacer(Modifier.height(30.dp * scale))
                                    Text(
                                        text = stringResource(R.string.vehicle_card_change_hint),
                                        color = MobiMonColors.muted,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.End).testTag("vehicle-card-change-hint"),
                                    )
                                }
                            }
                            if (snapshot.warnings.isNotEmpty()) WarningList(snapshot.warnings)
                        }
                    }
                }
            }
        } else {
            val compactScale = (maxWidth.value / 1400f).coerceIn(0.55f, 0.9f)
            val isWide = maxWidth >= 980.dp
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                VehicleHeader(
                    snapshot = snapshot,
                    friendId = friendId,
                    onBack = onBack,
                    onHome = onHome,
                    scale = compactScale,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    VehicleStatusBanner(snapshot, mood, friendId)
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CompanionStatusPanel(
                                mood = mood,
                                friendId = friendId,
                                accessoryId = accessoryId,
                                outfitId = outfitId,
                                backgroundId = backgroundId,
                                tireWarning = VehicleCardCatalog.status("tire", snapshot) == VehicleCardStatus.CAUTION,
                                modifier = Modifier.weight(0.4f),
                            )
                            VehicleCardGrid(
                                snapshot = snapshot,
                                readings = readings,
                                cards = currentCards,
                                onLongPress = ::openSelector,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        CompanionStatusPanel(
                            mood = mood,
                            friendId = friendId,
                            accessoryId = accessoryId,
                            outfitId = outfitId,
                            backgroundId = backgroundId,
                            tireWarning = VehicleCardCatalog.status("tire", snapshot) == VehicleCardStatus.CAUTION,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        VehicleCardGrid(
                            snapshot = snapshot,
                            readings = readings,
                            cards = currentCards,
                            onLongPress = ::openSelector,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = stringResource(R.string.vehicle_card_change_hint),
                        color = MobiMonColors.muted,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().testTag("vehicle-card-change-hint"),
                    )
                    if (snapshot.warnings.isNotEmpty()) WarningList(snapshot.warnings)
                }
            }
        }
        dialogSlot?.let { slot ->
            CompositionLocalProvider(LocalVehicleDesignScale provides if (reference) scale else 1f) {
                VehicleCardSelector(
                    snapshot = snapshot,
                    cards = currentCards,
                    selectedSlot = slot,
                    selectedCardId = draftCardId,
                    onSlotSelected = { next ->
                        dialogSlot = next
                        draftCardId = null
                    },
                    onCardSelected = { draftCardId = it },
                    onDismiss = { dialogSlot = null },
                    onConfirm = {
                        val chosen = draftCardId
                        if (chosen != null &&
                            chosen !in currentCards &&
                            VehicleCardCatalog.cards.any { it.id == chosen }
                        ) {
                            currentCards = currentCards.toMutableList().also { it[slot] = chosen }
                            onCardSelectionConfirmed(currentCards)
                        }
                        dialogSlot = null
                    },
                )
            }
        }
    }
}

@Composable
internal fun VehicleHeader(
    snapshot: VehicleSnapshot,
    friendId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttonSize = if (scale >= 0.7f) 104.dp * scale else MobiMonDimensions.touchTarget
        val iconSize = if (scale >= 0.7f) 40.dp * scale else 24.dp
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .size(buttonSize)
                    .background(VehiclePanelBackground, CircleShape)
                    .border(1.dp, MobiMonColors.border, CircleShape)
                    .testTag("vehicle-header-back-button"),
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.mobimon_icon_back),
                contentDescription = stringResource(CoreUiR.string.mobimon_back),
                tint = MobiMonColors.text,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(if (scale >= 0.7f) 32.dp * scale else 16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.vehicle_destination_title),
                color = MobiMonColors.text,
                fontSize = if (scale >= 0.7f) (46f * scale).sp else 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(
                        R.string.vehicle_header_subtitle,
                        stringResource(
                            if (friendId == "friend:luna") {
                                R.string.vehicle_companion_luna
                            } else {
                                R.string.vehicle_companion_mobi
                            },
                        ),
                    ),
                color = MobiMonColors.muted,
                fontSize = if (scale >= 0.7f) (28f * scale).sp else 14.sp,
            )
        }

        MobiMonParkingStatusBadge(
            confirmed = snapshot.parkingBadgeConfirmed,
            modifier = Modifier.align(Alignment.Top),
            scale = scale,
        )
    }
}

@Composable
private fun VehicleStatusBanner(
    snapshot: VehicleSnapshot,
    mood: VehicleMood,
    friendId: String,
    modifier: Modifier = Modifier,
) {
    val designScale = LocalVehicleDesignScale.current
    StatusSurface(
        background = mood.bannerBackground,
        border = mood.accent,
        modifier = modifier.fillMaxWidth().testTag("vehicle-status-banner"),
        contentPadding = PaddingValues(horizontal = 28.dp * designScale, vertical = 24.dp * designScale),
        corner = 20.dp * designScale,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mood == VehicleMood.WARNING &&
                VehicleCardCatalog.status("tire", snapshot) == VehicleCardStatus.CAUTION
            ) {
                BannerText(
                    title =
                        stringResource(R.string.vehicle_banner_sick_title).replace(
                            "모비",
                            if (friendId ==
                                "friend:luna"
                            ) {
                                "루나"
                            } else {
                                "모비"
                            },
                        ),
                    description = stringResource(R.string.vehicle_banner_sick_tire_desc),
                    accent = mood.accent,
                )
            } else if (mood == VehicleMood.ATTENTION &&
                (snapshot.batteryQuality ?: snapshot.quality) == SignalQuality.VALID &&
                snapshot.batteryPercent?.let { it in 0..19 } == true
            ) {
                BannerText(
                    title =
                        stringResource(R.string.vehicle_banner_low_battery_title).replace(
                            "모비",
                            if (friendId ==
                                "friend:luna"
                            ) {
                                "루나"
                            } else {
                                "모비"
                            },
                        ),
                    description = stringResource(R.string.vehicle_banner_low_battery_desc),
                    accent = mood.accent,
                )
            } else {
                BannerText(
                    title =
                        stringResource(mood.bannerTitleRes).replace(
                            "모비",
                            if (friendId ==
                                "friend:luna"
                            ) {
                                "루나"
                            } else {
                                "모비"
                            },
                        ),
                    description = stringResource(mood.bannerDescriptionRes),
                    accent = mood.accent,
                )
            }
            if (snapshot.quality == SignalQuality.STALE) {
                snapshot.parkingAgeMillis?.let {
                    Text(lastCheckedText(it), color = MobiMonColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BannerText(
    title: String,
    description: String,
    accent: Color,
) {
    val designScale = LocalVehicleDesignScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp * designScale),
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = (40f * designScale).sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "|",
            color = accent.copy(alpha = 0.55f),
            fontSize = (32f * designScale).sp,
        )
        Text(
            text = description,
            color = MobiMonColors.text,
            fontSize = (30f * designScale).sp,
        )
    }
}

@Composable
private fun CompanionStatusPanel(
    mood: VehicleMood,
    friendId: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    tireWarning: Boolean,
    modifier: Modifier = Modifier,
    panelHeight: Dp = VehiclePanelHeight,
) {
    val designScale = LocalVehicleDesignScale.current
    StatusSurface(
        background = VehiclePanelBackground,
        border = VehicleBorder,
        modifier = modifier.height(panelHeight),
        contentPadding = PaddingValues(horizontal = 28.dp * designScale, vertical = 32.dp * designScale),
        corner = 24.dp * designScale,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp * designScale),
        ) {
            PetAvatar(
                modifier =
                    Modifier.size(
                        if (designScale < 1f) {
                            624.dp * designScale
                        } else {
                            (panelHeight - 240.dp).coerceIn(220.dp, 624.dp)
                        },
                    ),
                friendId = friendId,
                accessoryId = accessoryId,
                outfitId = outfitId,
                backgroundId = backgroundId,
                vehicleWarning = mood == VehicleMood.WARNING,
                vehicleHungry = mood == VehicleMood.ATTENTION,
                artworkOverride =
                    when (friendId) {
                        "friend:mobi" ->
                            when (mood) {
                                VehicleMood.GOOD -> R.drawable.mobi_vehicle_normal
                                else -> null
                            }
                        "friend:luna" ->
                            when (mood) {
                                VehicleMood.GOOD -> R.drawable.luna_vehicle_normal
                                VehicleMood.ATTENTION -> R.drawable.luna_vehicle_hungry
                                VehicleMood.WARNING -> R.drawable.luna_vehicle_sick
                                else -> null
                            }
                        else -> null
                    },
            )
            StatusPill(
                text = stringResource(mood.badgeRes),
                foreground = mood.accent,
                background = mood.badgeBackground,
                border = mood.accent,
                modifier = Modifier.width(230.dp * designScale),
                centered = true,
            )
            Text(
                text =
                    stringResource(
                        if (mood == VehicleMood.WARNING && tireWarning) {
                            R.string.vehicle_mood_tire_companion
                        } else {
                            mood.companionTextRes
                        },
                    ).replace("모비", if (friendId == "friend:luna") "루나" else "모비"),
                color = MobiMonColors.text,
                fontSize = (32f * designScale).sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VehicleCardGrid(
    snapshot: VehicleSnapshot,
    readings: VehicleInfoUiState,
    cards: List<String>,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    targetHeight: Dp? = null,
) {
    val designScale = LocalVehicleDesignScale.current
    val fontScale = LocalDensity.current.fontScale
    val minimumCardWidth = 360.dp * designScale * fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier) {
        val columns =
            when {
                maxWidth >= minimumCardWidth * 3 + 48.dp * designScale -> 3
                maxWidth >= minimumCardWidth * 2 + 24.dp * designScale -> 2
                else -> 1
            }
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp * designScale),
        ) {
            val rows = cards.chunked(columns)
            val rowHeight =
                targetHeight?.let {
                    ((it - 24.dp * designScale * (rows.size - 1)) / rows.size)
                        .coerceAtLeast(VehicleCardHeight * designScale)
                } ?: (VehicleCardHeight * fontScale.coerceAtLeast(1f))
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(24.dp * designScale),
                ) {
                    row.forEachIndexed { columnIndex, cardId ->
                        val slot = rowIndex * columns + columnIndex
                        VehicleCard(
                            cardId = cardId,
                            snapshot = snapshot,
                            readings = readings,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("vehicle-card-slot-${slot + 1}")
                                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(slot) }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(
    cardId: String,
    snapshot: VehicleSnapshot,
    readings: VehicleInfoUiState,
    modifier: Modifier = Modifier,
) {
    val status = VehicleCardCatalog.status(cardId, snapshot) ?: return
    when (cardId) {
        "battery" -> BatteryCard(snapshot, readings.batteryPercent, status, modifier)
        "tire" -> TireCard(readings, status, modifier)
        "environment" -> EnvironmentCard(readings, status, modifier)
        "assist" -> DriverAssistCard(readings, status, modifier)
        "charging" -> {
            val current = snapshot.takeIf { it.quality == SignalQuality.VALID }?.isCharging
            MetricCard(
                title = stringResource(R.string.vehicle_charging_card_title),
                value =
                    current?.let {
                        if (it) {
                            stringResource(
                                R.string.vehicle_charging_active,
                            )
                        } else {
                            stringResource(R.string.vehicle_charging_idle)
                        }
                    }
                        ?: stringResource(R.string.vehicle_unknown_short),
                supporting =
                    if (current == null) {
                        stringResource(R.string.vehicle_charging_unavailable)
                    } else {
                        stringResource(
                            if (current) {
                                R.string.vehicle_charging_active_detail
                            } else {
                                R.string.vehicle_charging_idle_detail
                            },
                        )
                    },
                badge = stringResource(status.labelRes),
                badgeTone = status.tone,
                modifier = modifier,
                testTag = "vehicle-card-charging",
            )
        }
        "washer" -> {
            val level = snapshot.takeIf { it.quality == SignalQuality.VALID }?.washerFluidLevel?.takeIf { it in 0..100 }
            MetricCard(
                title = stringResource(R.string.vehicle_washer_card_title),
                value = level?.let { "$it%" } ?: stringResource(R.string.vehicle_unknown_short),
                supporting =
                    if (level != null) {
                        stringResource(R.string.vehicle_washer_detail)
                    } else {
                        stringResource(R.string.vehicle_washer_unavailable)
                    },
                badge = stringResource(status.labelRes),
                badgeTone = status.tone,
                modifier = modifier,
                testTag = "vehicle-card-washer",
            )
        }
        else -> {
            val spec = VehicleCardCatalog.find(cardId) ?: return
            val reading = VehicleCardCatalog.reading(cardId, snapshot) ?: return
            MetricCard(
                title = spec.title,
                value = reading.value ?: stringResource(R.string.vehicle_unknown_short),
                supporting =
                    if (reading.value ==
                        null
                    ) {
                        stringResource(R.string.vehicle_card_signal_unavailable)
                    } else {
                        reading.supporting
                    },
                badge = stringResource(status.labelRes),
                badgeTone = status.tone,
                modifier = modifier,
                testTag = "vehicle-card-$cardId",
            )
        }
    }
}

@Composable
private fun VehicleCardSelector(
    snapshot: VehicleSnapshot,
    cards: List<String>,
    selectedSlot: Int,
    selectedCardId: String?,
    onSlotSelected: (Int) -> Unit,
    onCardSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val readings = snapshot.toVehicleInfoUiState()
    val designScale = LocalVehicleDesignScale.current
    val availableCards = VehicleCardCatalog.cards.filterNot { it.id in cards }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .testTag("vehicle-card-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 1200.dp
        val choiceColumns =
            if (wide) {
                3
            } else if (maxWidth >= 650.dp) {
                2
            } else {
                1
            }
        val dialogWidth =
            if (wide) {
                (1900.dp * designScale).coerceAtMost(maxWidth - 32.dp)
            } else {
                maxWidth - 32.dp
            }
        val dialogHeight = (1080.dp * designScale).coerceAtMost(maxHeight - 32.dp)
        Surface(
            modifier = Modifier.width(dialogWidth).height(dialogHeight).testTag("vehicle-card-selector"),
            color = VehicleScreenBackground,
            contentColor = MobiMonColors.text,
            shape = RoundedCornerShape(32.dp * designScale),
            border = BorderStroke(2.dp, VehicleBorder),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(if (wide) 48.dp * designScale else 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp * designScale),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vehicle_card_selector_title),
                            color = MobiMonColors.text,
                            fontSize = (48f * designScale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.vehicle_card_selector_description, selectedSlot + 1),
                            color = MobiMonColors.muted,
                            fontSize = (28f * designScale).sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("×", color = MobiMonColors.text, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * designScale)) {
                    cards.forEachIndexed { index, id ->
                        val active = index == selectedSlot
                        Surface(
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    ).height(72.dp * designScale)
                                    .clickable { onSlotSelected(index) }
                                    .testTag("vehicle-dialog-slot-${index + 1}"),
                            color = if (active) NeutralBadgeBackground else VehiclePanelBackground,
                            border =
                                BorderStroke(
                                    if (active) 3.dp else 1.dp,
                                    if (active) MobiMonColors.accent else VehicleBorder,
                                ),
                            shape = RoundedCornerShape(16.dp * designScale),
                        ) {
                            Row(
                                Modifier.padding(8.dp * designScale),
                                horizontalArrangement = Arrangement.spacedBy(12.dp * designScale),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = MobiMonColors.accent,
                                    fontSize = (24f * designScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .background(
                                                NeutralBadgeBackground,
                                                RoundedCornerShape(10.dp * designScale),
                                            ).padding(horizontal = 12.dp * designScale, vertical = 6.dp * designScale),
                                )
                                Text(
                                    VehicleCardCatalog.find(id)?.title.orEmpty(),
                                    color = MobiMonColors.text,
                                    fontSize = (22f * designScale).sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.vehicle_card_selector_available),
                        color = MobiMonColors.text,
                        fontSize = (30f * designScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.vehicle_card_selector_count, availableCards.size),
                        color = MobiMonColors.muted,
                        fontSize = (24f * designScale).sp,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(choiceColumns),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (wide) {
                                    Modifier.padding(
                                        start = 42.dp * designScale,
                                        end = 76.dp * designScale,
                                    )
                                } else {
                                    Modifier
                                },
                            ).testTag("vehicle-dialog-list"),
                    horizontalArrangement = Arrangement.spacedBy(24.dp * designScale),
                    verticalArrangement = Arrangement.spacedBy(20.dp * designScale),
                ) {
                    itemsIndexed(availableCards, key = { _, card -> card.id }) { index, card ->
                        val selected = selectedCardId == card.id
                        Box(
                            Modifier
                                .height(if (wide) 388.dp * designScale else VehicleCardHeight)
                                .clickable { onCardSelected(card.id) }
                                .testTag("vehicle-dialog-option-${card.id}"),
                        ) {
                            VehicleCard(card.id, snapshot, readings, Modifier.fillMaxSize())
                            if (selected) {
                                Box(
                                    Modifier.matchParentSize().border(
                                        4.dp,
                                        MobiMonColors.accent,
                                        RoundedCornerShape(24.dp),
                                    ),
                                )
                            }
                            Text(
                                "${index + 1}",
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .background(
                                            VehicleScreenBackground.copy(alpha = 0.72f),
                                            RoundedCornerShape(
                                                16.dp * designScale,
                                            ),
                                        ).padding(horizontal = 12.dp * designScale, vertical = 4.dp * designScale),
                                color = MobiMonColors.text,
                                fontSize = (22f * designScale).sp,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            selectedCardId?.let { id ->
                                stringResource(
                                    R.string.vehicle_card_selector_selected,
                                    selectedSlot + 1,
                                    VehicleCardCatalog.find(id)?.title.orEmpty(),
                                )
                            } ?: stringResource(R.string.vehicle_card_selector_choose),
                        color = MobiMonColors.muted,
                        fontSize = (26f * designScale).sp,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .then(
                                    if (wide) {
                                        Modifier.width(172.dp * designScale).height(
                                            68.dp * designScale,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).testTag("vehicle-dialog-cancel"),
                    ) {
                        Text(stringResource(R.string.vehicle_card_selector_cancel))
                    }
                    Spacer(Modifier.width(16.dp * designScale))
                    Button(
                        onClick = onConfirm,
                        enabled = availableCards.any { it.id == selectedCardId },
                        modifier =
                            Modifier
                                .then(
                                    if (wide) {
                                        Modifier.width(280.dp * designScale).height(
                                            68.dp * designScale,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).testTag("vehicle-dialog-confirm"),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MobiMonColors.accent,
                                contentColor = VehicleScreenBackground,
                            ),
                    ) {
                        Text(stringResource(R.string.vehicle_card_selector_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(
    snapshot: VehicleSnapshot,
    battery: Int?,
    status: VehicleCardStatus,
    modifier: Modifier = Modifier,
) {
    val quality = snapshot.batteryQuality ?: snapshot.quality
    val statusText = batteryStatusText(snapshot)
    val warning = battery != null && battery < 20 || quality != SignalQuality.VALID
    MetricCard(
        title = stringResource(R.string.vehicle_battery_card_title),
        value = battery?.let { "$it%" } ?: stringResource(R.string.vehicle_unknown_short),
        supporting = battery?.let { stringResource(R.string.vehicle_battery, it) } ?: statusText,
        modifier = modifier,
        testTag = "vehicle-card-battery",
        badge = stringResource(status.labelRes),
        badgeTone = status.tone,
    ) {
        if (quality == SignalQuality.STALE) {
            snapshot.batteryAgeMillis?.let {
                Text(
                    text = lastCheckedText(it),
                    color = MobiMonColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            BatteryBar(percent = battery ?: 0, tone = if (warning) VehicleTone.WARNING else VehicleTone.SUCCESS)
        }
    }
}

@Composable
private fun DrivingCard(
    snapshot: VehicleSnapshot,
    modifier: Modifier = Modifier,
) {
    val drivingText =
        stringResource(snapshot.drivingStatusTextRes())
    MetricCard(
        title = stringResource(R.string.vehicle_driving_card_title),
        value = drivingText,
        testTag = "vehicle-card-driving",
        supporting = parkingSupportingText(snapshot),
        modifier = modifier,
        badge =
            stringResource(
                when (snapshot.quality) {
                    SignalQuality.VALID -> R.string.vehicle_quality_valid_short
                    SignalQuality.STALE -> R.string.vehicle_quality_stale_short
                    SignalQuality.UNAVAILABLE -> R.string.vehicle_quality_unavailable_short
                },
            ),
        badgeTone = if (snapshot.quality == SignalQuality.VALID) VehicleTone.SUCCESS else VehicleTone.WARNING,
    ) {
        if (snapshot.quality == SignalQuality.STALE) {
            snapshot.parkingAgeMillis?.let {
                Text(
                    text = lastCheckedText(it),
                    color = MobiMonColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TireCard(
    readings: VehicleInfoUiState,
    status: VehicleCardStatus,
    modifier: Modifier = Modifier,
) {
    val warning = readings.tireWarning
    val low = status == VehicleCardStatus.CAUTION
    MetricCard(
        title = stringResource(R.string.vehicle_tire_card_title),
        value =
            when {
                low -> stringResource(R.string.vehicle_tire_low)
                readings.tireStatus == "OK" -> stringResource(R.string.vehicle_tire_normal)
                else -> readings.tireStatus ?: stringResource(R.string.vehicle_unknown_short)
            },
        supporting =
            warning?.description ?: stringResource(
                if (low) {
                    R.string.vehicle_tire_low_detail
                } else {
                    if (readings.tireStatus == null) {
                        R.string.vehicle_tire_unavailable
                    } else {
                        R.string.vehicle_tire_checked
                    }
                },
            ),
        modifier = modifier,
        testTag = "vehicle-card-tire",
        badge = stringResource(status.labelRes),
        badgeTone = status.tone,
    )
}

@Composable
private fun EnvironmentCard(
    readings: VehicleInfoUiState,
    status: VehicleCardStatus,
    modifier: Modifier = Modifier,
) {
    val temperature = readings.outsideTemperature?.let { stringResource(R.string.vehicle_temperature_value, it) }
    val rainText =
        when (readings.isRaining) {
            true -> stringResource(R.string.vehicle_raining)
            false -> stringResource(R.string.vehicle_not_raining)
            null -> stringResource(R.string.vehicle_weather_unknown)
        }
    MetricCard(
        title = stringResource(R.string.vehicle_environment_card_title),
        value = temperature ?: stringResource(R.string.vehicle_unknown_short),
        supporting = rainText,
        badge = stringResource(status.labelRes),
        badgeTone = status.tone,
        modifier = modifier,
        testTag = "vehicle-card-environment",
    )
}

@Composable
private fun DriverAssistCard(
    readings: VehicleInfoUiState,
    status: VehicleCardStatus,
    modifier: Modifier = Modifier,
) {
    val issue =
        when (readings.assistWarning) {
            DriverAssistWarning.EMERGENCY_BRAKING -> stringResource(R.string.vehicle_emergency_braking)
            DriverAssistWarning.DROWSY -> stringResource(R.string.vehicle_drowsy)
            DriverAssistWarning.DISTRACTED -> stringResource(R.string.vehicle_distracted)
            null -> {
                when {
                    readings.frontDistance != null ->
                        stringResource(
                            R.string.vehicle_front_distance,
                            readings.frontDistance,
                        )
                    readings.assistChecked -> stringResource(R.string.vehicle_assist_no_issue)
                    else -> stringResource(R.string.vehicle_assist_unavailable)
                }
            }
        }
    MetricCard(
        title = stringResource(R.string.vehicle_assist_card_title),
        value = readings.attentionLevel?.let { "$it" } ?: stringResource(R.string.vehicle_unknown_short),
        supporting = issue,
        modifier = modifier,
        testTag = "vehicle-card-assist",
        badge = stringResource(status.labelRes),
        badgeTone = status.tone,
    )
}

@Composable
private fun ConnectionCard(
    snapshot: VehicleSnapshot,
    modifier: Modifier = Modifier,
) {
    MetricCard(
        title = stringResource(R.string.vehicle_connection_card_title),
        value =
            stringResource(
                when (snapshot.quality) {
                    SignalQuality.VALID -> R.string.vehicle_connection_live
                    SignalQuality.STALE -> R.string.vehicle_connection_stale
                    SignalQuality.UNAVAILABLE -> R.string.vehicle_connection_unavailable
                },
            ),
        supporting =
            if (snapshot.source == SignalSource.SIMULATED) {
                stringResource(R.string.vehicle_source_simulated)
            } else {
                stringResource(R.string.vehicle_source_real)
            },
        modifier = modifier,
        testTag = "vehicle-card-connection",
        badge =
            if (snapshot.source == SignalSource.SIMULATED) {
                stringResource(R.string.vehicle_source_simulated_badge)
            } else {
                stringResource(R.string.vehicle_source_real_badge)
            },
        badgeTone = if (snapshot.quality == SignalQuality.VALID) VehicleTone.SUCCESS else VehicleTone.WARNING,
    )
}

@Composable
private fun WarningList(warnings: List<VehicleWarning>) {
    StatusSurface(
        background = VehiclePanelBackground,
        border = VehicleBorder,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(28.dp),
        corner = 24.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = stringResource(R.string.vehicle_warnings_title),
                color = MobiMonColors.muted,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            warnings.forEach { warning ->
                WarningRow(warning)
            }
        }
    }
}

@Composable
private fun WarningRow(warning: VehicleWarning) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text =
                stringResource(
                    if (warning.quality == SignalQuality.VALID) {
                        R.string.vehicle_warning_current
                    } else {
                        R.string.vehicle_warning_previous
                    },
                    stringResource(
                        when (warning.severity) {
                            WarningSeverity.NOTICE -> R.string.vehicle_warning_notice
                            WarningSeverity.CAUTION -> R.string.vehicle_warning_caution
                            WarningSeverity.CRITICAL -> R.string.vehicle_warning_critical
                        },
                    ),
                    warning.item,
                ),
            color = if (warning.quality == SignalQuality.VALID) WarningAccent else MobiMonColors.muted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        warning.location?.let {
            Text(text = it, color = MobiMonColors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = warning.description, color = MobiMonColors.text, style = MaterialTheme.typography.bodyLarge)
        if (warning.quality == SignalQuality.VALID) {
            Text(text = warning.nextAction, color = MobiMonColors.warning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    badge: String? = null,
    badgeTone: VehicleTone = VehicleTone.NEUTRAL,
    valueContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val designScale = LocalVehicleDesignScale.current
    StatusSurface(
        background = VehiclePanelBackground,
        border = VehicleBorder,
        modifier =
            modifier
                .heightIn(min = VehicleCardHeight * designScale)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentPadding = PaddingValues(32.dp * designScale),
        corner = 24.dp * designScale,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val figmaLayout =
                maxHeight >= 300.dp * designScale &&
                    maxWidth >= 400.dp * designScale &&
                    LocalDensity.current.fontScale <= 1.2f
            if (figmaLayout) {
                Text(
                    text = title,
                    modifier = Modifier.align(Alignment.TopStart).offset(y = (-4).dp * designScale),
                    color = MobiMonColors.muted,
                    fontSize = (30f * designScale).sp,
                    fontWeight = FontWeight.Bold,
                )
                badge?.let {
                    StatusPill(
                        text = it,
                        foreground = badgeTone.foreground,
                        background = badgeTone.background,
                        border = badgeTone.border,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (-4).dp * designScale)
                                .then(if (testTag != null) Modifier.testTag("$testTag-status") else Modifier),
                    )
                }
                if (valueContent != null) {
                    Box(Modifier.offset(y = 78.dp * designScale)) { valueContent() }
                } else {
                    Text(
                        text = value,
                        color = MobiMonColors.text,
                        fontSize = (58f * designScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = 78.dp * designScale),
                    )
                }
                Text(
                    text = supporting,
                    color = MobiMonColors.muted,
                    fontSize = (28f * designScale).sp,
                    modifier = Modifier.offset(y = 196.dp * designScale),
                )
                Column(
                    Modifier.offset(y = 314.dp * designScale),
                    verticalArrangement = Arrangement.spacedBy(8.dp * designScale),
                ) { content() }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            color = MobiMonColors.muted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        badge?.let {
                            StatusPill(
                                text = it,
                                foreground = badgeTone.foreground,
                                background = badgeTone.background,
                                border = badgeTone.border,
                                modifier = if (testTag != null) Modifier.testTag("$testTag-status") else Modifier,
                            )
                        }
                    }
                    if (valueContent != null) {
                        valueContent()
                    } else {
                        Text(
                            text = value,
                            color = MobiMonColors.text,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = supporting,
                        color = MobiMonColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun BatteryBar(
    percent: Int,
    tone: VehicleTone,
) {
    val designScale = LocalVehicleDesignScale.current
    val clamped = percent.coerceIn(0, 100)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(16.dp * designScale)
                .clip(RoundedCornerShape(8.dp * designScale))
                .background(MobiMonColors.raised),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(clamped / 100f)
                    .height(16.dp * designScale)
                    .clip(RoundedCornerShape(8.dp * designScale))
                    .background(tone.foreground),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    foreground: Color,
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val designScale = LocalVehicleDesignScale.current
    Surface(
        modifier = modifier,
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(28.dp * designScale),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            modifier =
                Modifier
                    .then(if (centered) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = 18.dp * designScale, vertical = 8.dp * designScale),
            fontSize = (24f * designScale).sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun StatusSurface(
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    corner: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = background,
        contentColor = MobiMonColors.text,
        shape = RoundedCornerShape(corner),
        border =
            if (border == Color.Transparent) {
                null
            } else {
                BorderStroke(VehicleBorderWidth, border)
            },
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun batteryStatusText(snapshot: VehicleSnapshot): String =
    when {
        (snapshot.batteryQuality ?: snapshot.quality) == SignalQuality.STALE -> {
            stringResource(R.string.vehicle_battery_stale)
        }
        snapshot.batteryUnavailableReason == SignalUnavailableReason.UNSUPPORTED -> {
            stringResource(R.string.vehicle_battery_unsupported)
        }
        snapshot.batteryUnavailableReason == SignalUnavailableReason.PERMISSION_DENIED -> {
            stringResource(R.string.vehicle_battery_permission)
        }
        snapshot.batteryUnavailableReason == SignalUnavailableReason.DISCONNECTED -> {
            stringResource(R.string.vehicle_battery_disconnected)
        }
        else -> stringResource(R.string.vehicle_battery_unavailable)
    }

@Composable
private fun parkingSupportingText(snapshot: VehicleSnapshot): String =
    when {
        snapshot.quality == SignalQuality.VALID -> stringResource(R.string.vehicle_quality_valid)
        snapshot.quality == SignalQuality.STALE -> stringResource(R.string.vehicle_quality_stale)
        snapshot.parkingUnavailableReason != null -> {
            val reason = snapshot.parkingUnavailableReason ?: SignalUnavailableReason.NOT_REPORTED
            stringResource(reason.description())
        }
        else -> stringResource(R.string.vehicle_quality_unavailable)
    }

private fun VehicleCondition.mood(): VehicleMood =
    when (this) {
        VehicleCondition.CHECKED -> VehicleMood.GOOD
        VehicleCondition.PARTIAL -> VehicleMood.PARTIAL
        VehicleCondition.LOW_BATTERY -> VehicleMood.ATTENTION
        VehicleCondition.WARNING -> VehicleMood.WARNING
        VehicleCondition.STALE -> VehicleMood.STALE
        VehicleCondition.UNAVAILABLE -> VehicleMood.UNKNOWN
    }

private fun VehicleSnapshot.drivingStatusTextRes(): Int =
    when {
        quality != SignalQuality.VALID || drivingState == DrivingState.UNKNOWN ->
            CoreUiR.string.mobimon_parking_unconfirmed
        drivingState == DrivingState.MOVING -> R.string.vehicle_driving_moving
        else -> CoreUiR.string.mobimon_parking_confirmed
    }

private enum class VehicleMood(
    val badgeRes: Int,
    val companionTextRes: Int,
    val bannerTitleRes: Int,
    val bannerDescriptionRes: Int,
    val accent: Color,
    val bannerBackground: Color,
    val badgeBackground: Color,
) {
    GOOD(
        R.string.vehicle_mood_good_badge,
        R.string.vehicle_mood_good_companion,
        R.string.vehicle_banner_good_title,
        R.string.vehicle_banner_good_desc,
        SuccessAccent,
        SuccessBackground,
        SuccessBadgeBackground,
    ),
    PARTIAL(
        R.string.vehicle_mood_partial_badge,
        R.string.vehicle_mood_partial_companion,
        R.string.vehicle_banner_partial_title,
        R.string.vehicle_banner_partial_desc,
        MobiMonColors.accent,
        NeutralBackground,
        NeutralBadgeBackground,
    ),
    ATTENTION(
        R.string.vehicle_mood_hungry_badge,
        R.string.vehicle_mood_hungry_companion,
        R.string.vehicle_banner_low_battery_title,
        R.string.vehicle_banner_hungry_desc,
        WarningAccent,
        WarningBackground,
        WarningBadgeBackground,
    ),
    WARNING(
        R.string.vehicle_mood_warning_badge,
        R.string.vehicle_mood_warning_companion,
        R.string.vehicle_banner_warning_title,
        R.string.vehicle_banner_warning_desc,
        WarningAccent,
        WarningBackground,
        WarningBadgeBackground,
    ),
    STALE(
        R.string.vehicle_mood_stale_badge,
        R.string.vehicle_mood_stale_companion,
        R.string.vehicle_banner_stale_title,
        R.string.vehicle_banner_stale_desc,
        WarningAccent,
        WarningBackground,
        WarningBadgeBackground,
    ),
    UNKNOWN(
        R.string.vehicle_mood_unknown_badge,
        R.string.vehicle_mood_unknown_companion,
        R.string.vehicle_banner_unknown_title,
        R.string.vehicle_banner_unknown_desc,
        MobiMonColors.accent,
        NeutralBackground,
        NeutralBadgeBackground,
    ),
}

private enum class VehicleTone(
    val foreground: Color,
    val background: Color,
    val border: Color,
) {
    SUCCESS(SuccessAccent, SuccessBadgeBackground, SuccessAccent),
    WARNING(WarningAccent, WarningBadgeBackground, WarningAccent),
    NEUTRAL(MobiMonColors.accent, NeutralBadgeBackground, MobiMonColors.border),
}

private val VehicleCardStatus.labelRes: Int
    get() =
        when (this) {
            VehicleCardStatus.INFO -> R.string.vehicle_card_status_info
            VehicleCardStatus.NORMAL -> R.string.vehicle_card_status_normal
            VehicleCardStatus.CAUTION -> R.string.vehicle_card_status_caution
            VehicleCardStatus.UNAVAILABLE -> R.string.vehicle_card_status_unavailable
        }

private val VehicleCardStatus.tone: VehicleTone
    get() =
        when (this) {
            VehicleCardStatus.INFO, VehicleCardStatus.UNAVAILABLE -> VehicleTone.NEUTRAL
            VehicleCardStatus.NORMAL -> VehicleTone.SUCCESS
            VehicleCardStatus.CAUTION -> VehicleTone.WARNING
        }

private val SuccessAccent = Color(0xFF71E5C5)
private val SuccessBackground = Color(0xFF102B28)
private val SuccessBadgeBackground = Color(0xFF123733)
private val WarningAccent = Color(0xFFFBBF24)
private val WarningBackground = Color(0xFF251E14)
private val WarningBadgeBackground = Color(0xFF2E2213)
private val NeutralBackground = Color(0xFF10243A)
private val NeutralBadgeBackground = Color(0xFF142A42)
internal val VehicleScreenBackground = MobiMonColors.background
private val VehiclePanelBackground = Color(0xFF142A42)
private val VehicleBorder = Color(0xFF2A4968)
private val VehicleBorderWidth = 2.dp
private val VehicleCardHeight = 260.dp
private val VehiclePanelHeight = 544.dp

@Composable
private fun lastCheckedText(ageMillis: Long): String {
    val seconds = ageMillis / 1_000
    return when {
        seconds < 60 -> stringResource(R.string.vehicle_last_checked_seconds, seconds)
        seconds < 3_600 -> stringResource(R.string.vehicle_last_checked_minutes, seconds / 60)
        seconds < 86_400 -> stringResource(R.string.vehicle_last_checked_hours, seconds / 3_600)
        else -> stringResource(R.string.vehicle_last_checked_days, seconds / 86_400)
    }
}

private fun SignalUnavailableReason.description(): Int =
    when (this) {
        SignalUnavailableReason.UNSUPPORTED -> R.string.vehicle_parking_unsupported
        SignalUnavailableReason.PERMISSION_DENIED -> R.string.vehicle_parking_permission
        SignalUnavailableReason.DISCONNECTED -> R.string.vehicle_parking_disconnected
        SignalUnavailableReason.NOT_REPORTED, SignalUnavailableReason.INVALID -> R.string.vehicle_parking_unavailable
    }
