package com.monsters.mobimon.di

import com.monsters.mobimon.chat.GroundedConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ConversationToolsModule {
    @Provides
    fun conversationTools(evidence: VehicleChatEvidenceSource): ConversationTools =
        ConversationTools(emptyList(), groundedReplyPolicy = GroundedConversationReplyPolicy(evidence))
}
