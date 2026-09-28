package com.monsters.mobimon.feature.quest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.MobiMonPointSummary
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun QuestRightPanel(
    quests: List<QuestItemUiModel>,
    scale: Float,
    selectedTab: QuestFilterTab,
    onSelectTab: (QuestFilterTab) -> Unit,
    canClaim: Boolean,
    isCompact: Boolean,
    onSelectQuest: (String) -> Unit,
    onClaimReward: (String) -> Unit,
    modifier: Modifier = Modifier,
    pointBalance: PointBalanceState = PointBalanceState.Loading,
    showPointSummary: Boolean = true,
) {
    val displayedQuests =
        when (selectedTab) {
            QuestFilterTab.ALL -> quests
            QuestFilterTab.IN_PROGRESS -> quests.filter { it.status != QuestItemStatus.COMPLETED }
            QuestFilterTab.COMPLETED -> quests.filter { it.status == QuestItemStatus.COMPLETED }
        }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height((if (isCompact) 72 else 80).dp * scale),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quest_section_title),
                style = questTextStyle(48f, scale, bold = true, color = Colors.text),
            )
            if (showPointSummary) {
                MobiMonPointSummary(
                    balance = (pointBalance as? PointBalanceState.Ready)?.balance,
                    modifier = Modifier.offset(y = 8.dp * scale),
                    failed = pointBalance == PointBalanceState.Failed,
                    scale = scale,
                )
            }
        }

        Spacer(Modifier.height((if (isCompact) 24 else 38).dp * scale))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp * scale else 24.dp * scale),
        ) {
            QuestFilterTabButton(
                title = stringResource(R.string.quest_tab_all),
                iconRes = R.drawable.quest_icon_grid,
                selected = selectedTab == QuestFilterTab.ALL,
                scale = scale,
                isCompact = isCompact,
                onClick = { onSelectTab(QuestFilterTab.ALL) },
                modifier =
                    if (isCompact) {
                        Modifier.weight(1f).testTag("quest-tab-all")
                    } else {
                        Modifier.testTag("quest-tab-all")
                    },
            )
            QuestFilterTabButton(
                title = stringResource(R.string.quest_tab_ongoing),
                iconRes = R.drawable.quest_icon_clock,
                selected = selectedTab == QuestFilterTab.IN_PROGRESS,
                scale = scale,
                isCompact = isCompact,
                onClick = { onSelectTab(QuestFilterTab.IN_PROGRESS) },
                modifier =
                    if (isCompact) {
                        Modifier.weight(1f).testTag("quest-tab-ongoing")
                    } else {
                        Modifier.testTag("quest-tab-ongoing")
                    },
            )
            QuestFilterTabButton(
                title = stringResource(R.string.quest_tab_completed),
                iconRes = R.drawable.quest_icon_check,
                selected = selectedTab == QuestFilterTab.COMPLETED,
                scale = scale,
                isCompact = isCompact,
                onClick = { onSelectTab(QuestFilterTab.COMPLETED) },
                modifier =
                    if (isCompact) {
                        Modifier.weight(1f).testTag("quest-tab-completed")
                    } else {
                        Modifier.testTag("quest-tab-completed")
                    },
            )
        }

        Spacer(Modifier.height((if (isCompact) 24 else 36).dp * scale))

        if (displayedQuests.isEmpty()) {
            val emptyModifier =
                if (isCompact) {
                    Modifier.fillMaxWidth().heightIn(min = 400.dp * scale)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                }
            QuestEmptyStateCard(
                selectedTab = selectedTab,
                scale = scale,
                onShowAll = { onSelectTab(QuestFilterTab.ALL) },
                modifier = emptyModifier,
                isCompact = isCompact,
            )
        } else {
            val scrollState = rememberScrollState()
            val listModifier =
                if (isCompact) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxSize().verticalScroll(scrollState)
                }
            Box(modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = listModifier,
                    verticalArrangement = Arrangement.spacedBy(26.dp * scale),
                ) {
                    displayedQuests.forEach { quest ->
                        key(quest.id) {
                            QuestCardItem(
                                quest = quest,
                                canClaim = canClaim,
                                scale = scale,
                                isCompact = isCompact,
                                onClick = { onSelectQuest(quest.id) },
                                onClaimReward = { onClaimReward(quest.id) },
                            )
                        }
                    }
                    Spacer(Modifier.height(80.dp * scale))
                }
                if (!isCompact && scrollState.maxValue > 0) {
                    QuestScrollIndicator(
                        scrollState = scrollState,
                        scale = scale,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 24.dp * scale),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuestScrollIndicator(
    scrollState: ScrollState,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .width(8.dp * scale)
            .fillMaxHeight()
            .padding(bottom = 10.dp * scale)
            .testTag("quest-scroll-indicator"),
    ) {
        val thumbHeight = 180.dp.toPx() * scale
        val travel = (size.height - thumbHeight - 12.dp.toPx() * scale).coerceAtLeast(0f)
        val progress = if (scrollState.maxValue == 0) 0f else scrollState.value.toFloat() / scrollState.maxValue
        drawRoundRect(
            Color(0xFF142A42),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(size.width / 2),
        )
        drawRoundRect(
            Color(0xFF64839F),
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(0f, 6.dp.toPx() * scale + travel * progress),
            size =
                androidx.compose.ui.geometry
                    .Size(size.width, thumbHeight.coerceAtMost(size.height)),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(size.width / 2),
        )
    }
}

@Composable
internal fun QuestFilterTabButton(
    title: String,
    iconRes: Int,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val bg = if (selected) Colors.accent else Colors.panel
    val fg = if (selected) Colors.onButton else Colors.text
    val borderMod =
        if (selected) {
            Modifier
        } else {
            Modifier.border(
                2.dp * scale,
                Colors.border,
                RoundedCornerShape(44.dp * scale),
            )
        }

    Row(
        modifier =
            modifier
                .then(if (isCompact) Modifier else Modifier.width(336.dp * scale))
                .height((if (isCompact) 72 else 88).dp * scale)
                .clip(RoundedCornerShape(44.dp * scale))
                .background(bg)
                .then(borderMod)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = if (isCompact) 12.dp * scale else 24.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp * scale),
            colorFilter = ColorFilter.tint(fg),
        )
        Spacer(Modifier.width(14.dp * scale))
        Text(
            text = title,
            style = questTextStyle(34f, scale, bold = true, color = fg),
        )
    }
}
