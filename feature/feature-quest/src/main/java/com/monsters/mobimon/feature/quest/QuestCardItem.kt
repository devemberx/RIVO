package com.monsters.mobimon.feature.quest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun QuestCardItem(
    quest: QuestItemUiModel,
    scale: Float,
    canClaim: Boolean,
    isClaimPending: Boolean = false,
    onClick: () -> Unit,
    onClaimReward: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val buttonWidth = if (isCompact) 280.dp * scale else 368.dp * scale
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp * scale)
                .clip(RoundedCornerShape(36.dp * scale))
                .background(Colors.panel)
                .border(2.dp * scale, Colors.border, RoundedCornerShape(36.dp * scale))
                .clickable(onClick = onClick)
                .padding(horizontal = if (isCompact) 24.dp * scale else 48.dp * scale, vertical = 26.dp * scale)
                .testTag("quest-card-${quest.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quest.title,
                style = questTextStyle(40f, scale, bold = true, color = Colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp * scale))
            Text(
                text = quest.description,
                style = questTextStyle(30f, scale, bold = false, color = Colors.muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp * scale))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp * scale else 24.dp * scale),
            ) {
                // Status pill
                val (statusText, statusColor) =
                    when (quest.status) {
                        QuestItemStatus.CLAIMABLE -> stringResource(R.string.quest_status_claimable) to Colors.success
                        QuestItemStatus.IN_PROGRESS -> stringResource(R.string.quest_status_ongoing) to Colors.accent
                        QuestItemStatus.COMPLETED -> stringResource(R.string.quest_status_completed) to Colors.muted
                    }
                Box(
                    modifier =
                        Modifier
                            .width(if (isCompact) 160.dp * scale else 210.dp * scale)
                            .height(60.dp * scale)
                            .clip(RoundedCornerShape(30.dp * scale))
                            .background(Colors.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = statusText,
                        style = questTextStyle(26f, scale, bold = false, color = statusColor),
                    )
                }

                val rewardFormat =
                    if (quest.status == QuestItemStatus.COMPLETED) {
                        stringResource(
                            R.string.quest_reward_points_received_format,
                            quest.scheduleText,
                            quest.rewardPoints,
                        )
                    } else {
                        stringResource(R.string.quest_reward_points_format, quest.scheduleText, quest.rewardPoints)
                    }
                Text(
                    text = rewardFormat,
                    style = questTextStyle(30f, scale, bold = false, color = statusColor),
                )
            }
        }

        Spacer(Modifier.width(16.dp * scale))

        // Action button on right
        when (quest.actionType) {
            QuestActionType.CLAIM_REWARD -> {
                Row(
                    modifier =
                        Modifier
                            .width(buttonWidth)
                            .height(108.dp * scale)
                            .clip(RoundedCornerShape(54.dp * scale))
                            .background(Colors.button)
                            .clickable(enabled = canClaim, onClick = onClaimReward)
                            .testTag("quest-btn-claim-${quest.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.quest_icon_gift),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp * scale),
                        colorFilter = ColorFilter.tint(Colors.onButton),
                    )
                    Spacer(Modifier.width(16.dp * scale))
                    Text(
                        text =
                            stringResource(
                                if (isClaimPending) R.string.quest_saving_short else R.string.quest_action_claim,
                            ),
                        style = questTextStyle(38f, scale, bold = true, color = Colors.onButton),
                    )
                }
            }

            QuestActionType.VIEW_DETAIL -> {
                Row(
                    modifier =
                        Modifier
                            .width(buttonWidth)
                            .height(108.dp * scale)
                            .clip(RoundedCornerShape(54.dp * scale))
                            .background(Colors.panel)
                            .border(2.dp * scale, Colors.border, RoundedCornerShape(54.dp * scale))
                            .clickable(onClick = onClick)
                            .testTag("quest-btn-detail-${quest.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.quest_icon_search),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp * scale),
                        colorFilter = ColorFilter.tint(Colors.text),
                    )
                    Spacer(Modifier.width(16.dp * scale))
                    Text(
                        text = stringResource(R.string.quest_action_view_detail),
                        style = questTextStyle(38f, scale, bold = true, color = Colors.text),
                    )
                }
            }

            QuestActionType.ALREADY_CLAIMED -> {
                Row(
                    modifier =
                        Modifier
                            .width(buttonWidth)
                            .height(108.dp * scale)
                            .clip(RoundedCornerShape(20.dp * scale))
                            .background(Color(0xFF33465B))
                            .clickable(enabled = false) {}
                            .testTag("quest-btn-claimed-${quest.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.quest_icon_check),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp * scale),
                        colorFilter = ColorFilter.tint(Colors.muted),
                    )
                    Spacer(Modifier.width(16.dp * scale))
                    Text(
                        text = stringResource(R.string.quest_action_received),
                        style = questTextStyle(38f, scale, bold = true, color = Colors.muted),
                    )
                }
            }
        }
    }
}
