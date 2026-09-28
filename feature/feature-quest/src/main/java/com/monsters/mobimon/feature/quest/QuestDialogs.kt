package com.monsters.mobimon.feature.quest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.PetEmotion
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun QuestRewardSuccessModal(
    points: Long,
    bonusPoints: Long = 0L,
    weatherMultiplier: Float = 1.0f,
    friendId: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    scale: Float,
    onConfirm: () -> Unit,
) {
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val entrance = remember(motionEnabled) { Animatable(if (motionEnabled) 0f else 1f) }
    val avatarPop = remember(motionEnabled) { Animatable(if (motionEnabled) 0.75f else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            entrance.animateTo(1f, tween(durationMillis = 240))
            avatarPop.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    Dialog(
        onDismissRequest = onConfirm,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xBF050C16))
                    .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            val modalScale = minOf(scale, maxWidth.value / 1120f, maxHeight.value / 940f)
            Box(
                modifier =
                    Modifier
                        .width(1040.dp * modalScale)
                        .height(880.dp * modalScale)
                        .graphicsLayer {
                            val progress = entrance.value
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
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 48.dp * modalScale)
                            .widthIn(min = 160.dp * modalScale)
                            .height(44.dp * modalScale)
                            .clip(RoundedCornerShape(22.dp * modalScale))
                            .background(Colors.raised)
                            .padding(horizontal = 20.dp * modalScale),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (bonusPoints >
                                    0
                                ) {
                                    R.string.quest_modal_badge_weather_bonus
                                } else {
                                    R.string.quest_modal_badge
                                },
                            ),
                        style = questTextStyle(24f, modalScale, color = Colors.accent),
                    )
                }
                PetAvatar(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 112.dp * modalScale)
                            .size(360.dp * modalScale)
                            .graphicsLayer {
                                scaleX = avatarPop.value
                                scaleY = avatarPop.value
                            },
                    appearanceKey = "GOLDEN",
                    friendId = friendId,
                    accessoryId = accessoryId,
                    outfitId = outfitId,
                    backgroundId = backgroundId,
                    emotion = PetEmotion.HAPPY,
                )
                Text(
                    text = stringResource(R.string.quest_modal_title, points),
                    style = questTextStyle(46f, modalScale, bold = true, color = Colors.text),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.align(Alignment.TopCenter).offset(y = 480.dp * modalScale).width(
                            900.dp * modalScale,
                        ),
                )
                Text(
                    text =
                        stringResource(R.string.quest_modal_subtitle).replace(
                            "모비",
                            if (friendId == "friend:luna") "루나" else "모비",
                        ),
                    style = questTextStyle(30f, modalScale, color = Colors.muted),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.align(Alignment.TopCenter).offset(y = 550.dp * modalScale).width(
                            900.dp * modalScale,
                        ),
                )
                if (bonusPoints > 0) {
                    Text(
                        text = stringResource(R.string.quest_modal_weather_bonus, bonusPoints),
                        style = questTextStyle(26f, modalScale, bold = true, color = Colors.accent),
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.align(Alignment.TopCenter).offset(y = 600.dp * modalScale).width(
                                900.dp * modalScale,
                            ),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (if (bonusPoints > 0) 650 else 610).dp * modalScale)
                            .width(if (bonusPoints > 0) 640.dp * modalScale else 520.dp * modalScale)
                            .height(64.dp * modalScale)
                            .clip(RoundedCornerShape(32.dp * modalScale))
                            .background(Color(0xFF0E2034))
                            .border(1.dp * modalScale, Color(0xFF2A4968), RoundedCornerShape(32.dp * modalScale)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
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
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (if (bonusPoints > 0) 750 else 710).dp * modalScale)
                            .width(440.dp * modalScale)
                            .height(96.dp * modalScale)
                            .clip(RoundedCornerShape(20.dp * modalScale))
                            .background(Colors.button)
                            .clickable(role = Role.Button, onClick = onConfirm)
                            .testTag("quest-modal-btn-confirm"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.quest_action_confirm),
                        style = questTextStyle(38f, modalScale, bold = true, color = Colors.onButton),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuestHiddenClaimModal(
    quest: HiddenQuestUiModel,
    friendId: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    scale: Float,
    canClaim: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xE6050C16))
                    .clickable(enabled = !isBusy, onClick = onDismiss)
                    .testTag("quest-hidden-backdrop"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(1040.dp * scale)
                        .heightIn(max = 880.dp * scale)
                        .clip(RoundedCornerShape(32.dp * scale))
                        .background(Colors.panel)
                        .border(2.dp * scale, Color(0xFFF1C40F), RoundedCornerShape(32.dp * scale))
                        .clickable(enabled = false) {}
                        .padding(32.dp * scale)
                        .verticalScroll(rememberScrollState())
                        .testTag("quest-hidden-claim-modal"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Badge
                Box(
                    modifier =
                        Modifier
                            .width(220.dp * scale)
                            .height(44.dp * scale)
                            .clip(RoundedCornerShape(12.dp * scale))
                            .background(Color(0xFF2E2611)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.quest_hidden_badge),
                        style = questTextStyle(24f, scale, bold = true, color = Color(0xFFF1C40F)),
                    )
                }

                Spacer(Modifier.height(20.dp * scale))

                // Character avatar
                PetAvatar(
                    modifier = Modifier.size(260.dp * scale),
                    appearanceKey = "GOLDEN",
                    friendId = friendId,
                    accessoryId = accessoryId,
                    outfitId = outfitId,
                    backgroundId = backgroundId,
                )

                Spacer(Modifier.height(20.dp * scale))

                Text(
                    text = quest.title,
                    style = questTextStyle(42f, scale, bold = true, color = Colors.text),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp * scale))

                Text(
                    text = quest.description,
                    style = questTextStyle(28f, scale, bold = false, color = Colors.muted),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp * scale))

                errorMessage?.let { MobiMonMessage(it, isError = true) }

                // Reward chip
                Box(
                    modifier =
                        Modifier
                            .width(520.dp * scale)
                            .height(64.dp * scale)
                            .clip(RoundedCornerShape(16.dp * scale))
                            .background(Color(0xFF0E2034))
                            .border(1.dp * scale, Color(0xFF2A4968), RoundedCornerShape(16.dp * scale)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.quest_modal_chip, quest.rewardPoints),
                        style = questTextStyle(32f, scale, bold = true, color = Colors.success),
                    )
                }

                Spacer(Modifier.height(30.dp * scale))

                // Claim button (440x96)
                Box(
                    modifier =
                        Modifier
                            .width(440.dp * scale)
                            .height(96.dp * scale)
                            .clip(RoundedCornerShape(20.dp * scale))
                            .background(if (canClaim) Colors.button else Colors.raised)
                            .clickable(enabled = canClaim, onClick = onClaim)
                            .testTag("quest-hidden-btn-claim"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(if (isBusy) R.string.quest_saving_short else R.string.quest_action_claim),
                        style =
                            questTextStyle(
                                baseSp = 36f,
                                scale = scale,
                                bold = true,
                                color = if (canClaim) Colors.onButton else Colors.muted,
                            ),
                    )
                }
            }
        }
    }
}
