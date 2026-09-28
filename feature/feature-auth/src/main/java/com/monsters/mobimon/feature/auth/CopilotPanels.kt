package com.monsters.mobimon.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.ui.CompanionIcon
import com.monsters.mobimon.core.ui.MobiMonListItem
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun CopilotPanel(
    state: CopilotUiState,
    onAction: (CopilotAction) -> Unit,
    interactionAllowed: Boolean,
    friend: String,
    qrCode: Painter?,
    scale: Float,
    modifier: Modifier = Modifier,
    reference: Boolean = false,
) {
    if (reference && state.hasReferenceLayout(interactionAllowed, qrCode != null)) {
        CopilotReferencePanel(state, onAction, friend, qrCode, scale, interactionAllowed, modifier)
        return
    }
    Surface(modifier.testTag("copilot-panel"), shape = RoundedCornerShape(48.dp * scale), color = Colors.panel) {
        Column(Modifier.padding(64.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            Column(
                if (reference) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,
                verticalArrangement = Arrangement.spacedBy(24.dp * scale),
            ) {
                when (state) {
                    is CopilotUiState.AuthenticationStatus -> {
                        if (state.problem != AuthenticationProblem.NETWORK) {
                            ConnectionSteps(if (state.account != null) 1 else 0, scale)
                        }
                        PanelHeading(
                            if (state.account !=
                                null
                            ) {
                                R.string.github_authenticated
                            } else {
                                R.string.github_authentication
                            },
                            null,
                            scale,
                        )
                        if (state.account != null) {
                            AccountCard(state.account, null, scale) {
                                AccountDisconnectAction(
                                    { onAction(CopilotAction.CONFIRM_DISCONNECT) },
                                    interactionAllowed && !state.pending,
                                    scale,
                                )
                            }
                        }
                        if (state.pending) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            PanelText(R.string.github_checking, scale)
                        } else if (state.problem != null) {
                            Feedback(stringResource(state.problem.message()), scale)
                        } else {
                            PanelText(R.string.github_authenticated_note, scale)
                        }
                    }
                    is CopilotUiState.Introduction -> {
                        ConnectionSteps(0, scale)
                        PanelHeading(R.string.copilot_intro_heading, R.string.copilot_intro_subtitle, scale)
                        PanelText(R.string.copilot_intro_disclosure, scale)
                        HorizontalDivider(color = Colors.border.copy(alpha = 0.4f))
                        if (state.connectionUnavailable) {
                            Text(
                                stringResource(R.string.copilot_unavailable),
                                style = copilotStyle(30f, scale),
                                color = Colors.warning,
                            )
                        } else {
                            PanelText(R.string.copilot_intro_note, scale, muted = true)
                        }
                    }
                    is CopilotUiState.Waiting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                PanelHeading(
                                    if (state.showAddress ||
                                        qrCode == null
                                    ) {
                                        R.string.copilot_address_heading
                                    } else {
                                        R.string.copilot_waiting_heading
                                    },
                                    if (state.showAddress ||
                                        qrCode == null
                                    ) {
                                        R.string.copilot_address_instruction
                                    } else {
                                        R.string.copilot_waiting_instruction
                                    },
                                    scale,
                                )
                            }
                            CopilotIconButton(
                                CompanionIcon.CLOSE,
                                stringResource(R.string.copilot_close),
                                { onAction(CopilotAction.CANCEL) },
                            )
                        }
                        WaitingDetails(state, qrCode, scale)
                        if (state.error != null) Feedback(state.error, scale)
                        if (state.retrying) Feedback(stringResource(R.string.github_network), scale)
                    }
                    CopilotUiState.Expired -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.copilot_step_account),
                                Modifier.weight(1f),
                                style = copilotStyle(32f, scale),
                                color = Colors.muted,
                            )
                            CopilotIconButton(
                                CompanionIcon.CLOSE,
                                stringResource(R.string.copilot_close),
                                { onAction(CopilotAction.CANCEL) },
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 80.dp * scale),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp * scale),
                        ) {
                            ExpiryClock(scale)
                            Text(
                                stringResource(R.string.copilot_expired_heading),
                                Modifier.semantics {
                                    heading()
                                },
                                style = copilotStyle(52f, scale, true),
                                textAlign = TextAlign.Center,
                            )
                            PanelText(R.string.copilot_expired_note, scale, muted = true)
                        }
                    }
                    is CopilotUiState.Connected -> {
                        ConnectionSteps(2, scale)
                        Text(
                            stringResource(R.string.copilot_connected_heading),
                            Modifier.semantics {
                                heading()
                            },
                            style = copilotStyle(52f, scale, true),
                        )
                        Text(
                            stringResource(R.string.copilot_connected_subtitle, friend),
                            style = copilotStyle(32f, scale),
                            color = Colors.muted,
                        )
                        AccountCard(state.account, stringResource(R.string.copilot_this_vehicle), scale)
                        PanelText(R.string.copilot_connected_note, scale)
                    }
                    is CopilotUiState.Reconnect -> {
                        PanelHeading(R.string.copilot_reconnect_heading, null, scale)
                        Text(state.reason, style = copilotStyle(32f, scale), color = Colors.warning)
                        PanelText(R.string.copilot_reconnect_note, scale, muted = true)
                        AccountCard(
                            stringResource(R.string.copilot_connected_account, state.account),
                            stringResource(R.string.copilot_draft_preserved),
                            scale,
                        )
                    }
                    is CopilotUiState.AccessCheck -> {
                        ConnectionSteps(1, scale)
                        PanelHeading(R.string.copilot_access_heading, R.string.copilot_access_subtitle, scale)
                        AccountCard(state.account, stringResource(state.reason.message()), scale)
                        if (state.detail != null) Feedback(state.detail, scale)
                        if (state.checking || state.reason == CopilotAccessIssue.CHECKING) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth(),
                                color = Colors.accent,
                                trackColor = Colors.raised,
                            )
                        }
                        PanelText(R.string.copilot_access_note, scale, muted = true)
                    }
                    is CopilotUiState.Disconnect -> {
                        PanelHeading(R.string.copilot_disconnect_heading, null, scale)
                        AccountCard(state.account, null, scale)
                        PanelText(R.string.copilot_disconnect_note, scale)
                        if (state.disconnecting) Feedback(stringResource(R.string.copilot_disconnecting), scale)
                        if (state.error != null) Feedback(state.error, scale)
                    }
                }
                if (!interactionAllowed) Feedback(stringResource(R.string.copilot_parked_notice), scale)
            }
            PanelActions(state, onAction, interactionAllowed, qrCode != null, friend, scale)
            if (state is CopilotUiState.Introduction) {
                PanelText(
                    R.string.copilot_policy_note,
                    scale,
                    muted = true,
                    size = 24f,
                )
            }
        }
    }
}

