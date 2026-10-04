package com.monsters.mobimon.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.PointEconomy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompanionAppearanceState(
    val inventory: CosmeticInventory? = null,
    val failed: Boolean = false,
) {
    val friendId: String get() = inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND) ?: "friend:mobi"
    val accessoryId: String? get() = inventory?.equippedItemIds?.get(CosmeticSlot.ACCESSORY)
    val outfitId: String? get() = inventory?.equippedItemIds?.get(CosmeticSlot.OUTFIT)
    val backgroundId: String? get() = inventory?.equippedItemIds?.get(CosmeticSlot.BACKGROUND)
    val backgroundOverlayId: String? get() = inventory?.backgroundOverlayId
    val backgroundPropId: String? get() = inventory?.backgroundPropId
    val backgroundEffectId: String? get() = inventory?.backgroundEffectId
}

/** Observes committed Room inventory only; store previews never enter this stream. */
class CompanionAppearanceViewModel(
    private val points: PointEconomy,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanionAppearanceState())
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
                    points.inventory.collect { mutableState.value = CompanionAppearanceState(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(failed = true)
                }
            }
    }
}

class CompanionAppearancePresentation(
    private val points: PointEconomy,
) {
    @Composable
    fun model(): CompanionAppearanceViewModel {
        val factory = remember(this) { viewModelFactory { initializer { CompanionAppearanceViewModel(points) } } }
        return viewModel(factory = factory)
    }

    @Composable
    fun state(): CompanionAppearanceState {
        val state by model().state.collectAsStateWithLifecycle()
        return state
    }
}
