package com.monsters.mobimon.feature.quest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.MobiMonParkingInterruption
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.MobiMonPointSummary
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
fun QuestScreen(
    state: QuestScreenState,
    onClaimReward: (String) -> Unit,
    onDismissHiddenQuest: (String) -> Unit,
    onDismissRewardSuccess: () -> Unit,
    onRetryQuests: () -> Unit,
    onRetryWallet: () -> Unit,
    onRetryAppearance: () -> Unit,
    onNavigateRoute: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    parkingBadgeConfirmed: Boolean = state.parkedVerified,
    parkingRequired: Boolean = false,
) {
    var selectedQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(QuestFilterTab.ALL) }
    val selectedQuest = state.quests.firstOrNull { it.id == selectedQuestId }
    BackHandler(enabled = !parkingRequired) {
        if (selectedQuest != null) {
            selectedQuestId = null
        } else {
            onBack()
        }
    }
    val title = stringResource(R.string.quest_header_title)
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Colors.background)
                    .focusProperties { canFocus = !parkingRequired }
                    .semantics { paneTitle = title },
        ) {
            val pointInHeader = maxWidth >= 1400.dp && LocalDensity.current.fontScale <= 1f
            Column(Modifier.fillMaxSize()) {
                QuestStatusPanel(state, onRetryQuests, onRetryWallet, onRetryAppearance)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val fontScale = LocalDensity.current.fontScale
                    val reference = maxWidth >= 1400.dp && maxHeight >= maxWidth * (1184f / 2560f) && fontScale <= 1f
                    val scale = if (reference) maxWidth.value / 2560f else 0.75f
                    val contentHeight = maxHeight
                    if (reference) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().testTag("quest-reference")) {
                                QuestHeader(
                                    friendId = state.appearance.friendId,
                                    scale = scale,
                                    onBack = {
                                        if (selectedQuest != null) {
                                            selectedQuestId = null
                                        } else {
                                            onBack()
                                        }
                                    },
                                    onHome = onHome,
                                    isDetail = selectedQuest != null,
                                    modifier =
                                        Modifier
                                            .offset(72.dp * scale, 36.dp * scale)
                                            .size(2416.dp * scale, 104.dp * scale),
                                )
                                QuestContent(
                                    state = state,
                                    selectedQuest = selectedQuest,
                                    selectedTab = selectedTab,
                                    scale = scale,
                                    isCompact = false,
                                    onSelectTab = { selectedTab = it },
                                    onSelectQuest = { selectedQuestId = it },
                                    onClaimReward = onClaimReward,
                                    onNavigateRoute = onNavigateRoute,
                                    pointInHeader = pointInHeader,
                                    modifier =
                                        Modifier.offset(72.dp * scale, 196.dp * scale).size(
                                            2416.dp * scale,
                                            contentHeight - 220.dp * scale,
                                        ),
                                )
                            }
                        }
                    } else {
                        val compactScale = (maxWidth.value / 1400f).coerceIn(0.55f, 0.9f)
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            QuestHeader(
                                friendId = state.appearance.friendId,
                                scale = compactScale,
                                onBack = {
                                    if (selectedQuest != null) {
                                        selectedQuestId = null
                                    } else {
                                        onBack()
                                    }
                                },
                                onHome = onHome,
                                isDetail = selectedQuest != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            QuestContent(
                                state = state,
                                selectedQuest = selectedQuest,
                                selectedTab = selectedTab,
                                scale = compactScale,
                                isCompact = true,
                                onSelectTab = { selectedTab = it },
                                onSelectQuest = { selectedQuestId = it },
                                onClaimReward = onClaimReward,
                                onNavigateRoute = onNavigateRoute,
                                pointInHeader = pointInHeader,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    val hiddenQuest = state.hiddenQuests.firstOrNull()
                    if (!parkingRequired &&
                        hiddenQuest != null &&
                        state.rewardSuccess == null &&
                        !state.isLoading &&
                        !state.observationFailed
                    ) {
                        QuestHiddenClaimModal(
                            quest = hiddenQuest,
                            friendId = state.appearance.friendId,
                            accessoryId = state.appearance.accessoryId,
                            outfitId = state.appearance.outfitId,
                            backgroundId = state.appearance.backgroundId,
                            scale = scale,
                            canClaim = state.canClaim,
                            isBusy = state.pendingQuestId != null,
                            errorMessage = state.errorMessage,
                            onClaim = { onClaimReward(hiddenQuest.id) },
                            onDismiss = { onDismissHiddenQuest(hiddenQuest.id) },
                        )
                    }
                    state.rewardSuccess?.takeUnless { parkingRequired }?.let { success ->
                        QuestRewardSuccessModal(
                            points = success.points,
                            bonusPoints = success.bonusPoints,
                            weatherMultiplier = success.weatherMultiplier,
                            friendId = state.appearance.friendId,
                            accessoryId = state.appearance.accessoryId,
                            outfitId = state.appearance.outfitId,
                            backgroundId = state.appearance.backgroundId,
                            scale = scale,
                            onConfirm = onDismissRewardSuccess,
                        )
                    }
                }
            }
            val badgeScale = maxWidth.value / 2560f
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 72.dp * badgeScale, top = 36.dp * badgeScale),
                horizontalArrangement = Arrangement.spacedBy(48.dp * badgeScale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pointInHeader) {
                    MobiMonPointSummary(
                        balance = (state.pointBalance as? PointBalanceState.Ready)?.balance,
                        modifier = Modifier.offset(y = 7.dp * badgeScale),
                        failed = state.pointBalance == PointBalanceState.Failed,
                        scale = badgeScale,
                    )
                }
                MobiMonParkingStatusBadge(
                    confirmed = parkingBadgeConfirmed,
                    scale = badgeScale,
                )
            }
        }
        if (parkingRequired) {
            MobiMonParkingInterruption(
                title = stringResource(R.string.quest_parking_popup_title),
                body = stringResource(R.string.quest_parking_popup_body),
                instruction = stringResource(R.string.quest_parking_popup_instruction),
                preserved = stringResource(R.string.quest_parking_popup_preserved),
                onHome = onHome ?: onBack,
            )
        }
    }
}