@Composable
private fun WaitingDetails(
    state: CopilotUiState.Waiting,
    qrCode: Painter?,
    scale: Float,
) {
    BoxWithConstraints {
        val address = state.showAddress || qrCode == null
        val qrSize = (532.dp * scale).coerceAtMost(maxWidth)
        val horizontalDetails = maxWidth / LocalDensity.current.fontScale >= 560.dp
        if (address) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                if (qrCode == null && !state.showAddress) PanelText(R.string.copilot_qr_missing, scale, muted = true)
                PanelText(R.string.copilot_address_label, scale, muted = true, size = 28f)
                SelectionContainer {
                    Text(
                        state.verificationUri.removePrefix("https://"),
                        style = copilotStyle(44f, scale),
                        color = Colors.accent,
                    )
                }
                HorizontalDivider(color = Colors.border.copy(alpha = 0.4f))
                ApprovalDetails(state, scale, horizontal = horizontalDetails)
            }
        } else if (maxWidth / LocalDensity.current.fontScale < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Image(
                    requireNotNull(qrCode),
                    stringResource(R.string.copilot_qr_description),
                    Modifier
                        .size(qrSize)
                        .background(Color.White)
                        .padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
                ApprovalDetails(state, scale)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    requireNotNull(qrCode),
                    stringResource(R.string.copilot_qr_description),
                    Modifier.size(532.dp * scale).background(Color.White, RoundedCornerShape(32.dp * scale)).padding(
                        32.dp * scale,
                    ),
                    contentScale = ContentScale.Fit,
                )
                Box(Modifier.weight(1f)) { ApprovalDetails(state, scale) }
            }
        }
    }
}

