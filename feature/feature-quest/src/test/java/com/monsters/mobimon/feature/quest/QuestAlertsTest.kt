package com.monsters.mobimon.feature.quest

import com.monsters.mobimon.core.domain.DefaultPointQuestCatalog
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestAlertsTest {
    @Test
    fun `completed quest alert disappears after reward is received`() {
        val state = QuestUiState(isLoading = false, satisfiedDrivingQuestIds = setOf(DrivingQuestIds.SEATBELT))
        val before = claimableQuestAlerts(state, CompanionAppearanceState(), DefaultPointQuestCatalog(), Int::toString)
        val after =
            claimableQuestAlerts(
                state.copy(completedPointQuestIds = setOf(DrivingQuestIds.SEATBELT)),
                CompanionAppearanceState(),
                DefaultPointQuestCatalog(),
                Int::toString,
            )
        assertEquals(listOf(DrivingQuestIds.SEATBELT), before.map { it.id })
        assertEquals(emptyList<QuestAlert>(), after)
    }
}
