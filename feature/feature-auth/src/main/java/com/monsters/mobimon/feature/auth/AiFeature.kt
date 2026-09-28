package com.monsters.mobimon.feature.auth

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.navigation.AiRoute
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.presentation.parkedVerified
import com.monsters.mobimon.core.presentation.parkingBadgeConfirmed
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonDimensions
import com.monsters.mobimon.core.ui.MobiMonMessage
import kotlinx.coroutines.awaitCancellation

/** AI-owned routes keep authentication separate from conversation readiness. */
class AiFeature(
    private val pets: PetRepository,
    private val points: PointEconomy,
    private val vehicle: VehiclePresentation,
    private val authentication: GitHubAuthentication,
    private val conversation: ConversationProvider,
    private val networkStatus: ConversationNetworkStatus = AssumedOnlineConversationNetworkStatus,
    private val speechInput: ConversationSpeechInput = UnavailableConversationSpeechInput,
    private val conversationStore: ConversationStore? = null,
) : FeatureEntry {
    override val routes = setOf(AiRoute.COPILOT, AiRoute.CONVERSATION)

    @Composable
    override fun Content(
        route: AppRoute,
        navigator: FeatureNavigator,
        modifier: Modifier,
    ) {
        val snapshot = vehicle.snapshot()
        val authenticationFactory =
            remember(this) {
                viewModelFactory { initializer { GitHubAuthenticationViewModel(authentication) } }
            }
        val authenticationModel: GitHubAuthenticationViewModel = viewModel(factory = authenticationFactory)
        val authenticationState by authenticationModel.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(authenticationModel, lifecycle, snapshot.parkedVerified, route) {
            if (route != AiRoute.COPILOT) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authenticationModel.activate(snapshot.parkedVerified)
                try {
                    awaitCancellation()
                } finally {
                    authenticationModel.deactivate()
                }
            }
        }
        val qrCode = githubQrCode((authenticationState as? CopilotUiState.Waiting)?.verificationUri)
        val factory =
            remember(this) { viewModelFactory { initializer { AiCompanionViewModel(pets, points) } } }
        val model: AiCompanionViewModel = viewModel(factory = factory)
        val companion by model.state.collectAsStateWithLifecycle()
        val profile = companion.profile
        val session by authentication.session.collectAsStateWithLifecycle()
        val online by networkStatus.online.collectAsStateWithLifecycle()
        val conversationFactory =
            remember(this) {
                viewModelFactory {
                    initializer {
                        ConversationViewModel(
                            authentication,
                            conversation,
                            networkStatus,
                            speechInput,
                            conversationStore,
                        )
                    }
                }
            }
        val conversationModel: ConversationViewModel = viewModel(factory = conversationFactory)
        val conversationState by conversationModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var voicePermissionRequest by remember(conversationModel) { mutableLongStateOf(0L) }
        val microphonePermission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                val activity =
                    generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                        .filterIsInstance<Activity>()
                        .firstOrNull()
                val showSettingsHint =
                    !granted &&
                        (
                            activity == null ||
                                !ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    Manifest.permission.RECORD_AUDIO,
                                )
                        )
                conversationModel.voicePermissionResult(voicePermissionRequest, granted, showSettingsHint)
            }
        val startVoice: () -> Unit = {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            conversationModel.requestVoice(granted)?.let { request ->
                voicePermissionRequest = request
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        val friendId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND) ?: "friend:mobi"
        LaunchedEffect(conversationModel, profile?.id, friendId) {
            profile?.id?.let { conversationModel.bind(it, friendId) }
        }
        LaunchedEffect(conversationModel, lifecycle, route, snapshot.parkedVerified) {
            if (route != AiRoute.CONVERSATION) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                conversationModel.activate(snapshot.parkedVerified)
                try {
                    awaitCancellation()
                } finally {
                    conversationModel.deactivate()
                }
            }
        }
        LaunchedEffect(conversationModel, lifecycle, route) {
            if (route != AiRoute.CONVERSATION) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                conversationModel.setVoiceResumed(true)
                try {
                    awaitCancellation()
                } finally {
                    conversationModel.setVoiceResumed(false)
                }
            }
        }
        Column(modifier.fillMaxSize()) {
            if (companion.failed && (route != AiRoute.CONVERSATION || snapshot.parkedVerified)) {
                Row(
                    Modifier.fillMaxWidth().padding(MobiMonDimensions.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(MobiMonDimensions.contentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobiMonMessage(
                        stringResource(
                            if (profile ==
                                null
                            ) {
                                R.string.ai_context_failed
                            } else {
                                R.string.ai_context_update_failed
                            },
                        ),
                        Modifier.weight(1f),
                        isError = true,
                    )
                    MobiMonButton(onClick = model::retry) { Text(stringResource(R.string.ai_retry)) }
                }
            }
            if (route == AiRoute.CONVERSATION) {
                val currentSession = session
                ConversationScreen(
                    state =
                        conversationState.copy(
                            connection =
                                if (profile == null) {
                                    ConversationConnection.CHECKING
                                } else {
                                    when (currentSession) {
                                        is GitHubSession.Authenticated -> conversationState.connection
                                        GitHubSession.Restoring -> ConversationConnection.CHECKING
                                        is GitHubSession.Failure ->
                                            if (currentSession.problem in
                                                setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
                                            ) {
                                                conversationState.connection
                                            } else {
                                                ConversationConnection.SIGNED_OUT
                                            }
                                        GitHubSession.SignedOut -> ConversationConnection.SIGNED_OUT
                                    }
                                },
                            connectionProblem =
                                if (profile == null) {
                                    null
                                } else {
                                    when (currentSession) {
                                        is GitHubSession.Failure ->
                                            when (currentSession.problem) {
                                                AuthenticationProblem.NETWORK -> ConversationProblem.NETWORK
                                                AuthenticationProblem.PROVIDER ->
                                                    if (online) {
                                                        ConversationProblem.SERVICE
                                                    } else {
                                                        ConversationProblem.NETWORK
                                                    }
                                                else -> null
                                            }
                                        else -> conversationState.connectionProblem
                                    }
                                },
                        ),
                    draft = conversationModel.draft,
                    onDraftChange = conversationModel::edit,
                    onSend = conversationModel::send,
                    onCancelReply = conversationModel::cancel,
                    onBack = {
                        conversationModel.clearDraftForHome()
                        navigator.back()
                    },
                    onOpenConnection = { if (snapshot.parkedVerified) navigator.navigate(AiRoute.COPILOT) },
                    modifier = Modifier.weight(1f),
                    interactionAllowed = snapshot.parkedVerified,
                    parkingBadgeConfirmed = snapshot.parkingBadgeConfirmed,
                    simulatedVehicle = snapshot.source == SignalSource.SIMULATED,
                    friendId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND) ?: "friend:mobi",
                    appearanceKey = profile?.appearance?.name ?: "GOLDEN",
                    accessoryId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.ACCESSORY),
                    outfitId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.OUTFIT),
                    onRetry = {
                        when (conversationState.problem) {
                            ConversationProblem.ACCOUNT -> navigator.navigate(AiRoute.COPILOT)
                            ConversationProblem.ACCESS -> conversationModel.retryConnection()
                            ConversationProblem.LIMIT -> conversationModel.newConversation()
                            else -> conversationModel.retry()
                        }
                    },
                    onDismissFailure = conversationModel::dismissFailure,
                    onNewConversation = conversationModel::newConversation,
                    onReturnHome = {
                        conversationModel.clearDraftForHome()
                        navigator.returnHome()
                    },
                    onRecheckConnection = conversationModel::retryConnection,
                    onStartVoice = startVoice,
                    onStopVoice = conversationModel::stopVoice,
                    onCancelVoice = conversationModel::cancelVoice,
                    onDismissVoiceProblem = conversationModel::dismissVoiceProblem,
                    onFinishVoiceReview = conversationModel::finishVoiceReview,
                )
                return@Column
            }
            CopilotConnectionScreen(
                state = authenticationState,
                qrCode = qrCode,
                onAction = { action ->
                    authenticationModel.action(action)
                    when (action) {
                        CopilotAction.BACK, CopilotAction.CANCEL -> navigator.back()
                        CopilotAction.OPEN_SETTINGS -> navigator.navigate(CompanionRoute.SETTINGS)
                        CopilotAction.START_CONVERSATION ->
                            if (snapshot.parkedVerified) {
                                navigator.navigate(
                                    AiRoute.CONVERSATION,
                                )
                            }
                        else -> Unit
                    }
                },
                modifier = Modifier.weight(1f),
                interactionAllowed = snapshot.parkedVerified,
                parkingBadgeConfirmed = snapshot.parkingBadgeConfirmed,
                simulatedVehicle = snapshot.source == SignalSource.SIMULATED,
                friendId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND) ?: "friend:mobi",
                appearanceKey = profile?.appearance?.name ?: "GOLDEN",
                accessoryId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.ACCESSORY),
                outfitId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.OUTFIT),
                backgroundId = companion.inventory?.equippedItemIds?.get(CosmeticSlot.BACKGROUND),
            )
        }
    }
}
