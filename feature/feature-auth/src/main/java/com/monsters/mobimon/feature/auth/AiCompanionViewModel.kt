package com.monsters.mobimon.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.PetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AiCompanionState(
    val profile: PetProfile? = null,
    val failed: Boolean = false,
)

/** Reads the committed profile; appearance comes from the shared presentation state. */
internal class AiCompanionViewModel(
    private val pets: PetRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AiCompanionState())
    val state = mutableState.asStateFlow()
    private var observer: Job? = null

    init {
        retry()
    }

    fun retry() {
        if (observer?.isActive == true) return
        mutableState.value = mutableState.value.copy(failed = false)
        observer =
            viewModelScope.launch {
                try {
                    pets.initialize()
                    pets.profile.collect { mutableState.value = AiCompanionState(profile = it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(failed = true)
                }
            }
    }
}