@Composable
private fun ExpiryClock(scale: Float) {
    Box(
        Modifier.size(192.dp * scale).background(Colors.raised, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.copilot_clock), null, Modifier.size(88.dp * scale))
    }
}

@Composable
private fun ApprovalDetails(
    state: CopilotUiState.Waiting,
    scale: Float,
    horizontal: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        if (horizontal) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp * scale)) {
                Column(Modifier.weight(1f)) { ApprovalCode(state.userCode, scale) }
                Column(Modifier.weight(1f)) { ApprovalTimer(state.remainingSeconds, scale) }
            }
        } else {
            ApprovalTimer(state.remainingSeconds, scale)
            ApprovalCode(state.userCode, scale)
        }
        Feedback(
            stringResource(if (state.checking) R.string.copilot_checking_status else R.string.copilot_waiting_status),
            scale,
            Colors.accent,
        )
    }
}

@Composable
private fun ApprovalTimer(
    seconds: Int,
    scale: Float,
) {
    PanelText(R.string.copilot_time_label, scale, muted = true, size = 28f)
    Text(
        stringResource(R.string.copilot_time_value, seconds / 60, seconds % 60),
        Modifier.testTag("copilot-countdown"),
        style = copilotStyle(80f, scale, true),
        color = Colors.warning,
    )
}

@Composable
private fun ApprovalCode(
    code: String,
    scale: Float,
) {
    PanelText(R.string.copilot_code_label, scale, muted = true, size = 28f)
    SelectionContainer { Text(code, style = copilotStyle(52f, scale, true)) }
}

