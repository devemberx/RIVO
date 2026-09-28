package com.monsters.mobimon.di

import com.monsters.mobimon.core.domain.ConversationTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ConversationToolsModule {
    @Provides
    fun conversationTools(): ConversationTools = ConversationTools.None
}
