package com.monsters.mobimon.di

import android.content.Context
import android.util.Log
import com.monsters.mobimon.chat.GroundedConversationReplyPolicy
import com.monsters.mobimon.chat.VehicleConversationTool
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.manual.AssetManualRetriever
import com.monsters.mobimon.manual.ManualConversationTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConversationToolsModule {
    @Provides
    @Singleton
    fun manualRetriever(
        @ApplicationContext context: Context,
    ): ManualRetriever = AssetManualRetriever(context.assets)

    @Provides
    @Singleton
    fun conversationTools(
        retriever: ManualRetriever,
        evidence: VehicleChatEvidenceSource,
    ): ConversationTools {
        val manual = ManualConversationTools.create(retriever) { Log.i("MobiMonManual", it.toString()) }
        return ConversationTools(
            manual.tools + VehicleConversationTool(evidence),
            manual.instruction.substringBefore("# Response contract"),
            onUsage = manual.onUsage,
            groundedReplyPolicy = GroundedConversationReplyPolicy(evidence, manual.replyPolicy),
        )
    }
}
