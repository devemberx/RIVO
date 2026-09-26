package com.monsters.mobimon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.R
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.navigation.AiRoute
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.navigation.FeatureRegistry
import com.monsters.mobimon.core.navigation.LocalDebugSettingsAvailable
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.presentation.parkedVerified
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.auth.AssumedOnlineConversationNetworkStatus
import com.monsters.mobimon.feature.auth.ConversationNetworkStatus
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.auth.ConversationViewModel
import com.monsters.mobimon.feature.auth.UnavailableConversationSpeechInput
import com.monsters.mobimon.runtime.AppUseStateSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val NAVIGATION_MOTION_DURATION_MILLIS = 220
internal const val CONVERSATION_REVEAL_DURATION_MILLIS = 300
private const val DEBUGGER_UNLOCK_TAPS = 10
private const val DEBUGGER_UNLOCK_NOTICE_THRESHOLD = 5
private const val DEBUGGER_UNLOCK_RESET_MILLIS = 3_000L
private const val DEBUGGER_UNLOCK_TOAST_MILLIS = 3_000L

private data class DestinationReveal(
    val route: AppRoute,
    val origin: Rect,
)

@Composable
fun MobiMonApp(
    entries: Set<FeatureEntry>,
    appUse: AppUseStateSource,
    companion: CompanionAppearancePresentation,
    settings: SettingsRepository,
    authentication: GitHubAuthentication,
    conversation: ConversationProvider,
    vehicle: VehiclePresentation,
    networkStatus: ConversationNetworkStatus = AssumedOnlineConversationNetworkStatus,
    speechInput: ConversationSpeechInput = UnavailableConversationSpeechInput,
) {
    val state by appUse.states.collectAsStateWithLifecycle()
    val session by authentication.session.collectAsStateWithLifecycle()
    val conversationFactory =
        remember(authentication, conversation, networkStatus, speechInput) {
            viewModelFactory {
                initializer {
                    ConversationViewModel(
                        authentication,
                        conversation,
                        networkStatus,
                        speechInput,
                    )
                }
            }
        }
    val conversationModel: ConversationViewModel = viewModel(factory = conversationFactory)
    val parked = vehicle.snapshot().parkedVerified
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canCheck = parked && state == AppUseState.ALLOWED
    LaunchedEffect(conversationModel, lifecycle, canCheck) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            conversationModel.setForegroundAllowed(canCheck, refresh = canCheck)
            try {
                awaitCancellation()
            } finally {
                conversationModel.setForegroundAllowed(false)
            }
        }
    }
    val appearance = companion.state()
    val currentSession = session
    val activeFriendId = appearance.inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND)
    val debugResetScope = rememberCoroutineScope()
    MobiMonContent(
        entries = entries,
        appUseState = state,
        activeFriendId = activeFriendId,
        activeAccessoryId = appearance.accessoryId,
        activeOutfitId = appearance.outfitId,
        activeBackgroundId = appearance.backgroundId,
        conversationAuthenticated =
            currentSession is GitHubSession.Authenticated ||
                currentSession == GitHubSession.Restoring ||
                currentSession is GitHubSession.Failure &&
                currentSession.problem in
                setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER),
        currentConversationAuthentication = {
            val latest = authentication.session.value
            latest is GitHubSession.Authenticated ||
                latest == GitHubSession.Restoring ||
                latest is GitHubSession.Failure &&
                latest.problem in setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
        },
        currentConversationStartReady = {
            when (val latest = authentication.session.value) {
                is GitHubSession.Authenticated -> true
                is GitHubSession.Failure ->
                    latest.problem in setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
                else -> false
            }
        },
        onReleaseDebuggerUnlocked = {
            debugResetScope.launch {
                settings.setDebugModeEnabled(false)
            }
        },
    )
}

internal val ShellSaver =
    listSaver<ShellState, String>(
        save = { listOf(it.route.name, it.connectionOrigin.name) },
        restore = { saved ->
            val routeIndex = if (saved.firstOrNull() in setOf("PET", "VEHICLE")) 1 else 0
            val originIndex = routeIndex + 1
            ShellState(
                route = AppRoute.entries.firstOrNull { it.name == saved.getOrNull(routeIndex) } ?: CompanionRoute.HOME,
                connectionOrigin =
                    when (saved.getOrNull(originIndex)) {
                        CompanionRoute.SETTINGS.name -> CompanionRoute.SETTINGS
                        AiRoute.CONVERSATION.name -> AiRoute.CONVERSATION
                        else -> CompanionRoute.HOME
                    },
            )
        },
    )

