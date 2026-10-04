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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
import com.monsters.mobimon.core.ui.MobiMonFontFamily
import com.monsters.mobimon.core.ui.MobiMonParkingInterruption
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
    parkingRequired: Boolean = false,
) {
    val readings = snapshot.toVehicleInfoUiState()
    val mood =
        if (readings.condition == VehicleCondition.CHECKED &&
            VehicleCardCatalog.defaultSlots.any {
                VehicleCardCatalog.status(it.id, snapshot) ==
                    VehicleCardStatus.UNAVAILABLE
            }
        ) {
            VehicleMood.PARTIAL
        } else {
            readings.condition.mood()
        }
    val title = stringResource(R.string.vehicle_destination_title)
    var currentCards by remember(selectedCards) {
        mutableStateOf(
            VehicleCardSelectionStore.validOrDefaults(selectedCards),
        )
    }
    var dialogSlot by remember { mutableStateOf<Int?>(null) }
    var draftCardId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(parkingRequired) {
        if (parkingRequired) {
            dialogSlot = null
            draftCardId = null
        }
    }

    fun openSelector(slot: Int) {
        if (parkingRequired) return
        dialogSlot = slot
        draftCardId = VehicleCardCatalog.cards.firstOrNull { it.id !in currentCards }?.id
    }

    ProvideTextStyle(LocalTextStyle.current.copy(letterSpacing = 0.sp)) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(VehicleScreenBackground)
                        .focusProperties { canFocus = !parkingRequired }
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
                                    onEditCards = { openSelector(0) },
                                    scale = scale,
                                    modifier =
                                        Modifier
                                            .offset(72.dp * scale, 36.dp * scale)
                                            .size(2416.dp * scale, 104.dp * scale),
                                )
                                Column(
                                    modifier =
                                        Modifier
                                            .offset(72.dp * scale, 172.dp * scale)
                                            .size(2416.dp * scale, contentHeight - 188.dp * scale)
                                            .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(32.dp * scale),
                                ) {
                                    val metricsPanelHeight = 852.dp * scale
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
                                                VehicleCardCatalog.status(
                                                    "tire",
                                                    snapshot,
                                                ) == VehicleCardStatus.CAUTION,
                                            modifier = Modifier.width(680.dp * scale).fillMaxHeight(),
                                            panelHeight = metricsPanelHeight,
                                        )
                                        VehicleCardGrid(
                                            snapshot = snapshot,
                                            readings = readings,
                                            cards = currentCards,
                                            onLongPress = ::openSelector,
                                            modifier = Modifier.weight(1f),
                                            targetHeight = metricsPanelHeight,
                                        )
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
                            onEditCards = { openSelector(0) },
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
                                        tireWarning =
                                            VehicleCardCatalog.status("tire", snapshot) ==
                                                VehicleCardStatus.CAUTION,
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
                                    tireWarning =
                                        VehicleCardCatalog.status("tire", snapshot) ==
                                            VehicleCardStatus.CAUTION,
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
                            if (snapshot.warnings.isNotEmpty()) WarningList(snapshot.warnings)
                        }
                    }
                }
                if (!parkingRequired) {
                    dialogSlot?.let { slot ->
                        CompositionLocalProvider(LocalVehicleDesignScale provides if (reference) scale else 1f) {
                            VehicleCardSelector(
                                snapshot = snapshot,
                                cards = currentCards,
                                selectedSlot = slot,
                                selectedCardId = draftCardId,
                                onSlotSelected = { next ->
                                    dialogSlot = next
                                    draftCardId = VehicleCardCatalog.cards.firstOrNull { it.id !in currentCards }?.id
                                },
                                onCardSelected = { draftCardId = it },
                                onDismiss = { dialogSlot = null },
                                onConfirm = {
                                    val chosen = draftCardId
                                    if (!parkingRequired &&
                                        chosen != null &&
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
            if (parkingRequired) {
                MobiMonParkingInterruption(
                    title = stringResource(R.string.vehicle_parking_popup_title),
                    body = stringResource(R.string.vehicle_parking_popup_body),
                    instruction = stringResource(R.string.vehicle_parking_popup_instruction),
                    preserved = stringResource(R.string.vehicle_parking_popup_preserved),
                    onHome = onHome ?: onBack,
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
    onEditCards: () -> Unit,
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
                modifier = Modifier.size(iconSize * 0.67f),
            )
        }
        Spacer(Modifier.width(if (scale >= 0.7f) 32.dp * scale else 16.dp))

        Column(modifier = Modifier.weight(1f).offset(y = (-9).dp * scale)) {
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
                            when (friendId) {
                                "friend:luna" -> R.string.vehicle_companion_luna
                                "friend:las" -> R.string.vehicle_companion_las
                                else -> R.string.vehicle_companion_mobi
                            },
                        ),
                    ),
                color = MobiMonColors.muted,
                fontSize = if (scale >= 0.7f) (28f * scale).sp else 14.sp,
            )
        }

        Surface(
            modifier =
                Modifier
                    .size(280.dp * scale, if (scale >= 0.7f) 76.dp * scale else MobiMonDimensions.touchTarget)
                    .align(Alignment.Top)
                    .clickable(onClick = onEditCards)
                    .testTag("vehicle-card-edit-button"),
            color = VehiclePanelBackground,
            contentColor = MobiMonColors.text,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp * scale, Color(0xFF64839F)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp * scale, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.vehicle_icon_edit),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp * scale),
                    tint = MobiMonColors.text,
                )
                Text(stringResource(R.string.vehicle_card_edit), fontSize = (30f * scale).sp)
            }
        }
        Spacer(Modifier.width(32.dp * scale))

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
    val tireWarning =
        mood == VehicleMood.WARNING && VehicleCardCatalog.status("tire", snapshot) == VehicleCardStatus.CAUTION
    val lowBattery =
        mood == VehicleMood.ATTENTION &&
            (snapshot.batteryQuality ?: snapshot.quality) == SignalQuality.VALID &&
            snapshot.batteryPercent?.let { it in 0..19 } == true
    val title =
        stringResource(
            when {
                tireWarning -> R.string.vehicle_banner_sick_title
                lowBattery -> R.string.vehicle_banner_low_battery_title
                else -> mood.bannerTitleRes
            },
        ).replace("모비", vehicleCompanionName(friendId))
    val description =
        if (tireWarning) {
            snapshot.warnings
                .firstOrNull { it.item.contains("타이어") && it.quality == SignalQuality.VALID }
                ?.description ?: stringResource(R.string.vehicle_banner_sick_tire_desc)
        } else {
            stringResource(if (lowBattery) R.string.vehicle_banner_low_battery_desc else mood.bannerDescriptionRes)
        }.replace("모비", vehicleCompanionName(friendId))
    StatusSurface(
        background = mood.bannerBackground,
        border = mood.accent,
        modifier = modifier.fillMaxWidth().heightIn(min = 104.dp * designScale).testTag("vehicle-status-banner"),
        contentPadding = PaddingValues(horizontal = 32.dp * designScale, vertical = 22.dp * designScale),
        corner = 20.dp * designScale,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp * designScale)) {
            BannerText(
                title = title,
                description = description,
                accent = mood.accent,
                icon =
                    if (lowBattery) {
                        R.drawable.vehicle_icon_summary_battery
                    } else if (tireWarning) {
                        R.drawable.vehicle_icon_summary_tire
                    } else {
                        R.drawable.vehicle_icon_summary_check
                    },
            )
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
    icon: Int,
) {
    val designScale = LocalVehicleDesignScale.current
    BoxWithConstraints {
        if (maxWidth < 900.dp * designScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(icon), null, Modifier.size(36.dp), tint = accent)
                    Text(title, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(description, color = MobiMonColors.text, fontSize = 22.sp)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp * designScale),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp * designScale),
                    tint = accent,
                )
                Text(
                    text = title,
                    modifier = Modifier.offset(y = 3.dp * designScale),
                    color = accent,
                    fontSize = (38f * designScale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp * designScale))
                Text(
                    text = description,
                    modifier = Modifier.offset(y = 2.dp * designScale),
                    color = MobiMonColors.text,
                    fontSize = (30f * designScale).sp,
                )
            }
        }
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
    val compact = panelHeight < 700.dp * designScale
    StatusSurface(
        background = VehiclePanelBackground,
        border = Color.Transparent,
        modifier = modifier.height(panelHeight),
        contentPadding = PaddingValues(0.dp),
        corner = 44.dp * designScale,
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.vehicle_companion_overview_title),
                color = MobiMonColors.text,
                fontSize = (if (compact) 30f else 40f).times(designScale).sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.align(Alignment.TopCenter).padding(
                        top =
                            (if (compact) 28.dp else 26.dp) * designScale,
                    ),
            )
            PetAvatar(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (if (compact) 88.dp else 100.dp) * designScale)
                        .size(if (compact) panelHeight - 200.dp * designScale else 624.dp * designScale),
                friendId = friendId,
                accessoryId = accessoryId,
                outfitId = outfitId,
                backgroundId = backgroundId,
                vehicleWarning = mood == VehicleMood.WARNING,
                vehicleHungry = mood == VehicleMood.ATTENTION,
            )
            Text(
                text =
                    stringResource(
                        if (mood == VehicleMood.WARNING && tireWarning) {
                            R.string.vehicle_mood_tire_companion
                        } else {
                            mood.companionTextRes
                        },
                    ).replace("모비", vehicleCompanionName(friendId)),
                color = MobiMonColors.text,
                fontSize = (if (compact) 26f else 36f).times(designScale).sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(
                        bottom =
                            (if (compact) 24.dp else 46.dp) * designScale,
                    ),
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
                maxWidth >= minimumCardWidth * 3 + 68.dp * designScale -> 3
                maxWidth >= minimumCardWidth * 2 + 34.dp * designScale -> 2
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
                    horizontalArrangement = Arrangement.spacedBy(35.dp * designScale),
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
                badgeTone = if (current == false) VehicleTone.MUTED else status.tone,
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
                        stringResource(
                            if (level <
                                20
                            ) {
                                R.string.vehicle_washer_low_detail
                            } else {
                                R.string.vehicle_washer_enough
                            },
                        )
                    } else {
                        stringResource(R.string.vehicle_washer_unavailable)
                    },
                badge = stringResource(status.labelRes),
                badgeTone = status.tone,
                modifier = modifier,
                testTag = "vehicle-card-washer",
            ) {
                if (level != null) BatteryBar(level, status.tone)
            }
        }
        else -> {
            val spec = VehicleCardCatalog.find(cardId) ?: return
            val reading = VehicleCardCatalog.reading(cardId, snapshot) ?: return
            val percent =
                reading.value
                    ?.removeSuffix("%")
                    ?.toIntOrNull()
                    ?.takeIf { it in 0..100 }
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
            ) {
                if (cardId == "battery-health" && percent != null) {
                    BatteryBar(percent, status.tone)
                }
            }
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
    val gridState = rememberLazyGridState()
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
                (1836.dp * designScale).coerceAtMost(maxWidth - 32.dp)
            } else {
                maxWidth - 32.dp
            }
        val dialogHeight = (1038.dp * designScale).coerceAtMost(maxHeight - 32.dp)
        Surface(
            modifier =
                Modifier
                    .offset(x = if (wide) 1.dp * designScale else 0.dp, y = if (wide) (-25).dp * designScale else 0.dp)
                    .width(dialogWidth)
                    .height(dialogHeight)
                    .testTag("vehicle-card-selector"),
            color = Color(0xFF102238),
            contentColor = MobiMonColors.text,
            shape = RoundedCornerShape(44.dp * designScale),
            border = BorderStroke(1.5.dp * designScale, Color(0xFF64839F)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (wide) 64.dp * designScale else 20.dp,
                            end = if (wide) 64.dp * designScale else 20.dp,
                            top = if (wide) 48.dp * designScale else 20.dp,
                            bottom = if (wide) 44.dp * designScale else 20.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(if (wide) 0.dp else 24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().then(
                        if (wide) Modifier.heightIn(min = 117.184.dp * designScale) else Modifier,
                    ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp * designScale),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.vehicle_selector_edit),
                                contentDescription = null,
                                tint = Color(0xFFB9CADD),
                                modifier = Modifier.size(48.dp * designScale),
                            )
                            Text(
                                stringResource(R.string.vehicle_card_selector_title),
                                color = MobiMonColors.text,
                                fontSize = (44f * designScale).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            stringResource(R.string.vehicle_card_selector_description),
                            modifier = Modifier.offset(y = 1.dp * designScale),
                            color = MobiMonColors.muted,
                            fontSize = (30f * designScale).sp,
                        )
                    }
                    Column(Modifier.offset(y = (-8).dp * designScale), horizontalAlignment = Alignment.End) {
                        val closeLabel = stringResource(R.string.vehicle_card_selector_close)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(76.dp * designScale).semantics { contentDescription = closeLabel },
                        ) {
                            Icon(
                                painterResource(R.drawable.vehicle_selector_close),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp * designScale),
                                tint = Color(0xFFB9CADD),
                            )
                        }
                        Text(
                            stringResource(R.string.vehicle_card_selector_count, availableCards.size),
                            color = Color(0xFFB9CADD),
                            fontSize = (28f * designScale).sp,
                        )
                    }
                }
                if (wide) Spacer(Modifier.height(16.dp * designScale))
                Row(horizontalArrangement = Arrangement.spacedBy(24.6.dp * designScale)) {
                    cards.forEachIndexed { index, id ->
                        val active = index == selectedSlot
                        Surface(
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    ).height(64.dp * designScale)
                                    .clickable { onSlotSelected(index) }
                                    .testTag("vehicle-dialog-slot-${index + 1}"),
                            color = if (active) Color(0xFF87DAF5) else VehiclePanelBackground,
                            border =
                                BorderStroke(
                                    1.5.dp * designScale,
                                    if (active) Color(0xFF87DAF5) else Color(0xFF64839F),
                                ),
                            shape = CircleShape,
                        ) {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(16.dp * designScale, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (active) VehicleScreenBackground else Color(0xFFB9CADD),
                                    fontSize = (28f * designScale).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    VehicleCardCatalog.find(id)?.title.orEmpty(),
                                    color = if (active) VehicleScreenBackground else Color(0xFFB9CADD),
                                    fontSize = (26f * designScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                if (wide) Spacer(Modifier.height(30.844.dp * designScale))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(choiceColumns),
                    state = gridState,
                    contentPadding = PaddingValues(top = if (wide) 2.dp * designScale else 0.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("vehicle-dialog-list")
                            .drawWithContent {
                                drawContent()
                                if (wide && availableCards.size > choiceColumns) {
                                    val inset = 12.dp.toPx() * designScale
                                    val trackHeight = size.height - inset
                                    val thumbHeight = 114.dp.toPx() * designScale
                                    val rowHeight = 438.dp.toPx() * designScale
                                    val rows = (availableCards.size + choiceColumns - 1) / choiceColumns
                                    val scrollRange =
                                        (rows * rowHeight - 24.dp.toPx() * designScale - size.height)
                                            .coerceAtLeast(
                                                1f,
                                            )
                                    val scrollOffset =
                                        gridState.firstVisibleItemIndex / choiceColumns * rowHeight +
                                            gridState.firstVisibleItemScrollOffset
                                    val x = size.width + 24.dp.toPx() * designScale
                                    val width = 6.dp.toPx() * designScale
                                    drawRoundRect(
                                        Color(0xFF203C58),
                                        Offset(x, inset),
                                        Size(width, trackHeight),
                                        CornerRadius(
                                            width / 2,
                                        ),
                                    )
                                    drawRoundRect(
                                        Color(0xFFB9CADD),
                                        Offset(
                                            x,
                                            inset +
                                                (trackHeight - thumbHeight) *
                                                (scrollOffset / scrollRange).coerceIn(0f, 1f),
                                        ),
                                        Size(width, thumbHeight),
                                        CornerRadius(width / 2),
                                    )
                                }
                            },
                    horizontalArrangement = Arrangement.spacedBy(34.dp * designScale),
                    verticalArrangement = Arrangement.spacedBy(24.dp * designScale),
                ) {
                    itemsIndexed(availableCards, key = { _, card -> card.id }) { index, card ->
                        val selected = selectedCardId == card.id
                        Box(
                            Modifier
                                .height(if (wide) 414.dp * designScale else VehicleCardHeight)
                                .clickable { onCardSelected(card.id) }
                                .testTag("vehicle-dialog-option-${card.id}"),
                        ) {
                            VehicleCard(card.id, snapshot, readings, Modifier.fillMaxSize())
                            if (selected) {
                                Box(
                                    Modifier.matchParentSize().border(
                                        3.dp * designScale,
                                        Color(0xFF87DAF5),
                                        RoundedCornerShape(24.dp * designScale),
                                    ),
                                )
                                Row(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 32.dp * designScale, top = 28.dp * designScale)
                                            .clearAndSetSemantics {},
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp * designScale),
                                ) {
                                    Text(
                                        card.title,
                                        color = Color.Transparent,
                                        fontSize = (30f * designScale).sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.vehicle_icon_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp * designScale),
                                        tint = Color(0xFF87DAF5),
                                    )
                                }
                            }
                        }
                    }
                }
                if (wide) Spacer(Modifier.height(20.dp * designScale))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (wide) Modifier.heightIn(min = 98.dp * designScale) else Modifier)
                        .testTag("vehicle-dialog-footer"),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f).align(Alignment.Top).offset(y = 2.dp * designScale)) {
                        Text(
                            stringResource(R.string.vehicle_card_selector_slot, selectedSlot + 1),
                            color = Color(0xFFB9CADD),
                            fontSize = (28f * designScale).sp,
                        )
                        val currentTitle = VehicleCardCatalog.find(cards[selectedSlot])?.title.orEmpty()
                        val nextTitle = selectedCardId?.let { VehicleCardCatalog.find(it)?.title }
                        if (nextTitle != null) {
                            val selectionLabel =
                                stringResource(R.string.vehicle_card_selector_selected, currentTitle, nextTitle)
                            Row(
                                modifier = Modifier.clearAndSetSemantics { contentDescription = selectionLabel },
                                horizontalArrangement = Arrangement.spacedBy(24.dp * designScale),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    currentTitle,
                                    color = MobiMonColors.text,
                                    fontSize = (36f * designScale).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    painterResource(R.drawable.vehicle_selector_arrow),
                                    null,
                                    Modifier.size(
                                        48.dp * designScale,
                                    ),
                                    tint = MobiMonColors.muted,
                                )
                                Text(
                                    nextTitle,
                                    color = MobiMonColors.text,
                                    fontSize = (36f * designScale).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.vehicle_card_selector_choose),
                                color = MobiMonColors.text,
                                fontSize =
                                    (
                                        36f *
                                            designScale
                                    ).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .then(
                                    if (wide) {
                                        Modifier.width(200.dp * designScale).height(
                                            80.dp * designScale,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).testTag("vehicle-dialog-cancel"),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp * designScale, Color(0xFF64839F)),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MobiMonColors.text,
                                containerColor = VehiclePanelBackground,
                            ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            stringResource(R.string.vehicle_card_selector_cancel),
                            modifier = Modifier.offset(y = (-2).dp * designScale),
                            style = selectorButtonTextStyle(32f * designScale),
                        )
                    }
                    Spacer(Modifier.width(24.dp * designScale))
                    Button(
                        onClick = onConfirm,
                        enabled = availableCards.any { it.id == selectedCardId },
                        contentPadding = PaddingValues(0.dp),
                        modifier =
                            Modifier
                                .then(
                                    if (wide) {
                                        Modifier.width(352.dp * designScale).height(
                                            80.dp * designScale,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).testTag("vehicle-dialog-confirm"),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF7F2E7),
                                contentColor = VehicleScreenBackground,
                            ),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vehicle_selector_check),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp * designScale),
                        )
                        Spacer(Modifier.width(12.dp * designScale))
                        Text(
                            stringResource(R.string.vehicle_card_selector_confirm),
                            style = selectorButtonTextStyle(30f * designScale),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun selectorButtonTextStyle(size: Float) =
    TextStyle(
        fontFamily = MobiMonFontFamily,
        fontSize = size.sp,
        lineHeight = (size * 1.25f).sp,
        fontWeight = FontWeight.Bold,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

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
        supporting =
            battery?.let {
                stringResource(if (it < 20) R.string.vehicle_battery_low_detail else R.string.vehicle_battery_enough)
            } ?: statusText,
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
    val warningLocation =
        if (low) {
            when (warning?.location) {
                "왼쪽 앞바퀴", "앞왼쪽" -> stringResource(R.string.vehicle_tire_front_left)
                "오른쪽 앞바퀴", "앞오른쪽" -> stringResource(R.string.vehicle_tire_front_right)
                "왼쪽 뒷바퀴", "뒤왼쪽" -> stringResource(R.string.vehicle_tire_rear_left)
                "오른쪽 뒷바퀴", "뒤오른쪽" -> stringResource(R.string.vehicle_tire_rear_right)
                else -> null
            }
        } else {
            null
        }
    MetricCard(
        title = stringResource(R.string.vehicle_tire_card_title),
        value =
            when {
                low -> stringResource(R.string.vehicle_tire_low)
                readings.tireStatus == "OK" -> stringResource(R.string.vehicle_tire_normal)
                else -> readings.tireStatus ?: stringResource(R.string.vehicle_unknown_short)
            },
        supporting =
            warningLocation?.let { stringResource(R.string.vehicle_tire_low_at_location, it) }
                ?: warning?.description ?: stringResource(
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
    val statusColor =
        when (badgeTone) {
            VehicleTone.SUCCESS -> SuccessAccent
            VehicleTone.WARNING -> WarningAccent
            VehicleTone.MUTED -> Color(0xFF91A7BD)
            VehicleTone.NEUTRAL -> MobiMonColors.text
        }
    val supportingColor =
        if (badgeTone == VehicleTone.NEUTRAL) MobiMonColors.muted else statusColor
    val cardId = testTag?.removePrefix("vehicle-card-")
    val valueSize =
        when (cardId) {
            "battery", "washer", "battery-health" -> 76f
            "tire" -> 68f
            "charging", "battery-range", "battery-time" -> 56f
            else -> 64f
        }
    val icon = vehicleCardIcon(cardId)
    StatusSurface(
        background = VehiclePanelBackground,
        border = Color.Transparent,
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
                    modifier = Modifier.align(Alignment.TopStart).offset(y = 2.dp * designScale),
                    color = MobiMonColors.muted,
                    fontSize = (30f * designScale).sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    painter = painterResource(icon),
                    contentDescription = badge,
                    tint = if (badgeTone == VehicleTone.NEUTRAL) Color(0xFFB9CADD) else statusColor,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp * designScale, y = (-8).dp * designScale)
                            .size(80.dp * designScale)
                            .then(if (testTag != null) Modifier.testTag("$testTag-status") else Modifier),
                )
                if (valueContent != null) {
                    Box(Modifier.offset(y = 94.dp * designScale)) { valueContent() }
                } else {
                    Text(
                        text = value,
                        color = statusColor,
                        fontSize = (valueSize * designScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.offset(
                                y =
                                    (126f - valueSize / 2f + if (valueSize >= 76f) -2f else 2f).dp * designScale,
                            ),
                    )
                }
                Text(
                    text = supporting,
                    color = supportingColor,
                    fontSize = (28f * designScale).sp,
                    modifier = Modifier.offset(y = 187.dp * designScale),
                )
                Column(
                    Modifier.offset(y = 297.dp * designScale),
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
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = badge,
                            tint = if (badgeTone == VehicleTone.NEUTRAL) Color(0xFFB9CADD) else statusColor,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .then(if (testTag != null) Modifier.testTag("$testTag-status") else Modifier),
                        )
                    }
                    if (valueContent != null) {
                        valueContent()
                    } else {
                        Text(
                            text = value,
                            color = statusColor,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = supporting,
                        color = supportingColor,
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
                .height(14.dp * designScale)
                .clip(RoundedCornerShape(8.dp * designScale))
                .background(MobiMonColors.raised),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(clamped / 100f)
                    .height(14.dp * designScale)
                    .clip(RoundedCornerShape(8.dp * designScale))
                    .background(if (tone == VehicleTone.NEUTRAL) Color(0xFFB9CADD) else tone.foreground),
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
    val companionTextRes: Int,
    val bannerTitleRes: Int,
    val bannerDescriptionRes: Int,
    val accent: Color,
    val bannerBackground: Color,
) {
    GOOD(
        R.string.vehicle_mood_good_companion,
        R.string.vehicle_banner_good_title,
        R.string.vehicle_banner_good_desc,
        SuccessAccent,
        SuccessBackground,
    ),
    PARTIAL(
        R.string.vehicle_mood_partial_companion,
        R.string.vehicle_banner_partial_title,
        R.string.vehicle_banner_partial_desc,
        MobiMonColors.accent,
        NeutralBackground,
    ),
    ATTENTION(
        R.string.vehicle_mood_hungry_companion,
        R.string.vehicle_banner_hungry_title,
        R.string.vehicle_banner_hungry_desc,
        WarningAccent,
        WarningBackground,
    ),
    WARNING(
        R.string.vehicle_mood_warning_companion,
        R.string.vehicle_banner_warning_title,
        R.string.vehicle_banner_warning_desc,
        WarningAccent,
        WarningBackground,
    ),
    STALE(
        R.string.vehicle_mood_stale_companion,
        R.string.vehicle_banner_stale_title,
        R.string.vehicle_banner_stale_desc,
        WarningAccent,
        WarningBackground,
    ),
    UNKNOWN(
        R.string.vehicle_mood_unknown_companion,
        R.string.vehicle_banner_unknown_title,
        R.string.vehicle_banner_unknown_desc,
        MobiMonColors.accent,
        NeutralBackground,
    ),
}

private enum class VehicleTone(
    val foreground: Color,
) {
    SUCCESS(SuccessAccent),
    WARNING(WarningAccent),
    MUTED(Color(0xFF91A7BD)),
    NEUTRAL(Color(0xFFB9CADD)),
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

private fun vehicleCardIcon(cardId: String?): Int =
    when (cardId) {
        "charging" -> R.drawable.vehicle_icon_charging
        "tire" -> R.drawable.vehicle_icon_tire
        "washer" -> R.drawable.vehicle_icon_washer
        "battery" -> R.drawable.vehicle_catalog_battery
        "battery-health" -> R.drawable.vehicle_catalog_battery_health
        "battery-range" -> R.drawable.vehicle_catalog_battery_range
        "battery-time" -> R.drawable.vehicle_catalog_battery_time
        "battery-error" -> R.drawable.vehicle_catalog_battery_error
        "driver-door" -> R.drawable.vehicle_catalog_driver_door
        "service-due" -> R.drawable.vehicle_catalog_service_due
        "charging-time" -> R.drawable.vehicle_catalog_charging_time
        "service-distance" -> R.drawable.vehicle_catalog_service_distance
        "service-time" -> R.drawable.vehicle_catalog_service_time
        "brake-fluid" -> R.drawable.vehicle_catalog_brake_fluid
        "low-beam" -> R.drawable.vehicle_catalog_low_beam
        "brake-light" -> R.drawable.vehicle_catalog_brake_light
        "parking-brake" -> R.drawable.vehicle_catalog_parking_brake
        "tire-low" -> R.drawable.vehicle_catalog_tire_low
        "driver-belt" -> R.drawable.vehicle_catalog_driver_belt
        "pad-wear" -> R.drawable.vehicle_catalog_pad_wear
        "pad-warning" -> R.drawable.vehicle_catalog_pad_warning
        "abs" -> R.drawable.vehicle_catalog_abs
        "hood" -> R.drawable.vehicle_catalog_hood
        "trunk" -> R.drawable.vehicle_catalog_trunk
        "washer-low" -> R.drawable.vehicle_catalog_washer_low
        "air-temperature" -> R.drawable.vehicle_catalog_air_temperature
        "rain-intensity" -> R.drawable.vehicle_catalog_rain_intensity
        "cabin-temperature" -> R.drawable.vehicle_catalog_cabin_temperature
        "distance" -> R.drawable.vehicle_catalog_distance
        "dtc-count" -> R.drawable.vehicle_catalog_dtc_count
        "fatigue" -> R.drawable.vehicle_catalog_fatigue
        "distraction" -> R.drawable.vehicle_catalog_distraction
        "breakdown" -> R.drawable.vehicle_catalog_breakdown
        else -> R.drawable.vehicle_catalog_service_due
    }

private val SuccessAccent = Color(0xFF71E5C5)
private val SuccessBackground = Color(0xFF142D2B)
private val WarningAccent = Color(0xFFFFD18A)
private val WarningBackground = Color(0xFF251E14)
private val NeutralBackground = Color(0xFF10243A)
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

private fun vehicleCompanionName(friendId: String): String =
    when (friendId) {
        "friend:luna" -> "루나"
        "friend:las" -> "라스"
        else -> "모비"
    }

private fun SignalUnavailableReason.description(): Int =
    when (this) {
        SignalUnavailableReason.UNSUPPORTED -> R.string.vehicle_parking_unsupported
        SignalUnavailableReason.PERMISSION_DENIED -> R.string.vehicle_parking_permission
        SignalUnavailableReason.DISCONNECTED -> R.string.vehicle_parking_disconnected
        SignalUnavailableReason.NOT_REPORTED, SignalUnavailableReason.INVALID -> R.string.vehicle_parking_unavailable
    }
