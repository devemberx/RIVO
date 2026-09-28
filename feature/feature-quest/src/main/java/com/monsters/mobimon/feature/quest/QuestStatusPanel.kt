package com.monsters.mobimon.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonMessage

@Composable
internal fun QuestStatusPanel(
    state: QuestScreenState,
    onRetryQuests: () -> Unit,
    onRetryWallet: () -> Unit,
    onRetryAppearance: () -> Unit,
) {
    val hasStatus =
        state.isLoading ||
            state.observationFailed ||
            state.errorMessage != null ||
            state.appearance.failed ||
            state.pointBalance == PointBalanceState.Failed
    if (!hasStatus) return
    Column(Modifier.fillMaxWidth(0.70f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.observationFailed -> {
                MobiMonMessage(stringResource(R.string.quest_observation_failed), isError = true)
                MobiMonButton(onClick = onRetryQuests) { Text(stringResource(R.string.quest_retry_records)) }
            }
            state.isLoading -> MobiMonMessage(stringResource(R.string.quest_loading))
        }
        state.errorMessage?.let { MobiMonMessage(it, isError = true) }
        if (state.pointBalance == PointBalanceState.Failed) {
            MobiMonButton(onClick = onRetryWallet) { Text(stringResource(R.string.quest_retry_wallet)) }
        }
        if (state.appearance.failed) {
            MobiMonMessage(stringResource(R.string.quest_appearance_failed), isError = true)
            MobiMonButton(onClick = onRetryAppearance) { Text(stringResource(R.string.quest_retry_appearance)) }
        }
    }
}