@Composable
private fun PanelActions(
    state: CopilotUiState,
    onAction: (CopilotAction) -> Unit,
    allowed: Boolean,
    hasQr: Boolean,
    friend: String,
    scale: Float,
) {
    val buttons =
        when (state) {
            is CopilotUiState.AuthenticationStatus ->
                when {
                    state.pending -> listOf(ActionButton(stringResource(R.string.copilot_later), CopilotAction.CANCEL))
                    state.account != null ->
                        listOf(
                            ActionButton(
                                stringResource(R.string.copilot_chat, friend),
                                CopilotAction.START_CONVERSATION,
                                allowed && state.problem == null,
                            ),
                            ActionButton(stringResource(R.string.copilot_settings), CopilotAction.OPEN_SETTINGS),
                        )
                    else ->
                        buildList {
                            add(
                                ActionButton(
                                    stringResource(
                                        if (state.problem == AuthenticationProblem.REAUTHENTICATION ||
                                            state.problem == AuthenticationProblem.DENIED
                                        ) {
                                            R.string.copilot_reconnect
                                        } else {
                                            R.string.copilot_recheck
                                        },
                                    ),
                                    if (state.problem == AuthenticationProblem.REAUTHENTICATION ||
                                        state.problem == AuthenticationProblem.DENIED
                                    ) {
                                        CopilotAction.REQUEST_CODE
                                    } else {
                                        CopilotAction.RECHECK
                                    },
                                    allowed,
                                ),
                            )
                            if (state.problem != AuthenticationProblem.NETWORK) {
                                add(
                                    ActionButton(
                                        stringResource(R.string.github_clear_connection),
                                        CopilotAction.DISCONNECT,
                                        allowed,
                                    ),
                                )
                            }
                        }
                }
            is CopilotUiState.Introduction ->
                listOf(
                    ActionButton(
                        stringResource(R.string.copilot_connect),
                        CopilotAction.REQUEST_CODE,
                        allowed && !state.connectionUnavailable,
                    ),
                    ActionButton(stringResource(R.string.copilot_later), CopilotAction.CANCEL),
                )
            is CopilotUiState.Waiting ->
                buildList {
                    if (!state.showAddress && hasQr) {
                        add(ActionButton(stringResource(R.string.copilot_qr_help), CopilotAction.SHOW_ADDRESS))
                    } else if (hasQr) {
                        add(ActionButton(stringResource(R.string.copilot_back_to_qr), CopilotAction.SHOW_QR))
                    }
                    add(
                        ActionButton(
                            stringResource(R.string.copilot_approved),
                            CopilotAction.RECHECK,
                            allowed && !state.checking,
                        ),
                    )
                }
            CopilotUiState.Expired ->
                listOf(
                    ActionButton(stringResource(R.string.copilot_new_code), CopilotAction.REQUEST_CODE, allowed),
                )
            is CopilotUiState.Connected ->
                listOf(
                    ActionButton(
                        stringResource(R.string.copilot_chat, friend),
                        CopilotAction.START_CONVERSATION,
                        allowed,
                    ),
                    ActionButton(stringResource(R.string.copilot_settings), CopilotAction.OPEN_SETTINGS),
                )
            is CopilotUiState.Reconnect ->
                listOf(
                    ActionButton(stringResource(R.string.copilot_reconnect), CopilotAction.REQUEST_CODE, allowed),
                    ActionButton(stringResource(R.string.copilot_later), CopilotAction.CANCEL),
                )
            is CopilotUiState.AccessCheck ->
                listOf(
                    ActionButton(stringResource(R.string.copilot_review_access), CopilotAction.REVIEW_ACCESS, allowed),
                    ActionButton(
                        stringResource(R.string.copilot_recheck),
                        CopilotAction.RECHECK,
                        allowed && !state.checking && state.reason != CopilotAccessIssue.CHECKING,
                    ),
                )
            is CopilotUiState.Disconnect ->
                listOf(
                    ActionButton(
                        stringResource(R.string.copilot_keep),
                        CopilotAction.KEEP_CONNECTION,
                        !state.disconnecting,
                    ),
                    ActionButton(
                        stringResource(R.string.copilot_disconnect),
                        CopilotAction.DISCONNECT,
                        allowed && !state.disconnecting,
                    ),
                )
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 560.dp
        val expiryActionWidth = (640.dp * scale).coerceAtMost(maxWidth)
        if (state == CopilotUiState.Expired) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ActionControl(
                    buttons.single(),
                    0,
                    state,
                    onAction,
                    scale,
                    Modifier.width(expiryActionWidth),
                )
            }
        } else if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                buttons.forEachIndexed {
                        index,
                        button,
                    ->
                    ActionControl(button, index, state, onAction, scale, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                buttons.forEachIndexed { index, button ->
                    ActionControl(
                        button,
                        index,
                        state,
                        onAction,
                        scale,
                        Modifier.weight(
                            if (index ==
                                0 &&
                                state !is CopilotUiState.Waiting
                            ) {
                                1.6f
                            } else {
                                1f
                            },
                        ),
                    )
                }
            }
        }
    }
}

private data class ActionButton(
    val label: String,
    val action: CopilotAction,
    val enabled: Boolean = true,
)

@Composable
private fun ActionControl(
    button: ActionButton,
    index: Int,
    state: CopilotUiState,
    onAction: (CopilotAction) -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    CopilotButton(
        button.label,
        { onAction(button.action) },
        scale,
        modifier,
        primary =
            index == 0 && state !is CopilotUiState.Waiting,
        enabled = button.enabled,
        destructive =
            button.action == CopilotAction.DISCONNECT,
    )
}

