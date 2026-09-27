package com.monsters.mobimon.feature.quest

import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState

data class QuestAlert(
    val id: String,
    val title: String,
)

/** Claimable entries disappear once the reward transaction is observed as complete. */
fun claimableQuestAlerts(
    state: QuestUiState,
    appearance: CompanionAppearanceState,
    catalog: PointQuestCatalog,
    text: (Int) -> String,
): List<QuestAlert> {
    if (state.isLoading || state.observationFailed) return emptyList()
    val screen = QuestCatalog(catalog).present(state, appearance, PointBalanceState.Loading, false, text)
    return screen.quests.filter { it.status == QuestItemStatus.CLAIMABLE }.map { QuestAlert(it.id, "${it.title} 완료") } +
        screen.hiddenQuests.map { QuestAlert(it.id, "${it.title} 완료") }
}
