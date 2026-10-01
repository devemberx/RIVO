package com.monsters.mobimon.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.monsters.mobimon.core.database.AppDatabase
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.GitHubAccount
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GitHubSignIn
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.di.AppEnvironment
import com.monsters.mobimon.di.AppUseModule
import com.monsters.mobimon.di.AuthenticationModule
import com.monsters.mobimon.di.NetworkModule
import com.monsters.mobimon.di.PlatformModule
import com.monsters.mobimon.di.VehicleProviderModule
import com.monsters.mobimon.feature.auth.ConversationNetworkStatus
import com.monsters.mobimon.runtime.AppUseStateSource
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [
        PlatformModule::class,
        VehicleProviderModule::class,
        AppUseModule::class,
        AuthenticationModule::class,
        NetworkModule::class,
    ],
)
object JourneyTestModule {
    @Provides
    @Singleton
    fun authentication(authentication: JourneyAuthentication): GitHubAuthentication = authentication

    @Provides
    fun conversation(provider: JourneyConversationProvider): ConversationProvider = provider

    @Provides
    fun conversationStore(storage: JourneyStorage): com.monsters.mobimon.core.domain.ConversationStore =
        storage.conversations

    @Provides
    fun networkStatus(status: JourneyNetworkStatus): ConversationNetworkStatus = status

    @Provides
    fun clock(): Clock = Clock { 10_000L }

    @Provides
    fun utcClock(): UtcClock = UtcClock { 1_800_000_000_000L }

    @Provides
    fun environment(): AppEnvironment =
        AppEnvironment("unused-journey.db", ProgressionIdentity("journey-profile", SignalSource.SIMULATED))

    @Provides
    fun vehicle(vehicle: JourneyVehicle): VehicleRepository = vehicle

    @Provides
    fun appUseStateSource(appUse: JourneyAppUse): AppUseStateSource = appUse

    @Provides
    fun database(storage: JourneyStorage): AppDatabase = storage.database

    @Provides
    fun preferences(storage: JourneyStorage): DataStore<Preferences> = storage.preferences
}

@Singleton
class JourneyNetworkStatus
    @Inject
    constructor() : ConversationNetworkStatus {
        override val online = MutableStateFlow(true)

        override fun isOnline() = online.value
    }

@Singleton
class JourneyConversationProvider
    @Inject
    constructor() : ConversationProvider {
        var connectionResult: ConversationResult<String> = ConversationResult.Success("gpt-4o")
        var replyResult: ConversationResult<String> = ConversationResult.Success("이야기를 들려줘서 고마워요.")
        var connections = 0
            private set
        var replies = 0
            private set
        val requests = mutableListOf<List<ConversationTurn>>()
        val conversationIds = mutableListOf<String>()

        override suspend fun connect(accountId: Long): ConversationResult<String> {
            connections++
            return connectionResult
        }

        override suspend fun reply(
            accountId: Long,
            conversationId: String,
            friendId: String,
            messages: List<ConversationTurn>,
        ): ConversationResult<String> {
            replies++
            requests += messages
            conversationIds += conversationId
            return replyResult
        }
    }

@Singleton
class JourneyAuthentication
    @Inject
    constructor() : GitHubAuthentication {
        override val session = MutableStateFlow<GitHubSession>(GitHubSession.SignedOut)
        override val configured = false
        var restoreSession: GitHubSession? = null

        fun approve() {
            session.value = GitHubSession.Authenticated(GitHubAccount(1, "journey-sample"))
        }

        fun failNetwork() {
            session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
        }

        override suspend fun restore() {
            restoreSession?.let { session.value = it }
        }

        override suspend fun disconnect() {
            session.value = GitHubSession.SignedOut
        }

        override fun signIn() = emptyFlow<GitHubSignIn>()
    }

@Singleton
class JourneyAppUse
    @Inject
    constructor() : AppUseStateSource {
        private val mutableState = MutableStateFlow(AppUseState.ALLOWED)
        override val states: StateFlow<AppUseState> = mutableState.asStateFlow()

        override fun state(): AppUseState = states.value

        override fun start() = Unit

        override fun stop() = Unit
    }

@Singleton
class JourneyVehicle
    @Inject
    constructor() : VehicleRepository {
        private val state =
            MutableStateFlow(
                VehicleSnapshot(
                    id = "journey-1",
                    epoch = "journey-epoch",
                    sequence = 1,
                    receivedAtMillis = 10_000L,
                    source = SignalSource.SIMULATED,
                    drivingState = DrivingState.PARKED,
                    quality = SignalQuality.VALID,
                    batteryPercent = 72,
                ),
            )
        override val snapshots = state.asStateFlow()

        fun publish(
            drivingState: DrivingState = DrivingState.PARKED,
            quality: SignalQuality = SignalQuality.VALID,
        ) {
            val next = state.value.sequence + 1
            state.value =
                state.value.copy(id = "journey-$next", sequence = next, drivingState = drivingState, quality = quality)
        }

        override fun start() = Unit

        override fun stop() = Unit
    }

@Singleton
class JourneyStorage
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AutoCloseable {
        private val directory = Files.createTempDirectory(context.cacheDir.toPath(), "journey-").toFile()
        val conversations =
            com.monsters.mobimon.core.database
                .AtomicConversationStore(File(directory, "chat"))
        private val job = SupervisorJob()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val preferences =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                File(directory, "settings.preferences_pb")
            }

        override fun close() {
            runBlocking { job.cancelAndJoin() }
            database.close()
            check(directory.deleteRecursively()) { "Could not remove journey test preferences" }
        }
    }