@Composable
private fun ConnectionSteps(
    current: Int,
    scale: Float,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(R.string.copilot_step_account, R.string.copilot_step_access, R.string.copilot_step_chat).forEachIndexed {
                index,
                label,
            ->
            val status =
                stringResource(
                    if (index <
                        current
                    ) {
                        R.string.copilot_step_done
                    } else if (index ==
                        current
                    ) {
                        R.string.copilot_step_current
                    } else {
                        R.string.copilot_step_pending
                    },
                )
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    stateDescription = status
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp * scale,
                    ),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color =
                        if (index <
                            current
                        ) {
                            Colors.success
                        } else if (index == current) {
                            Colors.accent
                        } else {
                            Colors.raised
                        },
                ) {
                    Text(
                        (index + 1).toString(),
                        Modifier.padding(horizontal = 12.dp * scale, vertical = 2.dp * scale),
                        style = copilotStyle(28f, scale, true),
                        color =
                            if (index <=
                                current
                            ) {
                                Colors.onButton
                            } else {
                                Colors.muted
                            },
                    )
                }
                Text(
                    stringResource(label),
                    style = copilotStyle(28f, scale),
                    color =
                        if (index ==
                            current
                        ) {
                            Colors.text
                        } else {
                            Colors.muted
                        },
                )
            }
        }
    }
}

@Composable
private fun PanelHeading(
    title: Int,
    subtitle: Int?,
    scale: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(stringResource(title), Modifier.semantics { heading() }, style = copilotStyle(52f, scale, true))
        if (subtitle != null) PanelText(subtitle, scale, muted = true)
    }
}

@Composable
private fun AccountCard(
    account: String,
    detail: String?,
    scale: Float,
    trailing: (@Composable () -> Unit)? = null,
) {
    MobiMonListItem(
        Modifier.fillMaxWidth(),
        supporting = detail?.let { text -> { Text(text, style = copilotStyle(28f, scale), color = Colors.muted) } },
        trailing = trailing,
    ) { Text(account, style = copilotStyle(36f, scale, true)) }
}

@Composable
internal fun AccountDisconnectAction(
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
) {
    Box(Modifier.height(44.dp * scale), contentAlignment = Alignment.Center) {
        TextButton(onClick, Modifier.requiredHeight(76.dp), enabled = enabled) {
            Text(stringResource(R.string.copilot_disconnect), style = copilotStyle(26f, scale), color = Colors.accent)
        }
    }
}

@Composable
private fun PanelText(
    text: Int,
    scale: Float,
    muted: Boolean = false,
    size: Float = 34f,
) {
    Text(stringResource(text), style = copilotStyle(size, scale), color = if (muted) Colors.muted else Colors.text)
}

@Composable
private fun Feedback(
    text: String,
    scale: Float,
    color: Color = Colors.warning,
) {
    Text(
        text,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = copilotStyle(28f, scale),
        color = color,
    )
}

internal fun CopilotAccessIssue.message() =
    when (this) {
        CopilotAccessIssue.CHECKING -> R.string.copilot_access_checking
        CopilotAccessIssue.SUBSCRIPTION -> R.string.copilot_access_subscription
        CopilotAccessIssue.PERMISSION -> R.string.copilot_access_permission
        CopilotAccessIssue.USAGE_LIMIT -> R.string.copilot_access_usage
        CopilotAccessIssue.SERVICE_UNAVAILABLE -> R.string.copilot_access_service
    }

internal fun AuthenticationProblem.message(): Int =
    when (this) {
        AuthenticationProblem.CONFIGURATION -> R.string.github_configuration
        AuthenticationProblem.NETWORK -> R.string.github_network
        AuthenticationProblem.DENIED -> R.string.github_denied
        AuthenticationProblem.REAUTHENTICATION -> R.string.github_reauthenticate
        AuthenticationProblem.STORAGE -> R.string.github_storage
        AuthenticationProblem.PROVIDER -> R.string.github_provider
        AuthenticationProblem.RESTRICTED -> R.string.copilot_parked_notice
    }
