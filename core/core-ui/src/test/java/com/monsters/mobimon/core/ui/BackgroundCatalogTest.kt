package com.monsters.mobimon.core.ui

import androidx.compose.ui.Alignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackgroundCatalogTest {
    private val defaultScene = CompanionBackgroundCatalog.defaultScene

    @Test
    fun unknownAndRemovalIdsNeverInventDecorations() {
        listOf(
            null,
            "none:background",
            "background:default",
            "background:unknown",
            "background:future_snow",
        ).forEach { id ->
            val resolved = resolveCompanionBackground("Day", id)
            assertEquals("lake_park", resolved.scene.id)
            assertNull(resolved.prop)
            assertNull(resolved.effect)
        }
    }

    @Test
    fun legacySingleSlotSelectsOnlyItsRegisteredDecoration() {
        val prop = resolveCompanionBackground("Morning", "background:star_hanger")
        assertEquals("lake_park", prop.scene.id)
        assertEquals(BackgroundProp.STAR_HANGER, prop.prop)
        assertNull(prop.effect)
        mapOf(
            "star" to ParticleType.STAR,
            "snow" to ParticleType.SNOW,
            "petal" to ParticleType.PETAL,
        ).forEach { (id, kind) ->
            val effect = resolveCompanionBackground("Morning", "background:$id")
            assertEquals(kind, effect.effect)
            assertNull(effect.prop)
        }
    }

    @Test
    fun separateDecorationInputsKeepSelectedSceneAndCanRemoveLegacyDecoration() {
        val resolved =
            resolveCompanionBackground("Day", "background:cyberpunk_city", "background:star_hanger", "background:snow")
        assertEquals("cyberpunk_city", resolved.scene.id)
        assertEquals(BackgroundProp.STAR_HANGER, resolved.prop)
        assertEquals(ParticleType.SNOW, resolved.effect)
        assertNull(resolveCompanionBackground("Day", "background:star", effectId = "none:effect").effect)
        assertNull(resolveCompanionBackground("Day", "background:star_hanger", propId = "unknown").prop)
        assertNull(
            resolveCompanionBackground("Day", "background:cyberpunk_city", effectId = "background:star_hanger").effect,
        )
    }

    @Test
    fun anotherSceneRequiresOnlyRegistrationAndFallsBackToItsOwnNight() {
        val third =
            CompanionBackgroundCatalog
                .scene(
                    "background:cyberpunk_city",
                )!!
                .copy(id = "third_scene", homeAlignment = Alignment.Center)
        val catalog =
            BackgroundCatalog(
                listOf(defaultScene, third),
                listOf(BackgroundItem("background:third", BackgroundVisual.Scene(third.id))),
                defaultScene.id,
            )
        BackgroundPeriod.entries.forEach { period ->
            val resolved = resolveCompanionBackground(period.key, "background:third", catalog = catalog)
            assertEquals(third, resolved.scene)
            assertEquals(third.artwork.frame(period), resolved.layer.frame)
            assertEquals(Alignment.Center, resolved.layer.alignment)
        }
        assertEquals(
            third.artwork.frame(BackgroundPeriod.NIGHT),
            resolveCompanionBackground(null, "background:third", catalog = catalog).layer.frame,
        )
    }

    @Test
    fun staticArtworkDoesNotChangeAnimationLayerWhenTimeChanges() {
        val static = defaultScene.copy(id = "static", artwork = BackgroundArtwork.Static(BackgroundFrame(1)))
        val catalog =
            BackgroundCatalog(
                listOf(static),
                listOf(BackgroundItem("background:static", BackgroundVisual.Scene("static"))),
                "static",
            )
        val layers =
            BackgroundPeriod.entries.map {
                resolveCompanionBackground(it.key, "background:static", catalog = catalog).layer
            }
        assertEquals(1, layers.distinct().size)
    }

    @Test
    fun catalogRejectsAmbiguousIdsAndMissingSceneReferences() {
        val item = BackgroundItem("background:test", BackgroundVisual.Scene(defaultScene.id))
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundCatalog(listOf(defaultScene, defaultScene), listOf(item), defaultScene.id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundCatalog(listOf(defaultScene), listOf(item, item), defaultScene.id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundCatalog(
                listOf(defaultScene),
                listOf(item.copy(visual = BackgroundVisual.Scene("missing"))),
                defaultScene.id,
            )
        }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { BackgroundCatalog(listOf(defaultScene), listOf(item), "missing") }
    }
}
