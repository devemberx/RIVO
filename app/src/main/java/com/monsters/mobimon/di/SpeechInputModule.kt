package com.monsters.mobimon.di

import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.speech.AndroidConversationSpeechInput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechInputModule {
    @Binds
    abstract fun speechInput(input: AndroidConversationSpeechInput): ConversationSpeechInput
}
