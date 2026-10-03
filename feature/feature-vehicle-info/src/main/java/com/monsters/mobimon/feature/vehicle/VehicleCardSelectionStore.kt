package com.monsters.mobimon.feature.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface VehicleCardSelectionStore {
    val selectedCards: StateFlow<List<String>>

    fun save(cards: List<String>)

    companion object {
        private val previousDefaults = listOf("battery", "charging", "tire", "washer", "environment", "assist")
        private val retiredReplacements = mapOf("environment" to "low-beam", "assist" to "fatigue")

        fun defaults(): List<String> = VehicleCardCatalog.defaultSlots.map { it.id }

        fun validOrDefaults(cards: List<String>): List<String> {
            if (cards == previousDefaults) return defaults()
            if (cards.size != 6 || cards.any { it !in retiredReplacements && VehicleCardCatalog.find(it) == null }) {
                return defaults()
            }
            val used = cards.filterNot { it in retiredReplacements }.toMutableSet()
            return cards.map { id ->
                val preferred = retiredReplacements[id] ?: return@map id
                val replacement = (listOf(preferred) + defaults()).first { it !in used }
                used += replacement
                replacement
            }
        }
    }
}

internal class InMemoryVehicleCardSelectionStore : VehicleCardSelectionStore {
    private val state = MutableStateFlow(VehicleCardSelectionStore.defaults())
    override val selectedCards: StateFlow<List<String>> = state

    override fun save(cards: List<String>) {
        state.value = VehicleCardSelectionStore.validOrDefaults(cards)
    }
}
