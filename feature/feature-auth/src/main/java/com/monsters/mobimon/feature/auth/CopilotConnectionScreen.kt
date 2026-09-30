package com.monsters.mobimon.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.ui.CompanionIcon
import com.monsters.mobimon.core.ui.MobiMonFontFamily
import com.monsters.mobimon.core.ui.MobiMonNavigationButton
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

/** Figma P51–P56. The host owns navigation, countdowns and all connection work. */
@Composable
fun CopilotConnectionScreen(
    state: CopilotUiState,
    onAction: (CopilotAction) -> Unit,
    modifier: Modifier = Modifier,
    interactionAllowed: Boolean = false,
    simulatedVehicle: Boolean = false,
    friendId: String = "friend:mobi",
    appearanceKey: String = "GOLDEN",
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
    vehicleWarning: Boolean = false,
    vehicleHungry: Boolean = false,
    qrCode: Painter? = null,
    parkingBadgeConfirmed: Boolean = interactionAllowed,
) {
    val title = stringResource(R.string.copilot_title)
    val displayedState =
        if (state is CopilotUiState.Waiting &&
            state.remainingSeconds <= 0
        ) {
            CopilotUiState.Expired
        } else {
            state
        }
    val friend = stringResource(if (friendId == "friend:luna") R.string.copilot_luna else R.string.copilot_mobi)
    Box(modifier.fillMaxSize().background(Colors.background).semantics { paneTitle = title }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fontScale = LocalDensity.current.fontScale
            val reference = maxWidth >= 1400.dp && maxHeight >= maxWidth * (1184f / 2560f) && fontScale <= 1f
            val scale = if (reference) maxWidth.value / 2560f else 0.75f
            val contentHeight = maxHeight
            if (reference) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().testTag("copilot-reference")) {
                        CopilotReferenceHeader(
                            onAction,
                            parkingBadgeConfirmed,
                            simulatedVehicle,
                            scale,
                            Modifier.offset(72.dp * scale, 36.dp * scale).width(2416.dp * scale),
                        )
                        Row(
                            Modifier.offset(72.dp * scale, 196.dp * scale).size(
                                2416.dp * scale,
                                contentHeight - 220.dp * scale,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(44.dp * scale),
                        ) {
                            CompanionPanel(
                                displayedState,
                                friend,
                                friendId,
                                appearanceKey,
                                accessoryId,
                                outfitId,
                                backgroundId,
                                vehicleWarning,
                                vehicleHungry,
                                scale,
                                Modifier.width(884.dp * scale).fillMaxSize(),
                            )
                            CopilotPanelTransition(
                                displayedState,
                                onAction,
                                Modifier.weight(1f).fillMaxSize(),
                                interactionAllowed,
                            ) { panelState, panelAction ->
                                CopilotPanel(
                                    panelState,
                                    panelAction,
                                    interactionAllowed,
                                    friend,
                                    qrCode,
                                    scale,
                                    Modifier.fillMaxSize(),
                                    reference = true,
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    CopilotHeader(onAction, parkingBadgeConfirmed, simulatedVehicle, scale)
                    Row(
                        Modifier.fillMaxWidth().background(Colors.panel, RoundedCornerShape(24.dp)).padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        PetAvatar(
                            Modifier.size(112.dp),
                            appearanceKey,
                            friendId = friendId,
                            accessoryId = accessoryId,
                            outfitId = outfitId,
                            backgroundId = backgroundId,
                            vehicleWarning = vehicleWarning,
                            vehicleHungry = vehicleHungry,
                        )
                        Text(
                            stringResource(R.string.copilot_friend_heading, friend),
                            style = copilotStyle(40f, scale, true),
                        )
                    }
                    CopilotPanelTransition(
                        displayedState,
                        onAction,
                        Modifier.fillMaxWidth(),
                        interactionAllowed,
                    ) { panelState, panelAction ->
                        CopilotPanel(
                            panelState,
                            panelAction,
                            interactionAllowed,
                            friend,
                            qrCode,
                            scale,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CopilotHeader(
    onAction: (CopilotAction) -> Unit,
    parkingBadgeConfirmed: Boolean,
    simulatedVehicle: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth / LocalDensity.current.fontScale < 800.dp
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        24.dp * scale,
                    ),
            ) {
                MobiMonNavigationButton(
                    icon = painterResource(R.drawable.copilot_back),
                    description = stringResource(R.string.copilot_back),
                    onClick = { onAction(CopilotAction.BACK) },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.copilot_title),
                        Modifier.semantics { heading() },
                        style = copilotStyle(46f, scale, true),
                    )
                    Text(
                        stringResource(R.string.copilot_subtitle),
                        style = copilotStyle(28f, scale),
                        color = Colors.muted,
                    )
                }
                if (!compact) ParkingStatus(parkingBadgeConfirmed, simulatedVehicle, scale)
            }
            if (compact) ParkingStatus(parkingBadgeConfirmed, simulatedVehicle, scale)
        }
    }
}

@Composable
private fun CopilotReferenceHeader(
    onAction: (CopilotAction) -> Unit,
    parkingBadgeConfirmed: Boolean,
    simulatedVehicle: Boolean,
    scale: Float,
    modifier: Modifier,
) {
    Box(modifier) {
        val backSize = 104.dp * scale
        val backTarget = backSize.coerceAtLeast(76.dp)
        MobiMonNavigationButton(
            icon = painterResource(R.drawable.copilot_back),
            description = stringResource(R.string.copilot_back),
            onClick = { onAction(CopilotAction.BACK) },
            modifier = Modifier.offset(-(backTarget - backSize) / 2, -(backTarget - backSize) / 2),
            visualSize = backSize,
            iconSize = 28.dp * scale,
        )
        CopilotPositionedText(
            stringResource(R.string.copilot_title),
            136f,
            43.9f,
            46f,
            scale,
            Modifier.semantics { heading() },
            bold = true,
        )
        CopilotPositionedText(stringResource(R.string.copilot_subtitle), 136f, 88.2f, 28f, scale, color = Colors.muted)
        MobiMonParkingStatusBadge(
            confirmed = parkingBadgeConfirmed,
            modifier = Modifier.align(Alignment.TopEnd),
            scale = scale,
        )
        if (simulatedVehicle) {
            CopilotPositionedText(
                stringResource(R.string.copilot_simulated),
                2072f,
                120f,
                24f,
                scale,
                color = Colors.muted,
            )
        }
    }
}

@Composable
private fun ParkingStatus(
    parkingBadgeConfirmed: Boolean,
    simulatedVehicle: Boolean,
    scale: Float,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MobiMonParkingStatusBadge(
            confirmed = parkingBadgeConfirmed,
            scale = scale,
        )
        if (simulatedVehicle) {
            Text(
                stringResource(R.string.copilot_simulated),
                style = copilotStyle(24f, scale),
                color = Colors.muted,
            )
        }
    }
}

@Composable
private fun CompanionPanel(
    state: CopilotUiState,
    friend: String,
    friendId: String,
    appearanceKey: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    vehicleWarning: Boolean,
    vehicleHungry: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag("copilot-companion").background(Colors.panel, RoundedCornerShape(48.dp * scale))) {
        CopilotPositionedText(
            stringResource(R.string.copilot_friend_heading, friend),
            0f,
            115.2f,
            48f,
            scale,
            Modifier.width(884.dp * scale),
            bold = true,
            textAlign = TextAlign.Center,
        )
        CopilotPositionedText(
            stringResource(
                when (state) {
                    is CopilotUiState.AuthenticationStatus ->
                        if (state.account !=
                            null
                        ) {
                            R.string.github_friend_authenticated
                        } else {
                            R.string.copilot_friend_intro
                        }
                    is CopilotUiState.Connected -> R.string.copilot_friend_connected
                    is CopilotUiState.Waiting, CopilotUiState.Expired -> R.string.copilot_friend_waiting
                    is CopilotUiState.Disconnect -> R.string.copilot_friend_disconnect
                    else -> R.string.copilot_friend_intro
                },
            ),
            0f,
            174f,
            30f,
            scale,
            Modifier.width(884.dp * scale),
            color = Colors.muted,
            textAlign = TextAlign.Center,
        )
        PetAvatar(
            Modifier.offset(114.dp * scale, 188.dp * scale).size(636.dp * scale),
            appearanceKey,
            friendId = friendId,
            accessoryId = accessoryId,
            outfitId = outfitId,
            backgroundId = backgroundId,
            vehicleWarning = vehicleWarning,
            vehicleHungry = vehicleHungry,
        )
        Box(
            Modifier
                .offset(282.dp * scale, 888.dp * scale)
                .size(320.dp * scale, 60.dp * scale)
                .background(Colors.raised, RoundedCornerShape(50)),
        )
        CopilotPositionedText(
            stringResource(R.string.copilot_friend_tagline),
            282f,
            927.9f,
            26f,
            scale,
            Modifier.width(320.dp * scale),
            color = Colors.accent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CopilotPositionedText(
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = Colors.text,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        Modifier
            .offset(x.dp * scale, (baseline - size * 1.4f).dp * scale)
            .then(modifier)
            .paddingFromBaseline(top = (size * 1.4f).dp * scale),
        style = copilotStyle(size, scale, bold),
        color = color,
        textAlign = textAlign,
    )
}

internal fun copilotStyle(
    size: Float,
    scale: Float,
    bold: Boolean = false,
) = TextStyle(
    fontFamily = MobiMonFontFamily,
    fontSize = (size * scale).sp,
    letterSpacing = 0.sp,
    // Preserve the SVG's fractional glyph advances as the composition scales.
    textMotion = TextMotion.Animated,
    lineHeight = (size * scale * 1.4f).sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = Colors.text,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
internal fun CopilotIconButton(
    icon: CompanionIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick, modifier.size(76.dp).semantics { contentDescription = description }) {
        Surface(shape = RoundedCornerShape(50), color = Colors.raised, border = BorderStroke(1.dp, Colors.border)) {
            CompanionIcon(icon, Colors.muted, Modifier.padding(16.dp).size(24.dp))
        }
    }
}

@Composable
internal fun CopilotButton(
    label: String,
    onClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leadingIcon: Int? = null,
    heightUnits: Float = 112f,
    referenceLabelOffset: Float? = null,
    referenceIconOffset: Float = 0f,
) {
    val visualHeight = heightUnits.dp * scale
    val touchHeight = visualHeight.coerceAtLeast(76.dp)
    val enlargedText = LocalDensity.current.fontScale > 1f
    var focused by remember { mutableStateOf(false) }
    val foreground =
        when {
            !enabled -> Colors.muted
            destructive -> Colors.destructive
            primary -> Colors.onButton
            else -> Colors.text
        }
    val background =
        when {
            !enabled -> Colors.raised
            primary -> Colors.button
            else -> Colors.panel
        }
    Box(
        modifier
            .offset(y = if (enlargedText) 0.dp else -(touchHeight - visualHeight) / 2)
            .then(if (enlargedText) Modifier.heightIn(min = touchHeight) else Modifier.height(touchHeight))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(3.dp, Colors.accent, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .then(
                    if (enlargedText) Modifier.heightIn(min = visualHeight) else Modifier.height(visualHeight),
                ).then(
                    if (!primary && referenceLabelOffset != null) {
                        Modifier.copilotPillOutline(
                            (if (destructive) 3.dp else 2.dp) * scale,
                            if (destructive) Colors.destructive else Colors.border,
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(50),
            color = background,
            border =
                if (primary || referenceLabelOffset != null) {
                    null
                } else {
                    BorderStroke(
                        (if (destructive) 3.dp else 2.dp) * scale,
                        if (destructive) Colors.destructive else Colors.border,
                    )
                },
        ) {
            if (referenceLabelOffset != null) {
                Box(Modifier.fillMaxSize()) {
                    if (leadingIcon != null) {
                        Icon(
                            painterResource(leadingIcon),
                            null,
                            Modifier
                                .offset(referenceIconOffset.dp * scale, (heightUnits / 2 - 20).dp * scale)
                                .size(40.dp * scale),
                            tint = foreground,
                        )
                    }
                    CopilotPositionedText(
                        label,
                        referenceLabelOffset,
                        heightUnits / 2 + 15.2f,
                        38f,
                        scale,
                        Modifier.fillMaxWidth(),
                        bold = true,
                        color = foreground,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 24.dp * scale, vertical = if (enlargedText) 16.dp * scale else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (leadingIcon != null) {
                        Icon(painterResource(leadingIcon), null, Modifier.size(28.dp * scale), tint = foreground)
                        Spacer(Modifier.width(32.dp * scale))
                    }
                    Text(
                        label,
                        style = copilotStyle(38f, scale, true),
                        color = foreground,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Modifier.copilotPillOutline(
    width: Dp,
    color: Color,
): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2), style = Stroke(width.toPx()))
    }
