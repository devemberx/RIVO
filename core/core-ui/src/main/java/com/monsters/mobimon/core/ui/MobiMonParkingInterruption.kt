package com.monsters.mobimon.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Keeps first-entry loading or already-restricted routes separate from a live parking interruption. */
@Composable
fun rememberParkingInterruption(parkedVerified: Boolean): Boolean {
    var hadVerifiedPark by remember { mutableStateOf(parkedVerified) }
    LaunchedEffect(parkedVerified) {
        if (parkedVerified) hadVerifiedPark = true
    }
    return hadVerifiedPark && !parkedVerified
}

/** Blocking parking interruption surface shared by vehicle, customization, and quest routes. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MobiMonParkingInterruption(
    title: String,
    body: String,
    instruction: String,
    preserved: String,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeFocus = remember { FocusRequester() }
    BackHandler { }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.key == Key.Tab ||
                    event.key == Key.DirectionUp ||
                    event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight
            }.focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .semantics { paneTitle = title },
    ) {
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(MobiMonColors.background.copy(alpha = 0.72f))
                .clickable(interactionSource = source, indication = null) {}
                .clearAndSetSemantics {},
        )
        val wide = maxWidth >= 1200.dp && LocalDensity.current.fontScale <= 1.1f
        val scale = minOf(maxWidth.value / 2560f, maxHeight.value / 1184f)
        MobiMonParkingStatusBadge(
            confirmed = false,
            modifier =
                Modifier.align(Alignment.TopEnd).padding(
                    end = if (wide) 72.dp * scale else 24.dp,
                    top = if (wide) 36.dp * scale else 16.dp,
                ),
            scale = if (wide) scale else 0.7f,
        )
        if (wide) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 32.dp * scale)
                    .size(1360.dp * scale, 740.dp * scale)
                    .background(MobiMonColors.panel, RoundedCornerShape(32.dp * scale))
                    .border(2.dp * scale, MobiMonColors.border, RoundedCornerShape(32.dp * scale))
                    .testTag("parking-interruption-dialog"),
            ) {
                ParkingInterruptionIcon(Modifier.offset(64.dp * scale, 64.dp * scale), scale)
                MobiMonReferenceText(
                    title,
                    64f,
                    258f,
                    48f,
                    scale = scale,
                    bold = true,
                    modifier = Modifier.semantics { heading() },
                )
                MobiMonReferenceText(body, 64f, 336f, 36f, scale = scale, color = MobiMonColors.muted)
                MobiMonReferenceText(instruction, 64f, 390f, 36f, scale = scale, color = MobiMonColors.muted)
                MobiMonReferenceText(preserved, 64f, 466f, 28f, scale = scale, color = MobiMonColors.muted)
                MobiMonButton(
                    onClick = onHome,
                    modifier =
                        Modifier
                            .offset(64.dp * scale, 560.dp * scale)
                            .size(1232.dp * scale, 116.dp * scale)
                            .focusRequester(homeFocus)
                            .testTag("parking-interruption-home"),
                ) {
                    Text("홈으로", style = mobiMonReferenceTextStyle(40f, scale, true))
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .width((maxWidth - 48.dp).coerceAtMost(720.dp))
                    .heightIn(max = maxHeight - 32.dp)
                    .background(MobiMonColors.panel, RoundedCornerShape(24.dp))
                    .border(2.dp, MobiMonColors.border, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .testTag("parking-interruption-dialog"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ParkingInterruptionIcon(Modifier, 0.65f)
                Text(
                    title,
                    style = mobiMonReferenceTextStyle(48f, 0.65f, true),
                    color = MobiMonColors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "$body\n$instruction",
                    style = mobiMonReferenceTextStyle(36f, 0.65f),
                    color = MobiMonColors.muted,
                )
                Text(preserved, style = mobiMonReferenceTextStyle(28f, 0.65f), color = MobiMonColors.muted)
                Spacer(Modifier.height(8.dp))
                MobiMonButton(
                    onClick = onHome,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .focusRequester(homeFocus)
                            .testTag("parking-interruption-home"),
                ) {
                    Text("홈으로", style = mobiMonReferenceTextStyle(40f, 0.65f, true), textAlign = TextAlign.Center)
                }
            }
        }
        LaunchedEffect(homeFocus, wide) { homeFocus.requestFocus() }
    }
}

@Composable
private fun ParkingInterruptionIcon(
    modifier: Modifier,
    scale: Float,
) {
    Box(modifier.size(112.dp * scale).background(MobiMonColors.raised, RoundedCornerShape(32.dp * scale))) {
        Box(
            Modifier
                .offset(36.dp * scale, 30.dp * scale)
                .size(12.dp * scale, 52.dp * scale)
                .background(MobiMonColors.destructive, RoundedCornerShape(6.dp * scale)),
        )
        Box(
            Modifier
                .offset(64.dp * scale, 30.dp * scale)
                .size(12.dp * scale, 52.dp * scale)
                .background(MobiMonColors.destructive, RoundedCornerShape(6.dp * scale)),
        )
    }
}
