package com.monsters.mobimon.feature.quest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
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
    onClearRequestedQuest: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onHome: (() -> Unit)? = null,
    parkingBadgeConfirmed: Boolean = state.parkedVerified,
    parkingRequired: Boolean = false,
) {
    var selectedQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(QuestFilterTab.ALL) }
    val listScrollState = rememberScrollState()
    val detailScrollState = rememberScrollState()

    androidx.compose.runtime.LaunchedEffect(state.requestedQuestId) {
        if (state.requestedQuestId != null) {
            selectedQuestId = state.requestedQuestId
            onClearRequestedQuest()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        listScrollState.scrollTo(0)
        detailScrollState.scrollTo(0)
    }

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
                                    listScrollState = listScrollState,
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
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(if (selectedQuest == null) listScrollState else detailScrollState)
                                    .padding(24.dp),
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
                                listScrollState = listScrollState,
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
    listScrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val detailId = selectedQuest?.id
    val panelGap by animateDpAsState(
        targetValue = (if (detailId == null) 56 else 48).dp * scale,
        animationSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "quest panel gap",
    )
    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        QuestCompanionPanel(
            friendId = state.appearance.friendId,
            accessoryId = state.appearance.accessoryId,
            outfitId = state.appearance.outfitId,
            backgroundId = state.appearance.backgroundId,
            scale = scale,
            isDetail = detailId != null,
            isCompleted = selectedQuest?.status == QuestItemStatus.COMPLETED,
            isCompact = isCompact,
            modifier = panelModifier,
        )
    }
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        AnimatedContent(
            targetState = detailId,
            contentKey = { it != null },
            transitionSpec = {
                if (!motionEnabled) {
                    (EnterTransition.None togetherWith ExitTransition.None).using(null)
                } else {
                    val forward = targetState != null
                    val direction = if (forward) 1 else -1
                    val motion = tween<IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
                    val fade = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
                    (
                        (
                            slideInHorizontally(motion) { direction * it / 16 } +
                                fadeIn(fade) +
                                scaleIn(
                                    initialScale = 0.97f,
                                    transformOrigin = TransformOrigin(1f, 0.5f),
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                )
                        ) togetherWith
                            (slideOutHorizontally(motion) { -direction * it / 32 } + fadeOut(fade))
                    ).using(null)
                }
            },
            modifier = contentModifier,
            label = "quest detail transition",
        ) { currentId ->
            val currentQuest = state.quests.firstOrNull { it.id == currentId }
            val active = currentId == detailId
            val activeModifier = if (active) Modifier else Modifier.clearAndSetSemantics {}
            if (currentQuest != null) {
                QuestDetailCard(
                    quest = currentQuest,
                    canClaim = state.canClaim,
                    isClaimPending = state.pendingQuestId == currentQuest.id,
                    scale = scale,
                    onBackToList = { if (active) onSelectQuest(null) },
                    onExecute = { if (active) onNavigateRoute(currentQuest.targetRoute) },
                    onClaimReward = { if (active) onClaimReward(currentQuest.id) },
                    modifier = activeModifier.fillMaxSize(),
                    isCompact = isCompact,
                )
            } else {
                QuestRightPanel(
                    quests = state.quests,
                    scale = scale,
                    selectedTab = selectedTab,
                    onSelectTab = { if (active) onSelectTab(it) },
                    canClaim = state.canClaim,
                    pendingQuestId = state.pendingQuestId,
                    isCompact = isCompact,
                    onSelectQuest = { if (active) onSelectQuest(it) },
                    onClaimReward = { if (active) onClaimReward(it) },
                    pointBalance = state.pointBalance,
                    showPointSummary = !pointInHeader,
                    scrollState = listScrollState,
                    modifier = activeModifier.fillMaxSize(),
                )
            }
        }
    }
    if (isCompact) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            panel(Modifier.fillMaxWidth())
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(panelGap),
        ) {
            panel(Modifier.fillMaxHeight())
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}
