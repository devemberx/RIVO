package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun mobiShowsItsSpriteOnTheFirstRenderedFrame() {
        MobiSpriteCache.clear()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MobiMonTheme {
                PetAvatar(friendId = "friend:mobi", accessoryId = "accessory:mobi_headphones")
            }
        }

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

        compose.onNodeWithTag("mobi-animation-frame-goggles", useUnmergedTree = true).assertExists()
        assertNull(MobiSpriteCache.peek("accessory:mobi_goggles"))
    }

    @Test
    fun mobiFirstFramesMatchTheirAnimationAtlases() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles").forEach { accessory ->
            val atlas = requireNotNull(MobiSpriteCache.getOrLoad(context, accessory)).asAndroidBitmap()
            val first = requireNotNull(MobiSpriteCache.firstFrame(context, accessory)).asAndroidBitmap()
            val expected = Bitmap.createBitmap(atlas, 0, 0, atlas.width / 6, atlas.height / 4)
            assertTrue("First frame must match atlas for $accessory", expected.sameAs(first))
            expected.recycle()
        }
    }

    @Test
    fun lunaShowsItsSpriteOnTheFirstRenderedFrame() {
        LunaAnimationCache.clear()
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
    fun mobiAnimationCacheLoadsOneSheetAndReusesIt() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sprite = requireNotNull(MobiSpriteCache.getOrLoad(context))
        assertEquals(627 * 6, sprite.width)
        assertEquals(627 * 4, sprite.height)
        assertTrue(sprite === MobiSpriteCache.getOrLoad(context))
        assertEquals(
            listOf("mobi_idle_breath_normal_01.png", "mobi_idle_breath_normal_sprite.png"),
            context.assets.list("characters/mobi/normal/idle_breath")!!.toList(),
        )

        val headphonesSprite = requireNotNull(MobiSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))
        assertEquals(627 * 6, headphonesSprite.width)
        assertEquals(627 * 4, headphonesSprite.height)
        assertTrue(headphonesSprite === MobiSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))

        val gogglesSprite = requireNotNull(MobiSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
        assertEquals(627 * 6, gogglesSprite.width)
        assertEquals(627 * 4, gogglesSprite.height)
        assertTrue(gogglesSprite === MobiSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
    }

    @Test
    fun mobiHungryAnimationCacheLoadsOneSheetAndReusesIt() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sprite = requireNotNull(MobiHungrySpriteCache.getOrLoad(context))
        assertEquals(256 * 6, sprite.width)
        assertEquals(256 * 4, sprite.height)
        assertTrue(sprite === MobiHungrySpriteCache.getOrLoad(context))
        assertEquals(
            listOf("mobi_hungry_normal_sprite.png"),
            context.assets.list("characters/mobi/normal/hungry")!!.toList(),
        )
    }

    @Test
    fun mobiRunSpriteCacheLoadsSheetFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context))
        assertEquals(3762, sprite.width)
        assertEquals(2508, sprite.height)
        assertTrue(sprite === MobiRunSpriteCache.getOrLoad(context))
        assertTrue(context.assets.list("characters/mobi/normal/run")!!.contains("mobi_run_left_normal_sprite.png"))

        val headphonesSprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))
        assertEquals(3762, headphonesSprite.width)
        assertEquals(2508, headphonesSprite.height)
        assertTrue(headphonesSprite === MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_headphones"))
        assertTrue(
            context.assets.list("characters/mobi/headphones/run")!!.contains("mobi_run_left_headphones_sprite.png"),
        )

        val gogglesSprite = requireNotNull(MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
        assertEquals(3762, gogglesSprite.width)
        assertEquals(2508, gogglesSprite.height)
        assertTrue(gogglesSprite === MobiRunSpriteCache.getOrLoad(context, "accessory:mobi_goggles"))
        assertTrue(context.assets.list("characters/mobi/goggles/run")!!.contains("mobi_run_left_goggles_sprite.png"))
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
    fun lunaAnimationCacheLoadsTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val frames = LunaAnimationCache.getOrLoadFrames(context)
        assertEquals(24, frames.size)
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
    fun lunaRunAnimationCacheLoadsTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val frames = LunaRunAnimationCache.getOrLoadFrames(context)
        assertEquals(24, frames.size)
    }

    @Test
    fun lunaHungryAnimationCacheLoadsTwentyFourFramesFromAssets() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val frames = LunaHungryAnimationCache.getOrLoadFrames(context)
        assertEquals(24, frames.size)
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
        assertEquals(24, LunaAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
        assertEquals(24, LunaRunAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
        assertEquals(24, LunaHungryAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
        assertEquals(24, LunaSickAnimationCache.getOrLoadFrames(context, appearance = LunaAppearance.HAT).size)
    }

    @Test
    fun lunaSunglassesFramesReplaceCachedNormalAndHatArtwork() =
        kotlinx.coroutines.test.runTest {
            val context =
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext<android.content.Context>()
            val loaders =
                listOf(
                    LunaAnimationCache::getOrLoadFrames,
                    LunaRunAnimationCache::getOrLoadFrames,
                    LunaHungryAnimationCache::getOrLoadFrames,
                    LunaSickAnimationCache::getOrLoadFrames,
                )
            val actions = listOf("idle_breath", "run", "hungry", "sick")
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
    fun itemIconsCropBoundsMatchItemSpans() {
        val headphonesCrop = CharacterArtwork.itemIcons.getValue("accessory:mobi_headphones").crop
        assertNotNull(headphonesCrop)
        assertEquals(0, headphonesCrop!!.x)
        assertEquals(475, headphonesCrop.width)
        assertEquals(150, headphonesCrop.y)
        assertEquals(470, headphonesCrop.height)

        val gogglesCrop = CharacterArtwork.itemIcons.getValue("accessory:mobi_goggles").crop
        assertNotNull(gogglesCrop)
        assertEquals(480, gogglesCrop!!.x)
        assertEquals(468, gogglesCrop.width)
        assertEquals(275, gogglesCrop.y)
        assertEquals(320, gogglesCrop.height)

        val capCrop = CharacterArtwork.itemIcons.getValue("accessory:luna_cap").crop
        assertNotNull(capCrop)
        assertEquals(0, capCrop!!.x)
        assertEquals(500, capCrop.width)
        assertEquals(140, capCrop.y)
        assertEquals(480, capCrop.height)

        val sunglassesCrop = CharacterArtwork.itemIcons.getValue("accessory:luna_sunglasses").crop
        assertNotNull(sunglassesCrop)
        assertEquals(510, sunglassesCrop!!.x)
        assertEquals(460, sunglassesCrop.width)
        assertEquals(285, sunglassesCrop.y)
        assertEquals(330, sunglassesCrop.height)
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
                val canvas = if (asset == mobiHappy) 2508 else 1254
                assertTrue(crop.x >= 0 && crop.y >= 0)
                assertTrue(crop.x + crop.width <= canvas && crop.y + crop.height <= canvas)
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
    fun hungryAndSickArtworkAreDefinedForMobiAndLuna() {
        val mobiHungry = CharacterArtwork.hungry("friend:mobi")
        val lunaHungry = CharacterArtwork.hungry("friend:luna")
        val lunaSick = CharacterArtwork.sick("friend:luna")
        assertNotNull(mobiHungry)
        assertNotNull(lunaHungry)
        assertNotNull(lunaSick)
        assertEquals(0.87f, lunaHungry.visualScale)
        assertEquals(0.87f, lunaSick.visualScale)
        assertEquals(R.drawable.mobimon_luna_hungry, lunaHungry.resourceId)
        assertEquals(R.drawable.mobimon_luna_sick, lunaSick.resourceId)
    }

    @Test
    fun lunaAnimationManagerRetainsOnlyActiveCache() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        LunaAnimationCache.getOrLoadFrames(context)
        LunaHungryAnimationCache.getOrLoadFrames(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaRunAnimationCache.getOrLoadFrames(context)

        assertNotNull(LunaAnimationCache.peek())
        assertNotNull(LunaHungryAnimationCache.peek())
        assertNotNull(LunaSickAnimationCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())

        LunaAnimationManager.retainOnly(LunaActiveAnimation.IDLE)
        assertNotNull(LunaAnimationCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())
        assertNull(LunaHungryAnimationCache.peek())
        assertNull(LunaSickAnimationCache.peek())

        LunaHungryAnimationCache.getOrLoadFrames(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaAnimationManager.retainOnly(LunaActiveAnimation.RUN)
        assertNotNull(LunaAnimationCache.peek())
        assertNotNull(LunaRunAnimationCache.peek())
        assertNull(LunaHungryAnimationCache.peek())
        assertNull(LunaSickAnimationCache.peek())

        LunaHungryAnimationCache.getOrLoadFrames(context)
        LunaSickAnimationCache.getOrLoadFrames(context)
        LunaAnimationManager.retainOnly(LunaActiveAnimation.HUNGRY)
        assertNull(LunaAnimationCache.peek())
        assertNotNull(LunaHungryAnimationCache.peek())
        assertNull(LunaSickAnimationCache.peek())
        assertNull(LunaRunAnimationCache.peek())

        LunaAnimationManager.clearAll()
        assertNull(LunaAnimationCache.peek())
        assertNull(LunaHungryAnimationCache.peek())
        assertNull(LunaSickAnimationCache.peek())
        assertNull(LunaRunAnimationCache.peek())
    }
}
