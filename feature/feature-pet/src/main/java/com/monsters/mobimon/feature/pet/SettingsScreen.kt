package com.monsters.mobimon.feature.pet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonListItem
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.MobiMonNavigationButton
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.MobiMonReferenceText
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle
import com.monsters.mobimon.core.ui.R as CoreUiR

/** Independent preference controls; final composition remains feature-owned. */
@Composable
fun SettingsScreen(
    settings: CompanionSettings,
    onReducedMotionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDebugModeChange: (Boolean) -> Unit = {},
    debugModeAvailable: Boolean = false,
    onLauncherCharacterChange: ((Boolean) -> Unit)? = null,
    hasOverlayPermission: Boolean = true,
    launcherSaving: Boolean = false,
    launcherError: String? = null,
    motionSaving: Boolean = false,
    motionError: String? = null,
    debugSaving: Boolean = false,
    debugError: String? = null,
    settingsAvailable: Boolean = true,
    settingsLoadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    parkedVerified: Boolean = false,
    parkingBadgeConfirmed: Boolean = parkedVerified,
    debugModeInteractionAllowed: Boolean = parkedVerified,
    simulatedVehicle: Boolean = false,
    onOpenCopilot: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MobiMonColors.background)) {
        val reference = maxWidth >= 1400.dp && maxHeight >= 800.dp && LocalDensity.current.fontScale <= 1f
        val scale = if (reference) maxWidth.value / 2560f else 1f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            SettingsHeader(reference, scale, parkingBadgeConfirmed, simulatedVehicle, onBack)
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = (1856 * scale).dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (reference) 0.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy((24 * scale).dp),
            ) {
                if (!reference) {
                    Text(stringResource(R.string.pet_settings_subtitle), color = MobiMonColors.muted)
                }
                if (settingsAvailable) {
                    SettingsItem(
                        R.string.pet_settings_ai_title,
                        if (onOpenCopilot ==
                            null
                        ) {
                            R.string.pet_settings_ai_unavailable
                        } else {
                            R.string.pet_settings_ai_connect_description
                        },
                        if (onOpenCopilot ==
                            null
                        ) {
                            R.string.pet_settings_unavailable_label
                        } else {
                            R.string.pet_settings_ai_connect
                        },
                        reference,
                        scale,
                        enabled = parkedVerified,
                        onClick = onOpenCopilot,
                    )
                    if (onLauncherCharacterChange != null) {
                        val overlayMissing = settings.launcherCharacterEnabled && !hasOverlayPermission
                        val feedback =
                            launcherError ?: if (launcherSaving) {
                                stringResource(R.string.pet_saving)
                            } else if (overlayMissing) {
                                stringResource(R.string.pet_settings_overlay_permission_required)
                            } else {
                                null
                            }
                        SettingsItem(
                            title = R.string.pet_settings_launcher_title,
                            description = R.string.pet_settings_launcher_description,
                            status =
                                if (settings.launcherCharacterEnabled) {
                                    R.string.pet_settings_on
                                } else {
                                    R.string.pet_settings_off
                                },
                            reference = reference,
                            scale = scale,
                            checked = settings.launcherCharacterEnabled,
                            enabled = parkedVerified && !launcherSaving,
                            onCheckedChange = onLauncherCharacterChange,
                            feedback = feedback,
                            isError = launcherError != null || overlayMissing,
                        )
                    } else {
                        SettingsItem(
                            R.string.pet_settings_launcher_title,
                            R.string.pet_settings_launcher_unavailable,
                            R.string.pet_settings_preparing,
                            reference,
                            scale,
                        )
                    }
                    SettingsItem(
                        R.string.pet_setting_motion,
                        R.string.pet_setting_motion_description,
                        if (settings.reducedMotion) R.string.pet_settings_on else R.string.pet_settings_off,
                        reference,
                        scale,
                        checked = settings.reducedMotion,
                        enabled = parkedVerified && !motionSaving,
                        onCheckedChange = onReducedMotionChange,
                        feedback = motionError ?: if (motionSaving) stringResource(R.string.pet_saving) else null,
                        isError = motionError != null,
                    )
                    if (debugModeAvailable) {
                        SettingsItem(
                            R.string.pet_settings_debug_title,
                            if (reference) null else R.string.pet_settings_debug_description,
                            if (settings.debugModeEnabled) R.string.pet_settings_on else R.string.pet_settings_off,
                            reference,
                            scale,
                            checked = settings.debugModeEnabled,
                            enabled = debugModeInteractionAllowed && !debugSaving,
                            onCheckedChange = onDebugModeChange,
                            feedback = debugError ?: if (debugSaving) stringResource(R.string.pet_saving) else null,
                            isError = debugError != null,
                        )
                    }
                }
                if (!settingsAvailable || settingsLoadFailed) {
                    MobiMonMessage(
                        stringResource(
                            if (settingsLoadFailed) {
                                R.string.pet_settings_unavailable
                            } else {
                                R.string.pet_settings_loading
                            },
                        ),
                        isError = settingsLoadFailed,
                    )
                    if (settingsLoadFailed) MobiMonButton(onRetry) { Text(stringResource(R.string.pet_settings_retry)) }
                }
                if (!reference) Text(stringResource(R.string.pet_settings_parked_notice), color = MobiMonColors.muted)
            }
            SettingsFooter(reference, scale, onDone)
        }
    }
}