/** App shell owns only navigation, restoration, menu and the global AAOS restriction gate. */
@Composable
@OptIn(ExperimentalAnimationApi::class)
fun MobiMonContent(
    entries: Set<FeatureEntry>,
    modifier: Modifier = Modifier,
    appUseState: AppUseState = AppUseState.UNAVAILABLE,
    activeFriendId: String? = null,
    activeAccessoryId: String? = null,
    activeOutfitId: String? = null,
    activeBackgroundId: String? = null,
    reducedMotion: Boolean = false,
    conversationAuthenticated: Boolean = false,
    currentConversationAuthentication: () -> Boolean = { conversationAuthenticated },
    currentConversationStartReady: () -> Boolean = currentConversationAuthentication,
    debugOverlay: @Composable () -> Unit = { DebugOverlay() },
    debugSettingsAvailableByDefault: Boolean = BuildConfig.DEBUG,
    onReleaseDebuggerUnlocked: () -> Unit = {},
) {
    val registry = remember(entries) { FeatureRegistry(entries) }
    var savedShell by rememberSaveable(stateSaver = ShellSaver) { mutableStateOf(ShellState()) }
    val authenticated = currentConversationAuthentication()
    val shell = savedShell.requireConversationAccount(authenticated)
    var returning by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf<DestinationReveal?>(null) }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var debuggerUnlockTapCount by rememberSaveable { mutableIntStateOf(0) }
    var debuggerUnlockTapVersion by remember { mutableIntStateOf(0) }
    var debuggerUnlockNotice by remember { mutableStateOf<String?>(null) }
    var debuggerUnlockNoticeVersion by remember { mutableIntStateOf(0) }
    val debuggerSettingsAvailable = debugSettingsAvailableByDefault || debuggerUnlockTapCount >= DEBUGGER_UNLOCK_TAPS
    val context = LocalContext.current
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(
        debugSettingsAvailableByDefault,
        debuggerSettingsAvailable,
        debuggerUnlockTapVersion,
    ) {
        if (!debugSettingsAvailableByDefault && !debuggerSettingsAvailable && debuggerUnlockTapCount > 0) {
            delay(DEBUGGER_UNLOCK_RESET_MILLIS)
            debuggerUnlockTapCount = 0
        }
    }
    LaunchedEffect(debuggerUnlockNoticeVersion) {
        if (debuggerUnlockNotice != null) {
            delay(DEBUGGER_UNLOCK_TOAST_MILLIS)
            debuggerUnlockNotice = null
        }
    }
    val navigator =
        FeatureNavigator(
            navigate = { route ->
                reveal = null
                returning = false
                savedShell = shell.navigate(route, currentConversationStartReady())
            },
            back = {
                val previous = shell
                savedShell = previous.back()
                if (savedShell.route != previous.route) returning = true
            },
            returnHome = {
                returning = true
                savedShell = shell.returnHome()
            },
            openMenu = { savedShell = shell.openMenu() },
            navigateFrom = { requested, bounds ->
                val next = shell.navigate(requested, currentConversationStartReady())
                val route = next.route
                val coordinates = contentCoordinates?.takeIf { it.isAttached }
                reveal =
                    if (
                        shell.route == CompanionRoute.HOME &&
                        route in setOf(AiRoute.COPILOT, AiRoute.CONVERSATION) &&
                        coordinates != null &&
                        !bounds.isEmpty
                    ) {
                        val local = bounds.translate(-coordinates.positionInRoot())
                        DestinationReveal(
                            route,
                            Rect(
                                local.left / coordinates.size.width,
                                local.top / coordinates.size.height,
                                local.right / coordinates.size.width,
                                local.bottom / coordinates.size.height,
                            ),
                        )
                    } else {
                        null
                    }
                returning = false
                savedShell = next
            },
        )
    CompositionLocalProvider(
        LocalMobiMonMotionEnabled provides !reducedMotion,
        LocalDebugSettingsAvailable provides debuggerSettingsAvailable,
    ) {
        MobiMonTheme {
            Surface(modifier = modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().onGloballyPositioned { contentCoordinates = it }) {
                        BackHandler(enabled = shell.menuOpen || shell.route != CompanionRoute.HOME) {
                            navigator.back()
                        }
                        AnimatedContent(
                            targetState = shell.route,
                            modifier = Modifier.fillMaxSize(),
                            contentKey = { it.name },
                            transitionSpec = {
                                val anchored = reveal
                                if (
                                    anchored != null &&
                                    setOf(initialState, targetState) == setOf(CompanionRoute.HOME, anchored.route)
                                ) {
                                    (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
                                        .using(null)
                                        .apply {
                                            targetContentZIndex =
                                                if (targetState == CompanionRoute.HOME) 0f else 1f
                                        }
                                } else {
                                    val direction = if (returning) -1 else 1
                                    val motion =
                                        tween<IntOffset>(
                                            if (reducedMotion) 0 else NAVIGATION_MOTION_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing,
                                        )
                                    val fade =
                                        tween<Float>(
                                            if (reducedMotion) 0 else NAVIGATION_MOTION_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing,
                                        )
                                    (
                                        (
                                            slideInHorizontally(motion) { direction * it / 18 } + fadeIn(fade)
                                        ) togetherWith
                                            (slideOutHorizontally(motion) { -direction * it / 36 } + fadeOut(fade))
                                    ).using(null).apply { targetContentZIndex = 1f }
                                }
                            },
                            label = "destination change",
                        ) { route ->
                            val active = route == shell.route
                            val routeNavigator =
                                FeatureNavigator(
                                    navigate = { if (active) navigator.navigate(it) },
                                    back = { if (active) navigator.back() },
                                    returnHome = { if (active) navigator.returnHome() },
                                    openMenu = { if (active) navigator.openMenu() },
                                    navigateFrom = { destination, bounds ->
                                        if (active) navigator.navigateFrom(destination, bounds)
                                    },
                                )
                            val anchored = reveal?.takeIf { it.route == route }
                            val revealModifier =
                                if (anchored != null) {
                                    val progress =
                                        transition.animateFloat(
                                            transitionSpec = {
                                                tween(
                                                    if (reducedMotion) {
                                                        0
                                                    } else if (targetState ==
                                                        EnterExitState.Visible
                                                    ) {
                                                        CONVERSATION_REVEAL_DURATION_MILLIS
                                                    } else {
                                                        NAVIGATION_MOTION_DURATION_MILLIS
                                                    },
                                                    easing = FastOutSlowInEasing,
                                                )
                                            },
                                            label = "conversation reveal",
                                        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
                                    Modifier.revealFrom(anchored.origin) { progress.value }
                                } else {
                                    Modifier
                                }
                            Box(
                                Modifier.fillMaxSize().then(revealModifier).then(
                                    if (active) Modifier else Modifier.clearAndSetSemantics {},
                                ),
                            ) {
                                stateHolder.SaveableStateProvider(route.name) {
                                    registry[route].Content(route, routeNavigator, Modifier)
                                }
                                if (!active) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {}
                                            .clearAndSetSemantics {},
                                    )
                                }
                            }
                        }
                    }
                    CompanionMenu(
                        visible = shell.menuOpen,
                        currentRoute = shell.route,
                        onClose = navigator.back,
                        onNavigate = navigator.navigate,
                        onVersionClick = {
                            if (
                                appUseState == AppUseState.ALLOWED &&
                                !debugSettingsAvailableByDefault &&
                                debuggerUnlockTapCount < DEBUGGER_UNLOCK_TAPS
                            ) {
                                debuggerUnlockTapCount += 1
                                debuggerUnlockTapVersion += 1
                                val remaining = DEBUGGER_UNLOCK_TAPS - debuggerUnlockTapCount
                                debuggerUnlockNotice =
                                    if (remaining == 0) {
                                        onReleaseDebuggerUnlocked()
                                        context.getString(R.string.debugger_unlocked)
                                    } else if (remaining <= DEBUGGER_UNLOCK_NOTICE_THRESHOLD) {
                                        context.getString(R.string.debugger_unlock_remaining, remaining)
                                    } else {
                                        null
                                    }
                                if (debuggerUnlockNotice != null) {
                                    debuggerUnlockNoticeVersion += 1
                                }
                            }
                        },
                        activeFriendId = activeFriendId,
                        accessoryId = activeAccessoryId,
                        outfitId = activeOutfitId,
                        backgroundId = activeBackgroundId,
                    )
                    if (appUseState == AppUseState.ALLOWED) {
                        debugOverlay()
                    }
                    DebuggerUnlockToast(
                        message = debuggerUnlockNotice,
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DebuggerUnlockToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 6.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
