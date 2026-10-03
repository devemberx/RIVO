package com.monsters.mobimon.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleChatFieldCatalogTest {
    @Test fun catalogCoversCardPathsAndAllTopicsWithoutDefaultRealValues() {
        val fields = VehicleChatFieldCatalog.fields
        assertEquals(fields.size, fields.map { it.id }.distinct().size)
        assertTrue(
            VehicleCardVssDefaults.definitions.all { definition ->
                fields.any { definition.path in it.sourcePaths }
            },
        )
        val detailedTopics =
            VehicleChatTopic.entries.filter {
                it !in setOf(VehicleChatTopic.BASIC, VehicleChatTopic.OVERVIEW)
            }
        assertTrue(detailedTopics.all { topic -> fields.any { it.topic == topic } })
        assertTrue(fields.all { field -> field.dependencyIds.all { dependency -> fields.any { it.id == dependency } } })
    }

    @Test fun valuesValidateTypesWithoutCoercingMissingToZeroOrFalse() {
        assertNull(VehicleValue.parse(CardVssType.BOOLEAN, "unknown"))
        assertEquals(VehicleValue.Boolean(false), VehicleValue.parse(CardVssType.BOOLEAN, "false"))
        assertNull(VehicleValue.parse(CardVssType.NUMBER, "NaN"))
        assertEquals(VehicleValue.Number(0.0), VehicleValue.parse(CardVssType.NUMBER, "0"))
    }
}
