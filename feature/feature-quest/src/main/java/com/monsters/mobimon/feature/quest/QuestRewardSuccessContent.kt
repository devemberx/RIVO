package com.monsters.mobimon.feature.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.CharacterArtwork
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.PetEmotion
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

/** One measured layout for every friend, equipped look and committed reward. */
@Composable
internal fun QuestRewardSuccessContent(
    points: Long,
    bonusPoints: Long,
    friendId: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    modalScale: Float,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    entranceProgress: () -> Float = { 1f },
    avatarScale: () -> Float = { 1f },
) {
    Box(
        modifier
            .width(1040.dp * modalScale)
            .height(880.dp * modalScale)
            .graphicsLayer {
                val progress = entranceProgress()
                alpha = progress
                scaleX = 0.94f + progress * 0.06f
                scaleY = 0.94f + progress * 0.06f
                translationY = (1f - progress) * 24.dp.toPx()
            }.clip(RoundedCornerShape(32.dp * modalScale))
            .background(Colors.panel)
            .border(2.dp * modalScale, Colors.border, RoundedCornerShape(32.dp * modalScale))
            .clickable {}
            .testTag("quest-reward-success-modal"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = 64.dp * modalScale,
                end = 64.dp * modalScale,
                top = 48.dp * modalScale,
                bottom = 74.dp * modalScale,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .widthIn(min = 160.dp * modalScale)
                    .heightIn(min = 44.dp * modalScale)
                    .clip(RoundedCornerShape(22.dp * modalScale))
                    .background(Colors.raised)
                    .padding(horizontal = 20.dp * modalScale, vertical = 4.dp * modalScale)
                    .testTag("quest-reward-badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.quest_modal_badge),
                    style = questTextStyle(24f, modalScale, color = Colors.accent),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp * modalScale))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().testTag("quest-reward-artwork-area"),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Leave room above the slot for the restored cap and entrance spring.
                val avatarSize = minOf(320.dp * modalScale, maxHeight / 1.11f)
                PetAvatar(
                    modifier =
                        Modifier
                            .size(avatarSize)
                            .graphicsLayer {
                                scaleX = avatarScale()
                                scaleY = avatarScale()
                                transformOrigin = TransformOrigin(0.5f, CharacterArtwork.HAPPY_GROUND_FRACTION)
                            }.testTag("quest-reward-character"),
                    friendId = friendId,
                    accessoryId = accessoryId,
                    outfitId = outfitId,
                    backgroundId = backgroundId,
                    emotion = PetEmotion.HAPPY,
                )
            }
            Spacer(Modifier.height(16.dp * modalScale))
            Text(
                stringResource(R.string.quest_modal_title, points),
                style = questTextStyle(46f, modalScale, bold = true, color = Colors.text),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("quest-reward-title"),
            )
            Spacer(Modifier.height(4.dp * modalScale))
            Text(
                stringResource(R.string.quest_modal_subtitle).replace(
                    "모비",
                    when (friendId) {
                        "friend:luna" -> "루나"
                        "friend:las" -> "라스"
                        else -> "모비"
                    },
                ),
                style = questTextStyle(30f, modalScale, color = Colors.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("quest-reward-subtitle"),
            )
            Spacer(Modifier.height(12.dp * modalScale))
            Box(
                Modifier
                    .widthIn(min = 520.dp * modalScale)
                    .heightIn(min = 64.dp * modalScale)
                    .clip(RoundedCornerShape(32.dp * modalScale))
                    .background(Color(0xFF0E2034))
                    .border(1.dp * modalScale, Color(0xFF2A4968), RoundedCornerShape(32.dp * modalScale))
                    .padding(horizontal = 20.dp * modalScale, vertical = 8.dp * modalScale)
                    .testTag("quest-reward-points"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (bonusPoints > 0) {
                        stringResource(R.string.quest_modal_chip_weather_bonus, points, bonusPoints)
                    } else {
                        stringResource(R.string.quest_modal_chip, points)
                    },
                    style =
                        questTextStyle(
                            if (bonusPoints >
                                0
                            ) {
                                28f
                            } else {
                                32f
                            },
                            modalScale,
                            bold = true,
                            color = Colors.success,
                        ),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(36.dp * modalScale))
            Box(
                Modifier
                    .width(440.dp * modalScale)
                    .heightIn(min = 96.dp * modalScale)
                    .clip(RoundedCornerShape(20.dp * modalScale))
                    .background(Colors.button)
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .testTag("quest-modal-btn-confirm"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.quest_action_confirm),
                    style = questTextStyle(38f, modalScale, bold = true, color = Colors.onButton),
                )
            }
        }
    }
}
