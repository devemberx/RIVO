package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared parking status copy and palette; callers supply display state, never authorization. */
@Composable
fun MobiMonParkingStatusBadge(
    confirmed: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    MobiMonParkingBadge(
        status =
            stringResource(
                if (confirmed) R.string.mobimon_parking_confirmed else R.string.mobimon_parking_restricted,
            ),
        modifier = modifier,
        scale = scale,
        restricted = !confirmed,
    )
}

/** Shared parking badge geometry; callers supply display state, never authorization. */
@Composable
fun MobiMonParkingBadge(
    status: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    restricted: Boolean = false,
) {
    val positioned = LocalDensity.current.fontScale <= 1f
    val width = 344.dp * scale
    val foreground = if (restricted) MobiMonColors.destructive else MobiMonColors.accent
    val textStyle =
        TextStyle(
            fontFamily = MobiMonFontFamily,
            fontSize = (30 * scale).sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            textMotion = TextMotion.Animated,
            lineHeight = (42 * scale).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    Surface(
        modifier =
            modifier
                .then(if (positioned) Modifier.size(width, 76.dp * scale) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = status }
                .drawWithContent {
                    drawContent()
                    // The reference stroke is centered on the capsule boundary.
                    drawRoundRect(
                        MobiMonColors.border,
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke((2.dp * scale).toPx()),
                    )
                },
        shape = RoundedCornerShape(50),
        color = MobiMonColors.panel,
        contentColor = foreground,
    ) {
        if (positioned && !restricted) {
            Box(Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.mobimon_parking),
                    contentDescription = null,
                    modifier =
                        Modifier.offset(62.dp * scale, 18.dp * scale).size(40.dp * scale).testTag("parking-icon"),
                )
                Text(
                    status,
                    Modifier.offset(127.65.dp * scale, 7.dp * scale).paddingFromBaseline(top = 42.dp * scale),
                    style = textStyle,
                    maxLines = 1,
                )
            }
        } else {
            Row(
                Modifier
                    .then(
                        if (positioned) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .widthIn(min = width)
                                .heightIn(min = 76.dp * scale)
                                .padding(horizontal = 24.dp * scale, vertical = 12.dp * scale)
                        },
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        (if (restricted) 24.dp else 25.65.dp) * scale,
                        Alignment.CenterHorizontally,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(
                        if (restricted) R.drawable.mobimon_parking_unconfirmed else R.drawable.mobimon_parking,
                    ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(
                                (if (restricted) 20.dp else 40.dp) * scale,
                                (if (restricted) 24.dp else 40.dp) * scale,
                            ).testTag("parking-icon"),
                )
                Text(
                    status,
                    modifier = if (positioned) Modifier else Modifier.weight(1f, fill = false),
                    style = textStyle,
                    maxLines = 1,
                )
            }
        }
    }
}