@Composable
private fun SettingsHeader(
    reference: Boolean,
    scale: Float,
    parkingBadgeConfirmed: Boolean,
    simulatedVehicle: Boolean,
    onBack: () -> Unit,
) {
    val back: @Composable () -> Unit = {
        MobiMonNavigationButton(
            painterResource(CoreUiR.drawable.mobimon_icon_back),
            stringResource(R.string.pet_settings_back),
            onBack,
            Modifier.testTag("settings-back"),
            visualSize = (104 * scale).dp,
            iconSize = (28 * scale).dp,
            borderWidth = (2 * scale).dp,
        )
    }
    val parking: @Composable () -> Unit = { SettingsParking(parkingBadgeConfirmed, scale) }
    if (reference) {
        Box(Modifier.fillMaxWidth().height((196 * scale).dp)) {
            Box(Modifier.offset((72 * scale).dp, (36 * scale).dp)) { back() }
            MobiMonReferenceText(
                stringResource(R.string.pet_settings_title),
                208f,
                80f,
                46f,
                Modifier.semantics { heading() },
                scale,
                bold = true,
            )
            MobiMonReferenceText(
                stringResource(R.string.pet_settings_subtitle),
                208f,
                124f,
                28f,
                scale = scale,
                color = MobiMonColors.muted,
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = (72 * scale).dp).offset(y = (36 * scale).dp),
            ) { parking() }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MobiMonNavigationButton(
                painterResource(CoreUiR.drawable.mobimon_icon_back),
                stringResource(R.string.pet_settings_back),
                onBack,
                Modifier.testTag("settings-back"),
            )
            Text(
                stringResource(R.string.pet_settings_title),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = MobiMonColors.text,
            )
            SettingsParking(parkingBadgeConfirmed, 0.75f)
        }
    }
}

@Composable
private fun SettingsParking(
    parkingBadgeConfirmed: Boolean,
    scale: Float,
) {
    MobiMonParkingStatusBadge(
        confirmed = parkingBadgeConfirmed,
        scale = scale,
    )
}

