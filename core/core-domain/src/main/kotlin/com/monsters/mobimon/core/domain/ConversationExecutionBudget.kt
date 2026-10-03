package com.monsters.mobimon.core.domain

/** Whole-turn limits; tool and model counts are deliberately not capped. */
data class ConversationExecutionBudget(
    val maxElapsedMillis: Long = 30_000,
    val maxEstimatedTokens: Long = 64_000,
    val maxResultCharacters: Int = 64_000,
    val maxCombinedResultCharacters: Int = 64_000,
) {
    init {
        require(maxElapsedMillis in 1..30_000 && maxEstimatedTokens in 1..64_000)
        require(maxResultCharacters in 1..64_000 && maxCombinedResultCharacters in 1..64_000)
    }
}
