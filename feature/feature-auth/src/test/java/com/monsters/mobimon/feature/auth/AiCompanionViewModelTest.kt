package com.monsters.mobimon.feature.auth

import androidx.lifecycle.ViewModelStore
import com.monsters.mobimon.core.domain.PetAppearance
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.domain.WriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AiCompanionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val pets = FakePets()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun readsCommittedProfileAndDoesNotDuplicateActiveObserversOnRetry() =
        runTest(dispatcher) {
            val model = model()
            runCurrent()
            assertFalse(model.state.value.failed)
            assertEquals(pets.savedProfile.value, model.state.value.profile)

            model.retry()
            pets.savedProfile.value = PetProfile("saved", appearance = PetAppearance.CREAM)
            runCurrent()

            assertEquals(
                PetAppearance.CREAM,
                model.state.value.profile
                    ?.appearance,
            )
            assertEquals(1, pets.initializations)
        }

    @Test fun observationFailureRetainsCommittedProfileAndRetryReconnects() =
        runTest(dispatcher) {
            val fail = Channel<Unit>(Channel.CONFLATED)
            var subscriptions = 0
            pets.profile =
                flow {
                    subscriptions++
                    emit(pets.savedProfile.value)
                    if (subscriptions == 1) {
                        fail.receive()
                        throw IOException("temporary profile read failure")
                    }
                    awaitCancellation()
                }
            val model = model()
            runCurrent()
            fail.send(Unit)
            runCurrent()
            assertTrue(model.state.value.failed)
            assertEquals(pets.savedProfile.value, model.state.value.profile)

            model.retry()
            runCurrent()
            assertFalse(model.state.value.failed)
            assertEquals(2, subscriptions)
        }

    @Test fun cancelledInitializationIsNotReportedAsStorageFailure() =
        runTest(dispatcher) {
            pets.initializationFailure = CancellationException("owner ended")
            val model = model()
            runCurrent()
            assertFalse(model.state.value.failed)
            assertNull(model.state.value.profile)

            pets.initializationFailure = null
            model.retry()
            runCurrent()
            assertEquals(pets.savedProfile.value, model.state.value.profile)
        }

    private fun model() = AiCompanionViewModel(pets).also { store.put("ai", it) }

    private class FakePets : PetRepository {
        val savedProfile = MutableStateFlow(PetProfile("saved"))
        override var profile: Flow<PetProfile> = savedProfile
        var initializations = 0
        var initializationFailure: Exception? = null

        override suspend fun initialize() {
            initializations++
            initializationFailure?.let { throw it }
        }

        override suspend fun setAppearance(appearance: PetAppearance) = WriteResult.Failure
    }
}
