package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h300dp-mdpi")
class PetAvatarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mobiShowsItsCanonicalSpriteAfterColdBackgroundLoad() {
        MobiSpriteCache.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MobiMonTheme {
                PetAvatar(friendId = "friend:mobi", accessoryId = "accessory:mobi_headphones")
            }
        }

        awaitMobiFirstFrame("headphones")
        compose.onNodeWithTag("mobi-animation-frame-headphones", useUnmergedTree = true).assertExists()
    }

    @Test
    fun mobiStillCardUsesTheFirstSpriteWithoutLoadingTheAtlas() {
        MobiSpriteCache.clear()
        compose.setContent {
            MobiMonTheme {
                PetAvatar(friendId = "friend:mobi", accessoryId = "accessory:mobi_goggles", isAnimated = false)
            }
        }

        awaitMobiFirstFrame("goggles")
        compose.onNodeWithTag("mobi-animation-frame-goggles", useUnmergedTree = true).assertExists()
        assertNull(MobiSpriteCache.peek("accessory:mobi_goggles"))
    }

    private fun awaitMobiFirstFrame(appearance: String) {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose
                .onAllNodesWithTag("mobi-animation-frame-$appearance", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun mobiLayersRetainCanonicalFirstFrames() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles").forEach { accessory ->
            val loaded = requireNotNull(MobiSpriteCache.getOrLoad(context, accessory))
            val first = requireNotNull(MobiSpriteCache.firstFrame(context, accessory))
            assertTrue("Loading gesture layers must not replace the first frame for $accessory", loaded === first)
            assertEquals(627, loaded.width)
            assertEquals(627, loaded.height)
        }
    }

    @Test
    fun lunaShowsItsSpriteOnTheFirstRenderedFrame() {
        LunaIdleArtworkCache.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MobiMonTheme {
                PetAvatar(friendId = "friend:luna", accessoryId = "accessory:luna_sunglasses")
            }
        }

        compose.onNodeWithTag("luna-animation-frame-sunglasses", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("luna-animation-loading-sunglasses", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun equippedLooksUseIsolatedAssetsAndLunaUsesAnimatedLooks() {
        CharacterArtwork.equippedLooks.values.forEach { assertNull(it.crop) }
        val lunaScale = CharacterArtwork.characters.getValue("friend:luna").visualScale
        assertTrue(lunaScale < 1f)
        assertTrue(CharacterArtwork.equippedLooks.keys.none { it.startsWith("accessory:luna_") })
    }

    @Test
    fun supportedMobiVariantsHaveDistinctPixelSignatures() {
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                Row {
                    PetAvatar(modifier = Modifier.testTag("default"))
                    PetAvatar(
                        modifier = Modifier.testTag("cream"),
                        appearanceKey = "CREAM",
                    )
                    PetAvatar(
                        modifier = Modifier.testTag("headphones"),
                        accessoryId = "accessory:mobi_headphones",
                    )
                    PetAvatar(
                        modifier = Modifier.testTag("goggles"),
                        accessoryId = "accessory:mobi_goggles",
                    )
                }
            }
        }

        val tags = listOf("default", "cream", "headphones", "goggles")
        val bounds = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        lateinit var signatures: Map<String, List<Int>>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            signatures =
                bounds.mapValues { (_, area) ->
                    val left = area.left.toInt()
                    val top = area.top.toInt()
                    val width = area.width.toInt()
                    val height = area.height.toInt()
                    IntArray(width * height)
                        .also { pixels ->
                            bitmap.getPixels(pixels, 0, width, left, top, width, height)
                        }.toList()
                }
            bitmap.recycle()
        }

        signatures.values.toList().forEachIndexed { index, signature ->
            signatures.values.drop(index + 1).forEach { other -> assertNotEquals(signature, other) }
        }
        assertTrue(signatures.getValue("default").toSet().size > 1_000)
        assertTrue(0xFFF2E4C8.toInt() in signatures.getValue("cream"))
    }

    @Test
    fun mobiAnimationCacheReusesSmallLayersForEveryAppearance() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val appearances = listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")
        val loaded = appearances.map { requireNotNull(MobiIdleArtworkCache.getOrLoad(context, it)) }
        appearances.zip(loaded).forEach { (accessory, artwork) ->
            assertTrue(artwork === MobiIdleArtworkCache.getOrLoad(context, accessory))
            assertEquals(627, artwork.body.width)
            assertEquals(627, artwork.closedEyesBody.height)
            val name = mobiAppearanceName(accessory)
            val files = mutableSetOf("mobi_idle_breath_${name}_01.png")
            if (name != "normal") files.add("mobi_idle_breath_${name}_underlay.webp")
            assertEquals(files, context.assets.list("characters/mobi/$name/idle_breath")!!.toSet())
        }
        assertTrue(loaded.all { it.sprout === loaded.first().sprout })
        assertTrue(loaded[1].sproutInFront)
        assertTrue(!loaded[2].sproutInFront)
    }

    @Test
    fun mobiRunSpriteCacheLoadsSheetFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context))
        assertEquals(1536, sprite.width)
        assertEquals(1024, sprite.height)
        assertTrue(sprite === MobiRunSpriteCache.getOrLoad(context))
        assertTrue(context.assets.list("characters/mobi/normal/run")!!.contains("mobi_run_left_normal_sprite.webp"))

        val headphonesSprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))
        assertEquals(1536, headphonesSprite.width)
        assertEquals(1024, headphonesSprite.height)
        assertTrue(headphonesSprite === MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))
        assertTrue(
            context.assets.list("characters/mobi/headphones/run")!!.contains("mobi_run_left_headphones_sprite.webp"),
        )

        val gogglesSprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
        assertEquals(1536, gogglesSprite.width)
        assertEquals(1024, gogglesSprite.height)
        assertTrue(gogglesSprite === MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
        assertTrue(context.assets.list("characters/mobi/goggles/run")!!.contains("mobi_run_left_goggles_sprite.webp"))
    }

    @Test
    fun preloadPetRunSpriteWarmsUpCachesForMobiAndLuna() =
        kotlinx.coroutines.test.runTest {
            val context =
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext<android.content.Context>()
            preloadPetRunSprite(context, "friend:mobi", "accessory:mobi_headphones")
            assertNotNull(MobiRunSpriteCache.peek("accessory:mobi_headphones"))

            preloadPetRunSprite(context, "friend:luna", "accessory:luna_cap")
            assertNotNull(LunaRunAnimationCache.peek())
        }

    @Test
    fun lunaIdleLoadsTwoPosesAndSharesTheFirstFrameFallback() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val artwork = LunaIdleArtworkCache.getOrLoad(context)!!
        assertNotNull(artwork.closedEyesBody)
        assertTrue(
            artwork.original ===
                LunaFirstFrameCache.getOrLoad(
                    context,
                    LunaActiveAnimation.IDLE,
                    LunaAppearance.NORMAL,
                ),
        )
    }

    @Test
    fun lunaDisappearLoadsTwentyFourFramesAndHoldsTheLastOne() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertEquals(24, LunaDisappearTimeline.load(context)?.size)
        assertEquals(0, LunaDisappearTimeline.frameAt(0L))
        assertEquals(0, LunaDisappearTimeline.frameAt(LunaDisappearTimeline.HOLD_NANOS))
        assertEquals(12, LunaDisappearTimeline.frameAt(LunaDisappearTimeline.HOLD_NANOS + 750_000_000L))
        assertEquals(23, LunaDisappearTimeline.frameAt(LunaDisappearTimeline.DURATION_NANOS * 2))
    }

    @Test
    fun lunaDisappearingPlaysTheExitBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(friendId = "friend:luna", isDisappearing = true, onDisappeared = { finished = true })
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun lunaAppearLoadsTwentyFourFramesAndHandsOffToIdle() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertEquals(24, LunaAppearTimeline.load(context)?.size)
        assertEquals(0, LunaAppearTimeline.frameAt(0L))
        assertEquals(23, LunaAppearTimeline.frameAt(LunaAppearTimeline.DURATION_NANOS))
        assertEquals(0f, LunaAppearTimeline.idleBlendAt(0L), 0f)
        assertEquals(1f, LunaAppearTimeline.idleBlendAt(LunaAppearTimeline.DURATION_NANOS), 0f)
    }

    @Test
    fun lunaAppearingPlaysTheEntranceBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(friendId = "friend:luna", isAppearing = true, onAppeared = { finished = true })
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun equippedLunaAppearLoadsItsOwnTwentyFourFrames() {
        assertEquippedFramesLoaded(LunaAppearTimeline::load)
    }

    @Test
    fun equippedLunaDisappearLoadsItsOwnTwentyFourFrames() {
        assertEquippedFramesLoaded(LunaDisappearTimeline::load)
    }

    private fun assertEquippedFramesLoaded(
        load: (android.content.Context, LunaAppearance) -> List<androidx.compose.ui.graphics.ImageBitmap>?,
    ) {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val normal = requireNotNull(load(context, LunaAppearance.NORMAL))
        listOf(LunaAppearance.HAT, LunaAppearance.SUNGLASSES).forEach { appearance ->
            val frames = requireNotNull(load(context, appearance))
            assertEquals(24, frames.size)
            // A normal-frame fallback would also have 24 frames; Luna is fully visible mid-sequence.
            assertTrue(
                appearance.name,
                !frames[11].asAndroidBitmap().sameAs(normal[11].asAndroidBitmap()),
            )
        }
    }

    @Test
    fun lunaSunglassesDisappearingPlaysTheExitBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(
                friendId = "friend:luna",
                accessoryId = "accessory:luna_sunglasses",
                isDisappearing = true,
                onDisappeared = { finished = true },
            )
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun lunaSunglassesAppearingPlaysTheEntranceBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(
                friendId = "friend:luna",
                accessoryId = "accessory:luna_sunglasses",
                isAppearing = true,
                onAppeared = { finished = true },
            )
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun lunaHatDisappearingPlaysTheExitBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(
                friendId = "friend:luna",
                accessoryId = "accessory:luna_cap",
                isDisappearing = true,
                onDisappeared = { finished = true },
            )
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun lunaHatAppearingPlaysTheEntranceBeforeReportingCompletion() {
        var finished = false
        compose.setContent {
            PetAvatar(
                friendId = "friend:luna",
                accessoryId = "accessory:luna_cap",
                isAppearing = true,
                onAppeared = { finished = true },
            )
        }
        compose.mainClock.advanceTimeBy(100L)
        assertTrue(!finished)
        compose.waitUntil(10_000L) { finished }
    }

    @Test
    fun lunaRunAnimationCacheLoadsTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val frames = LunaRunAnimationCache.getOrLoadFrames(context)
        assertEquals(24, frames.size)
    }

    @Test
    fun lunaHungryUsesSharedPartsWithIdleGeometry() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val parts = LunaHungryPartsCache.getOrLoad(context)
        assertNotNull(parts)
        assertTrue(parts === LunaHungryPartsCache.getOrLoad(context))
    }

    @Test
    fun lunaSickAnimationCacheLoadsTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val frames = LunaSickAnimationCache.getOrLoadFrames(context)
        assertEquals(24, frames.size)
    }

    @Test
    fun lunaHatAnimationCachesLoadTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertNotNull(LunaIdleArtworkCache.getOrLoad(context, appearance = LunaAppearance.HAT)?.closedEyesBody)
        assertEquals(24, LunaRunAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
        assertNotNull(LunaHungryPartsCache.getOrLoad(context))
        assertEquals(24, LunaSickAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
    }

    @Test
    fun lunaIdleCapOccludesRightEarThroughoutBreathing() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val artwork = LunaIdleArtworkCache.getOrLoad(context, appearance = LunaAppearance.HAT)!!
        listOf(artwork.original.asAndroidBitmap(), artwork.closedEyesBody!!).forEach { bitmap ->
            assertEquals(627, bitmap.width)
            assertEquals(723, bitmap.height)
            // The former ear must not protrude behind the new crown.
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(540, 140)))
            // The opposite ear must remain visible.
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(150, 194)) > 240)
        }
    }

    @Test
    fun lunaIdleEquipmentSwitchUsesMatchingArtworkAndNoHiddenEyeTexture() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val normal = LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.NORMAL)!!
        val hat = LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.HAT)!!
        val glasses = LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.SUNGLASSES)!!
        assertNull(glasses.closedEyesBody)
        assertTrue(glasses.original !== normal.original && glasses.original !== hat.original)
        assertTrue(glasses === LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.SUNGLASSES))
        assertTrue(
            glasses.original ===
                LunaFirstFrameCache.getOrLoad(
                    context,
                    LunaActiveAnimation.IDLE,
                    LunaAppearance.SUNGLASSES,
                ),
        )
        assertTrue(normal.original === LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.NORMAL)!!.original)
    }

    @Test
    fun lunaSunglassesFramesReplaceCachedNormalAndHatArtwork() =
        kotlinx.coroutines.test.runTest {
            val context =
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext<android.content.Context>()
            val loaders =
                listOf(
                    LunaRunAnimationCache::getOrLoadFrames,
                    LunaSickAnimationCache::getOrLoadFrames,
                )
            val actions = listOf("run", "sick")
            loaders.forEachIndexed { index, load ->
                val normal = load(context, LunaAppearance.NORMAL)
                val hat = load(context, LunaAppearance.HAT)
                val sunglasses = load(context, LunaAppearance.SUNGLASSES)
                assertEquals(24, sunglasses.size)
                val action = actions[index]
                val prefix = if (action == "run") "run_left" else action
                val path = "characters/luna/sunglasses/$action/luna_${prefix}_sunglasses_01.png"
                val expected =
                    context.assets.open(path).use {
                        android.graphics.BitmapFactory.decodeStream(
                            it,
                            null,
                            android.graphics.BitmapFactory
                                .Options()
                                .apply { inSampleSize = 2 },
                        )!!
                    }
                assertTrue(
                    "$action uses approved sunglasses artwork",
                    sunglasses.first().asAndroidBitmap().sameAs(expected),
                )
                expected.recycle()
                assertTrue(sunglasses !== normal && sunglasses !== hat)
                assertTrue(sunglasses === load(context, LunaAppearance.SUNGLASSES))
                assertTrue(sunglasses !== load(context, LunaAppearance.NORMAL))
            }
            preloadPetRunSprite(context, "friend:luna", "accessory:luna_sunglasses")
            assertTrue(
                LunaRunAnimationCache.peek() ===
                    LunaRunAnimationCache.getOrLoadFrames(context, LunaAppearance.SUNGLASSES),
            )
        }

    @Test
    fun standaloneItemIconsPreserveOriginalCropPixelsAndCanvas() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        // RGBA hashes captured from the original Store crop regions before removing the sheets.
        val expected =
            mapOf(
                "accessory:mobi_headphones" to
                    Triple(475, 470, "7cf4dc750cba2fa9f2e83d5746224c2fcb6ab29f0ea031b99e4b56f62d98bc4a"),
                "accessory:mobi_goggles" to
                    Triple(468, 320, "84a935d8ad6e1d91690faaa8a86de12210ad329a5f2cbb749795f313998ccf2f"),
                "accessory:luna_cap" to
                    Triple(500, 480, "f7f405d754ff1cb9afa13b81234cb98c8b4a4538b5dc047a8bbb0f5b0a79e972"),
                "accessory:luna_sunglasses" to
                    Triple(460, 330, "f1afdded2044a0590ec3347f4fca2a62e4eb5517c9131db962c2b7ef5cc3a092"),
            )
        for ((id, baseline) in expected) {
            val asset = CharacterArtwork.itemIcons.getValue(id)
            assertNull(asset.crop)
            val bitmap =
                android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    asset.resourceId,
                    android.graphics.BitmapFactory.Options().apply {
                        inScaled = false
                        inPremultiplied = false
                    },
                )
            assertEquals(baseline.first, bitmap.width)
            assertEquals(baseline.second, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            for (pixel in pixels) {
                for (shift in listOf(16, 8, 0, 24)) digest.update((pixel ushr shift).toByte())
            }
            assertEquals(id, baseline.third, digest.digest().joinToString("") { "%02x".format(it) })
            bitmap.recycle()
        }
    }

    @Test
    fun happyCharactersAreDefinedAndRenderWithDistinctSignatures() {
        val mobiHappy = CharacterArtwork.happy("friend:mobi")
        val lunaHappy = CharacterArtwork.happy("friend:luna")
        val mobiHeadphonesHappy = CharacterArtwork.happy("friend:mobi", "accessory:mobi_headphones")
        val mobiGogglesHappy = CharacterArtwork.happy("friend:mobi", "accessory:mobi_goggles")
        val lunaCapHappy = CharacterArtwork.happy("friend:luna", "accessory:luna_cap")
        val lunaSunglassesHappy = CharacterArtwork.happy("friend:luna", "accessory:luna_sunglasses")
        assertNotNull(mobiHappy)
        assertNotNull(lunaHappy)
        assertNotNull(mobiHeadphonesHappy)
        assertNotNull(mobiGogglesHappy)
        assertNotNull(lunaCapHappy)
        assertNotNull(lunaSunglassesHappy)
        listOf(mobiHappy, lunaHappy, mobiHeadphonesHappy, mobiGogglesHappy, lunaCapHappy, lunaSunglassesHappy)
            .forEach { asset ->
                val crop = requireNotNull(asset.crop)
                val context =
                    androidx.test.core.app.ApplicationProvider
                        .getApplicationContext<android.content.Context>()
                val options =
                    android.graphics.BitmapFactory
                        .Options()
                        .apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeResource(context.resources, asset.resourceId, options)
                val canvas = options.outHeight
                assertTrue(crop.x >= 0 && crop.y >= 0)
                assertTrue(crop.x + crop.width <= options.outWidth && crop.y + crop.height <= canvas)
                assertTrue(crop.height >= canvas * 0.87)
                assertEquals(1f, asset.visualScale)
            }

        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                Row {
                    PetAvatar(
                        modifier = Modifier.testTag("mobi-happy"),
                        friendId = "friend:mobi",
                        emotion = PetEmotion.HAPPY,
                    )
                    PetAvatar(
                        modifier = Modifier.testTag("luna-happy"),
                        friendId = "friend:luna",
                        emotion = PetEmotion.HAPPY,
                    )
                }
            }
        }

        val tags = listOf("mobi-happy", "luna-happy")
        val bounds = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        lateinit var signatures: Map<String, List<Int>>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            signatures =
                bounds.mapValues { (_, area) ->
                    val left = area.left.toInt()
                    val top = area.top.toInt()
                    val width = area.width.toInt()
                    val height = area.height.toInt()
                    IntArray(width * height)
                        .also { pixels ->
                            bitmap.getPixels(pixels, 0, width, left, top, width, height)
                        }.toList()
                }
            bitmap.recycle()
        }

        assertNotEquals(signatures.getValue("mobi-happy"), signatures.getValue("luna-happy"))
        assertTrue(signatures.getValue("mobi-happy").toSet().size > 100)
        assertTrue(signatures.getValue("luna-happy").toSet().size > 100)
    }

    @Test
    fun lasHappyArtworkDiffersFromHisNormalArtwork() {
        assertNotEquals(
            CharacterArtwork.preview("friend:las", null).resourceId,
            CharacterArtwork.happy("friend:las").resourceId,
        )
    }

    @Test
    fun lasHappyAvatarRendersDifferentArtFromIdle() {
        lateinit var view: View
        val emotion = mutableStateOf(PetEmotion.IDLE)
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                PetAvatar(
                    modifier = Modifier.testTag("las-avatar"),
                    friendId = "friend:las",
                    emotion = emotion.value,
                    isAnimated = false,
                )
            }
        }

        val area = compose.onNodeWithTag("las-avatar").fetchSemanticsNode().boundsInRoot

        fun capture(): List<Int> {
            lateinit var pixels: List<Int>
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                pixels =
                    IntArray(area.width.toInt() * area.height.toInt())
                        .also { values ->
                            bitmap.getPixels(
                                values,
                                0,
                                area.width.toInt(),
                                area.left.toInt(),
                                area.top.toInt(),
                                area.width.toInt(),
                                area.height.toInt(),
                            )
                        }.toList()
                bitmap.recycle()
            }
            return pixels
        }

        val idlePixels = capture()
        compose.runOnIdle { emotion.value = PetEmotion.HAPPY }
        assertNotEquals(idlePixels, capture())
    }

    @Test
    fun lunaAnimationManagerRetainsOnlyActiveCache() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        LunaIdleArtworkCache.getOrLoad(context)
        LunaHungryPartsCache.getOrLoad(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaRunAnimationCache.getOrLoadFrames(context)

        assertNotNull(LunaIdleArtworkCache.peek())
        assertNotNull(LunaHungryPartsCache.peek())
        assertNotNull(LunaSickAnimationCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())

        LunaAnimationManager.retainOnly(LunaActiveAnimation.IDLE)
        assertNotNull(LunaIdleArtworkCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())
        assertNull(LunaHungryPartsCache.peek())
        assertNull(LunaSickAnimationCache.peek())

        LunaHungryPartsCache.getOrLoad(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaAnimationManager.retainOnly(LunaActiveAnimation.RUN)
        assertNotNull(LunaIdleArtworkCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())
        assertNull(LunaHungryPartsCache.peek())
        assertNull(LunaSickAnimationCache.peek())

        LunaHungryPartsCache.getOrLoad(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaAnimationManager.retainOnly(LunaActiveAnimation.HUNGRY)
        assertNotNull(LunaIdleArtworkCache.peek())
        assertNotNull(LunaHungryPartsCache.peek())
        assertNull(LunaSickAnimationCache.peek())
        assertNull(LunaRunAnimationCache.peek())

        LunaAnimationManager.clearAll()
        assertNull(LunaIdleArtworkCache.peek())
        assertNull(LunaHungryPartsCache.peek())
        assertNull(LunaSickAnimationCache.peek())
        assertNull(LunaRunAnimationCache.peek())
    }

    @Test
    fun lasHungryAnimationAssetExistsAndPetAvatarRendersLasHungry() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertTrue(
            context.assets
                .list("characters/las/hungry/idle_breath")!!
                .contains("las_idle_breath_hungry_sprite.png"),
        )
        compose.setContent {
            MobiMonTheme {
                PetAvatar(
                    friendId = "friend:las",
                    emotion = PetEmotion.HUNGRY,
                )
            }
        }
    }

    @Test
    fun lasSickAnimationAssetExistsAndPetAvatarRendersLasSick() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertTrue(
            context.assets
                .list("characters/las/sick/idle_breath")!!
                .contains("las_idle_breath_sick_sprite.png"),
        )
        compose.setContent {
            MobiMonTheme {
                PetAvatar(
                    friendId = "friend:las",
                    emotion = PetEmotion.SICK,
                )
            }
        }
    }

    @Test
    fun lasMovementUsesDancingSpriteAndItsLoopHasNoEndJump() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sheet = requireNotNull(LasDanceSpriteCache.getOrLoad(context))
        assertEquals(1536, sheet.width)
        assertEquals(1024, sheet.height)
        assertEquals(0, LasDanceTimeline.frame(0))
        assertEquals(11, LasDanceTimeline.frame(11 * 85L))
        assertEquals(1, LasDanceTimeline.frame(21 * 85L))
        assertEquals(0, LasDanceTimeline.frame(22 * 85L))

        compose.setContent {
            MobiMonTheme {
                PetAvatar(friendId = "friend:las", isMoving = true)
            }
        }
        compose.onNodeWithTag("las-dance-frame", useUnmergedTree = true).assertExists()
    }
}