@Composable
private fun SettingsFooter(
    reference: Boolean,
    scale: Float,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().then(
            if (reference) {
                Modifier.height((188 * scale).dp)
            } else {
                Modifier.padding(24.dp)
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MobiMonButton(
            onDone,
            Modifier
                .widthIn(max = (960 * scale).dp)
                .fillMaxWidth()
                .height(if (reference) (100 * scale).dp.coerceAtLeast(76.dp) else 76.dp)
                .testTag("settings-done"),
        ) {
            Icon(
                painterResource(R.drawable.pet_settings_done),
                null,
                Modifier.offset(x = (-2 * scale).dp).size(
                    (
                        40 *
                            scale
                    ).dp,
                ),
            )
            Spacer(Modifier.width((24 * scale).dp))
            Text(
                stringResource(R.string.pet_settings_done),
                modifier = if (reference) Modifier.offset(y = (-2 * scale).dp) else Modifier,
                style = mobiMonReferenceTextStyle(if (reference) 38f else 28f, scale, bold = true),
            )
        }
        if (reference) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.TopCenter)) {
                    MobiMonReferenceText(
                        stringResource(R.string.pet_settings_parked_notice),
                        0f,
                        44f,
                        28f,
                        scale = scale,
                        color = MobiMonColors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: Int,
    description: Int?,
    status: Int,
    reference: Boolean,
    scale: Float,
    checked: Boolean? = null,
    enabled: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    feedback: String? = null,
    isError: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((32 * scale).dp)
    val interaction =
        when {
            onClick != null -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            checked != null ->
                Modifier.toggleable(
                    checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
            else -> Modifier.semantics(mergeDescendants = true) { disabled() }
        }
    val supportingText = feedback ?: description?.let { stringResource(it) }
    MobiMonListItem(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(3.dp, MobiMonColors.accent, shape) else Modifier)
            .then(interaction),
        shape = shape,
        containerColor = MobiMonColors.panel,
        contentPadding =
            if (reference) {
                PaddingValues(
                    start = (60 * scale).dp,
                    end = (56 * scale).dp,
                )
            } else {
                PaddingValues(24.dp)
            },
        supporting =
            if (reference || supportingText == null) {
                null
            } else {
                {
                    Text(
                        supportingText,
                        Modifier.semantics { if (feedback != null) liveRegion = LiveRegionMode.Polite },
                        color = if (isError) MaterialTheme.colorScheme.error else MobiMonColors.muted,
                    )
                }
            },
        trailing = { SettingsStatus(stringResource(status), checked, onClick != null, scale, reference) },
    ) {
        if (reference) {
            Box(Modifier.fillMaxWidth().height((136 * scale).dp)) {
                MobiMonReferenceText(
                    stringResource(title),
                    0f,
                    if (supportingText == null) 75f else 53f,
                    38f,
                    scale = scale,
                    bold = true,
                )
                if (supportingText != null) {
                    MobiMonReferenceText(
                        supportingText,
                        0f,
                        103f,
                        28f,
                        Modifier.semantics { if (feedback != null) liveRegion = LiveRegionMode.Polite },
                        scale,
                        color = if (isError) MaterialTheme.colorScheme.error else MobiMonColors.muted,
                    )
                }
            }
        } else {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SettingsStatus(
    status: String,
    checked: Boolean?,
    link: Boolean,
    scale: Float,
    reference: Boolean,
) {
    if (!reference) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (link) MobiMonColors.panel else MobiMonColors.raised,
            border = if (link) null else BorderStroke(2.dp, MobiMonColors.border),
        ) {
            Row(
                Modifier.width((264 * LocalDensity.current.fontScale).dp).heightIn(min = 88.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (checked != null) {
                    Box(
                        Modifier.size(64.dp).background(
                            if (checked) MobiMonColors.accent else MobiMonColors.muted,
                            CircleShape,
                        ),
                    )
                }
                Text(
                    status,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (link) MobiMonColors.accent else MobiMonColors.text,
                )
                if (link) {
                    Icon(
                        painterResource(R.drawable.pet_settings_chevron),
                        null,
                        Modifier.size(28.dp),
                        tint = MobiMonColors.accent,
                    )
                }
            }
        }
        return
    }
    if (link) {
        Box(Modifier.size((264 * scale).dp, (88 * scale).dp)) {
            MobiMonReferenceText(status, 40f, 57f, 32f, scale = scale, color = MobiMonColors.accent)
            Icon(
                painterResource(R.drawable.pet_settings_chevron),
                null,
                Modifier.offset((217 * scale).dp, (30 * scale).dp).size((28 * scale).dp),
                tint = MobiMonColors.accent,
            )
        }
    } else {
        Surface(
            Modifier.size((264 * scale).dp, (88 * scale).dp),
            shape = RoundedCornerShape(50),
            color = MobiMonColors.raised,
            border = BorderStroke((if (checked == null) 1 else 2).dp * scale, MobiMonColors.border),
        ) {
            Box {
                if (checked != null) {
                    Box(
                        Modifier
                            .offset((if (checked) 188 else 12).dp * scale, (12 * scale).dp)
                            .size(
                                (64 * scale).dp,
                            ).background(if (checked) MobiMonColors.accent else MobiMonColors.muted, CircleShape),
                    )
                }
                Box(
                    Modifier
                        .offset(
                            x =
                                (
                                    if (checked ==
                                        true
                                    ) {
                                        36
                                    } else {
                                        88
                                    }
                                ).dp * scale,
                        ).width((148 * scale).dp)
                        .height((88 * scale).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        status,
                        Modifier.offset(x = (if (checked != null) 1 else 0).dp * scale, y = (2 * scale).dp),
                        style = mobiMonReferenceTextStyle(30f, scale, bold = true),
                    )
                }
            }
        }
    }
}
