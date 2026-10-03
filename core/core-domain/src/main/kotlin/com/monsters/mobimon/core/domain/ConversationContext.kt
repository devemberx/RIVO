package com.monsters.mobimon.core.domain

/** Optional data read at send time; never a storage identity or an instruction. */
class ConversationContext(
    val userName: String? = null,
    val vssTimestamp: String? = null,
    val timeOfDay: String? = null,
    val simulatedTime: Boolean = false,
    val batteryPercent: Int? = null,
    val simulatedBattery: Boolean = false,
    val petCondition: String? = null,
    val conditionReasons: List<ConversationConditionReason> = emptyList(),
    val simulatedCondition: Boolean = false,
    val vehicleCapture: VehicleChatCapture? = null,
) {
    override fun toString() = "ConversationContext(REDACTED)"
}

class ConversationConditionReason(
    val concern: String,
    val signal: String,
    val value: String,
    val description: String,
) {
    override fun toString() = "ConversationConditionReason(REDACTED)"
}

fun interface ConversationContextSource {
    suspend fun current(): ConversationContext
}