@Composable
private fun QuestContent(
    state: QuestScreenState,
    selectedQuest: QuestItemUiModel?,
    selectedTab: QuestFilterTab,
    scale: Float,
    isCompact: Boolean,
    onSelectTab: (QuestFilterTab) -> Unit,
    onSelectQuest: (String?) -> Unit,
    onClaimReward: (String) -> Unit,
    onNavigateRoute: (AppRoute) -> Unit,
    pointInHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selectedQuest != null) {
        QuestDetailContent(
            quest = selectedQuest,
            friendId = state.appearance.friendId,
            accessoryId = state.appearance.accessoryId,
            outfitId = state.appearance.outfitId,
            backgroundId = state.appearance.backgroundId,
            scale = scale,
            canClaim = state.canClaim,
            onBackToList = { onSelectQuest(null) },
            onExecute = { onNavigateRoute(selectedQuest.targetRoute) },
            onClaimReward = { onClaimReward(selectedQuest.id) },
            modifier = modifier,
            isCompact = isCompact,
        )
    } else {
        QuestListContent(
            quests = state.quests,
            friendId = state.appearance.friendId,
            accessoryId = state.appearance.accessoryId,
            outfitId = state.appearance.outfitId,
            backgroundId = state.appearance.backgroundId,
            scale = scale,
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            canClaim = state.canClaim,
            onSelectQuest = onSelectQuest,
            onClaimReward = onClaimReward,
            pointBalance = state.pointBalance,
            showPointInPanel = !pointInHeader,
            modifier = modifier,
            isCompact = isCompact,
        )
    }
}
