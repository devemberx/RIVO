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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.PetAvatar
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
            QuestRewardSuccessContent(
                points = points,
                bonusPoints = bonusPoints,
                friendId = friendId,
                accessoryId = accessoryId,
                outfitId = outfitId,
                backgroundId = backgroundId,
                modalScale = modalScale,
                entranceProgress = { entrance.value },
                avatarScale = { avatarPop.value },
                onConfirm = onConfirm,
            )
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
