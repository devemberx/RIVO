package com.monsters.mobimon.feature.quest

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun QuestCompanionPanel(
    friendId: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    scale: Float,
    isDetail: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val transition = updateTransition(isDetail, label = "quest companion movement")
    val width by transition.animateDp(
        transitionSpec = {
            if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap()
        },
        label = "companion panel width",
    ) { detail -> (if (detail) 824 else 680).dp * scale }
    val avatarX by transition.animateDp(
        transitionSpec = {
            if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap()
        },
        label = "companion x",
    ) { detail -> (if (detail) 86 else 28).dp * scale }
    val avatarScale by transition.animateFloat(
        transitionSpec = {
            if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap()
        },
        label = "companion size",
    ) { detail -> if (detail) 652f / 624f else 1f }
    val compactAvatarX by transition.animateDp(
        transitionSpec = {
            if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap()
        },
        label = "compact companion x",
    ) { detail -> (if (detail) 8 else 0).dp * scale }

    if (isCompact) {
        Row(
            modifier =
                modifier
                    .clip(RoundedCornerShape(24.dp * scale))
                    .background(Colors.panel)
                    .padding(24.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
        ) {
            PetAvatar(
                modifier =
                    Modifier
                        .offset { IntOffset(compactAvatarX.roundToPx(), 0) }
                        .size(160.dp * scale),
                friendId = friendId,
                accessoryId = accessoryId,
                outfitId = outfitId,
                backgroundId = backgroundId,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quest_companion_heading),
                    style = questTextStyle(40f, scale, bold = true, color = Colors.text),
                )
                Spacer(Modifier.height(8.dp * scale))
                Text(
                    text = stringResource(R.string.quest_companion_quote),
                    style = questTextStyle(if (isDetail) 36f else 34f, scale, bold = true, color = Colors.text),
                )
                Spacer(Modifier.height(6.dp * scale))
                Text(
                    text =
                        stringResource(
                            if (isCompleted) {
                                R.string.quest_companion_sub_completed
                            } else {
                                R.string.quest_companion_sub_ready
                            },
                        ),
                    style = questTextStyle(26f, scale, bold = false, color = Colors.muted),
                )
            }
        }
    } else {
        Box(
            modifier =
                modifier
                    .width(width)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(48.dp * scale))
                    .background(Colors.panel)
                    .testTag("quest-companion-panel"),
        ) {
            Text(
                text = stringResource(R.string.quest_companion_heading),
                style = questTextStyle(48f, scale, bold = true, color = Colors.text),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 55.dp * scale),
            )
            PetAvatar(
                modifier =
                    Modifier
                        .offset { IntOffset(avatarX.roundToPx(), (140.dp * scale).roundToPx()) }
                        .size(624.dp * scale)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        },
                friendId = friendId,
                accessoryId = accessoryId,
                outfitId = outfitId,
                backgroundId = backgroundId,
            )
            Text(
                text = stringResource(R.string.quest_companion_quote),
                style = questTextStyle(42f, scale, bold = true, color = Colors.text),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = -(127.dp * scale)),
            )
            Text(
                text =
                    stringResource(
                        if (isCompleted) R.string.quest_companion_sub_completed else R.string.quest_companion_sub_ready,
                    ),
                style = questTextStyle(30f, scale, bold = false, color = Colors.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = -(67.dp * scale)),
            )
        }
    }
}
