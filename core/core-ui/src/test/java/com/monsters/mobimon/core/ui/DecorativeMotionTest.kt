package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h400dp-mdpi")
class DecorativeMotionTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var view: View

    @Test
    fun mobiHungryRecoveryKeepsOutgoingPoseDuringFade() {
        var hungry by mutableStateOf(true)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:mobi",
                vehicleHungry = hungry,
            )
        }
        compose.onNodeWithTag("mobi-hungry-layer").assertExists()

        updateStateAndDraw { hungry = false }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("mobi-hungry-layer").assertExists()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("mobi-hungry-layer").assertDoesNotExist()
    }

    @Test
    fun mobiWarningRecoveryKeepsOutgoingPoseDuringFade() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context))
        requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        var warning by mutableStateOf(true)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:mobi",
                vehicleWarning = warning,
            )
        }
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("mobi-sick-layer").assertExists()

        updateStateAndDraw { warning = false }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("mobi-sick-layer").assertExists()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("mobi-sick-layer").assertDoesNotExist()
    }

    @Test
    fun mobiHungryAndSickCrossfadeWithoutNormalPose() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context))
        requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        var hungry by mutableStateOf(true)
        var warning by mutableStateOf(false)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:mobi",
                vehicleHungry = hungry,
                vehicleWarning = warning,
            )
        }

        updateStateAndDraw {
            hungry = false
            warning = true
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("mobi-hungry-layer").assertExists()
        compose.onNodeWithTag("mobi-sick-layer").assertExists()
        compose.onNodeWithTag("mobi-animation-frame-normal").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(240)
        updateStateAndDraw {
            warning = false
            hungry = true
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("mobi-sick-layer").assertExists()
        compose.onNodeWithTag("mobi-hungry-layer").assertExists()
        compose.onNodeWithTag("mobi-animation-frame-normal").assertDoesNotExist()
    }

    @Test
    fun lunaHungryAndSickCrossfadeWithoutNormalPose() {
        var hungry by mutableStateOf(true)
        var warning by mutableStateOf(false)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:luna",
                vehicleHungry = hungry,
                vehicleWarning = warning,
            )
        }

        updateStateAndDraw {
            hungry = false
            warning = true
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("luna-state-hungry").assertExists()
        compose.onNodeWithTag("luna-state-sick").assertExists()
        compose.onNodeWithTag("luna-state-idle").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(240)
        updateStateAndDraw {
            warning = false
            hungry = true
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("luna-state-sick").assertExists()
        compose.onNodeWithTag("luna-state-hungry").assertExists()
        compose.onNodeWithTag("luna-state-idle").assertDoesNotExist()
    }

    @Test
    fun mobiVehicleStatusChangesImmediatelyWithReducedMotion() {
        var hungry by mutableStateOf(false)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                PetAvatar(
                    modifier = Modifier.size(180.dp),
                    friendId = "friend:mobi",
                    vehicleHungry = hungry,
                )
            }
        }
        updateStateAndDraw { hungry = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("mobi-hungry-layer").assertExists()
        compose.onNodeWithTag("mobi-normal-layer").assertDoesNotExist()
    }

    @Test
    fun lunaVehicleStatesOverlapDuringFadeAndSettleOnLatestStatus() {
        var hungry by mutableStateOf(false)
        var warning by mutableStateOf(false)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:luna",
                vehicleHungry = hungry,
                vehicleWarning = warning,
            )
        }
        compose.onNodeWithTag("luna-state-idle").assertExists()

        updateStateAndDraw { hungry = true }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("luna-state-idle").assertExists()
        compose.onNodeWithTag("luna-state-hungry").assertExists()

        updateStateAndDraw { warning = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("luna-state-sick").assertExists()
        val outgoingCount =
            compose.onAllNodesWithTag("luna-state-idle").fetchSemanticsNodes().size +
                compose.onAllNodesWithTag("luna-state-hungry").fetchSemanticsNodes().size
        assertTrue("Rapid status changes retain only one outgoing pose", outgoingCount == 1)
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("luna-state-sick").assertExists()
        compose.onNodeWithTag("luna-state-idle").assertDoesNotExist()
        compose.onNodeWithTag("luna-state-hungry").assertDoesNotExist()
    }

    @Test
    fun lunaStatusFadeReversesWithoutDroppingTheOutgoingPose() {
        var hungry by mutableStateOf(false)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp),
                friendId = "friend:luna",
                vehicleHungry = hungry,
            )
        }

        updateStateAndDraw { hungry = true }
        compose.mainClock.advanceTimeBy(80)
        updateStateAndDraw { hungry = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("luna-state-idle").assertExists()
        compose.onNodeWithTag("luna-state-hungry").assertExists()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("luna-state-hungry").assertDoesNotExist()
    }

    @Test
    fun lunaVehicleStatusChangesImmediatelyWithReducedMotion() {
        var hungry by mutableStateOf(false)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                PetAvatar(
                    modifier = Modifier.size(180.dp),
                    friendId = "friend:luna",
                    vehicleHungry = hungry,
                )
            }
        }
        updateStateAndDraw { hungry = true }
        compose.onNodeWithTag("luna-state-hungry").assertExists()
        compose.onNodeWithTag("luna-state-idle").assertDoesNotExist()
    }

    @Test
    fun petAvatarsKeepBreathingWithReducedMotion() {
        var motionEnabled by mutableStateOf(true)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motionEnabled) {
                Row {
                    PetAvatar(Modifier.size(180.dp).testTag("mobi"))
                    PetAvatar(Modifier.size(180.dp).testTag("luna"), friendId = "friend:luna")
                }
            }
        }
        awaitLunaAnimation("luna")
        val firstMobi = pixels("mobi")
        val firstLuna = pixels("luna")
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Mobi breathes with motion enabled", firstMobi != pixels("mobi"))
        assertTrue("Luna breathes with motion enabled", firstLuna != pixels("luna"))

        updateStateAndDraw { motionEnabled = false }
        awaitLunaAnimation("luna")
        val reducedMobi = pixels("mobi")
        val reducedLuna = pixels("luna")
        compose.mainClock.advanceTimeBy(320)

        assertTrue("Mobi keeps breathing with reduced motion", reducedMobi != pixels("mobi"))
        assertTrue("Luna keeps breathing with reduced motion", reducedLuna != pixels("luna"))
    }

    @Test
    fun sickGesturesContinueWithoutTravelButExplicitPreviewsStayStill() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context))
        requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                Row {
                    PetAvatar(Modifier.size(180.dp).testTag("sick-active"), vehicleWarning = true)
                    PetAvatar(Modifier.size(180.dp).testTag("sick-still"), vehicleWarning = true, isAnimated = false)
                }
            }
        }
        val active = pixels("sick-active")
        val still = pixels("sick-still")
        compose.mainClock.advanceTimeBy(480)
        assertTrue("Home travel preference must not freeze sick gestures", active != pixels("sick-active"))
        assertTrue("Explicit nonanimated preview holds the source pose", still == pixels("sick-still"))
    }

    @Test
    fun movingLunaBreathesInPlaceWithReducedMotion() {
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                Row {
                    PetAvatar(Modifier.size(180.dp).testTag("moving"), friendId = "friend:luna", isMoving = true)
                    PetAvatar(Modifier.size(180.dp).testTag("idle"), friendId = "friend:luna")
                    CompositionLocalProvider(LocalMobiMonMotionEnabled provides true) {
                        PetAvatar(Modifier.size(180.dp).testTag("running"), friendId = "friend:luna", isMoving = true)
                    }
                }
            }
        }
        // The idle fallback already breathes while the run frames are still decoding.
        awaitLunaAnimation("running")
        awaitLunaAnimation("moving")
        val first = pixels("moving")
        compose.mainClock.advanceTimeBy(320)
        val moving = pixels("moving")
        val idle = pixels("idle")
        val running = pixels("running")

        assertTrue("Reduced motion still breathes", first != moving)
        // Neighbouring cells rasterize slightly differently, so compare against the run cycle's distance.
        val idleDistance = moving.indices.count { moving[it] != idle[it] }
        val runDistance = moving.indices.count { moving[it] != running[it] }
        assertTrue(
            "Reduced motion swaps running for the idle breath: idle=$idleDistance, run=$runDistance",
            idleDistance * 4 < runDistance,
        )
    }

    @Test
    fun explicitAvatarOptOutKeepsArtStaticWithMotionEnabled() {
        show { PetAvatar(Modifier.size(180.dp).testTag("mobi"), isAnimated = false) }
        val mobi = pixels("mobi")
        compose.mainClock.advanceTimeBy(480)

        assertTrue("Explicit avatar opt-out remains static", mobi == pixels("mobi"))
    }

    @Test
    fun lunaWithHatAnimatesAcrossMotionStates() {
        show {
            Row {
                PetAvatar(
                    modifier = Modifier.size(180.dp).testTag("luna_hat_idle"),
                    friendId = "friend:luna",
                    accessoryId = "accessory:luna_cap",
                )
                PetAvatar(
                    modifier = Modifier.size(180.dp).testTag("luna_hat_moving"),
                    friendId = "friend:luna",
                    accessoryId = "accessory:luna_cap",
                    isMoving = true,
                )
                PetAvatar(
                    modifier = Modifier.size(180.dp).testTag("luna_hat_hungry"),
                    friendId = "friend:luna",
                    accessoryId = "accessory:luna_cap",
                    emotion = PetEmotion.HUNGRY,
                )
                PetAvatar(
                    modifier = Modifier.size(180.dp).testTag("luna_hat_sick"),
                    friendId = "friend:luna",
                    accessoryId = "accessory:luna_cap",
                    emotion = PetEmotion.SICK,
                )
            }
        }
        listOf("luna_hat_idle", "luna_hat_moving", "luna_hat_hungry", "luna_hat_sick").forEach(::awaitLunaAnimation)
        val firstIdle = pixels("luna_hat_idle")
        val firstMoving = pixels("luna_hat_moving")
        val firstHungry = pixels("luna_hat_hungry")
        val firstSick = pixels("luna_hat_sick")

        compose.mainClock.advanceTimeBy(320)

        assertTrue("Luna with hat idle breathes over time", firstIdle != pixels("luna_hat_idle"))
        assertTrue("Luna with hat moves/runs over time", firstMoving != pixels("luna_hat_moving"))
        assertTrue("Luna with hat hungry animates over time", firstHungry != pixels("luna_hat_hungry"))
        assertTrue("Luna with hat sick animates over time", firstSick != pixels("luna_hat_sick"))
    }

    @Test
    fun lunaSunglassesAnimateAndSwitchAcrossAllStates() {
        var accessory by mutableStateOf<String?>(null)
        var emotion by mutableStateOf(PetEmotion.IDLE)
        var moving by mutableStateOf(false)
        show {
            PetAvatar(
                Modifier.size(180.dp).testTag("luna"),
                friendId = "friend:luna",
                accessoryId = accessory,
                emotion = emotion,
                isMoving = moving,
            )
        }
        awaitLunaAnimation("luna")
        val normal = pixels("luna")
        updateStateAndDraw { accessory = "accessory:luna_sunglasses" }
        assertTrue("Equipping sunglasses changes rendered artwork", normal != pixels("luna"))
        awaitLunaAnimation("luna")
        for (state in listOf(PetEmotion.IDLE, PetEmotion.HUNGRY, PetEmotion.SICK)) {
            updateStateAndDraw { emotion = state }
            compose.mainClock.advanceTimeBy(240)
            awaitLunaAnimation("luna")
            val first = pixels("luna")
            compose.mainClock.advanceTimeBy(320)
            assertTrue("Sunglasses animate in $state", first != pixels("luna"))
            saveLunaReview(state.name.lowercase())
        }
        updateStateAndDraw {
            emotion = PetEmotion.IDLE
            moving = true
        }
        awaitLunaAnimation("luna")
        val run = pixels("luna")
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Sunglasses run animation advances", run != pixels("luna"))
        saveLunaReview("run")
    }

    @Test
    fun particlesStopWhenMotionPreferenceChanges() {
        var motionEnabled by mutableStateOf(true)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motionEnabled) {
                FallingParticlesEffect(Modifier.size(300.dp).testTag("particles"))
            }
        }
        val first = pixels("particles")
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Enabled particles advance", first != pixels("particles"))

        updateStateAndDraw { motionEnabled = false }
        val stopped = pixels("particles")
        compose.mainClock.advanceTimeBy(480)

        assertTrue("Particles stop after the preference changes", stopped == pixels("particles"))
    }

    @Test
    fun explicitParticleOptOutKeepsDecorationStatic() {
        show { FallingParticlesEffect(Modifier.size(300.dp).testTag("particles"), isAnimated = false) }
        val first = pixels("particles")
        compose.mainClock.advanceTimeBy(480)

        assertTrue("Explicit particle opt-out remains static", first == pixels("particles"))
    }

    @Test
    fun particleColorChangesWithoutChangingType() {
        var color by mutableStateOf(Color.Red)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                FallingParticlesEffect(
                    Modifier.size(300.dp).testTag("particles"),
                    particleType = ParticleType.SNOW,
                    particleColor = color,
                )
            }
        }
        assertTrue(pixels("particles").any { android.graphics.Color.red(it) > android.graphics.Color.blue(it) + 40 })
        updateStateAndDraw { color = Color.Blue }

        assertTrue(pixels("particles").any { android.graphics.Color.blue(it) > android.graphics.Color.red(it) + 40 })
    }

    @Test
    fun particleSizeChangesWithoutChangingType() {
        var particleSize by mutableStateOf(4.dp)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                FallingParticlesEffect(
                    Modifier.size(300.dp).testTag("particles"),
                    particleType = ParticleType.SNOW,
                    minSize = particleSize,
                    maxSize = particleSize,
                    particleColor = Color.Red,
                )
            }
        }
        val initialArea =
            pixels("particles").count {
                android.graphics.Color.red(it) >
                    android.graphics.Color.blue(it) + 40
            }
        updateStateAndDraw { particleSize = 20.dp }
        val enlargedArea =
            pixels("particles").count {
                android.graphics.Color.red(it) >
                    android.graphics.Color.blue(it) + 40
            }

        assertTrue("Updated particle size must affect the rendered area", enlargedArea > initialArea * 4)
    }

    @Test
    fun mobiSpriteKeepsLayoutBoundsAcrossBreathingAndTilt() {
        show { PetAvatar(Modifier.size(240.dp).testTag("mobi")) }
        val bounds = compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot
        val initial = pixels("mobi")
        compose.mainClock.advanceTimeBy(2_150)
        assertTrue("Sprite and tilt advance", initial != pixels("mobi"))
        assertTrue(
            "Animation does not remeasure its parent",
            bounds == compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot,
        )
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = java.io.File("build/reports/mobi-idle-sprite-review.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.mainClock.advanceTimeBy(4_300)
        assertTrue(
            "Opposite tilt retains layout",
            bounds == compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot,
        )
    }

    @Test
    fun mobiBlendsBetweenSourceTicksWithoutFadingItsOpaqueBody() {
        show { PetAvatar(Modifier.size(240.dp).testTag("mobi")) }
        val initial = pixels("mobi")
        compose.mainClock.advanceTimeBy(64)
        val middle = pixels("mobi")
        assertTrue("Motion progresses inside a source-frame interval", initial != middle)
        for (step in 0..24) {
            val sample = pixels("mobi")
            // The original body is near-opaque (alpha 253), not 255. Allow two levels of raster rounding.
            assertTrue("Blending preserves source body opacity", (sample[130 * 240 + 120] ushr 24) >= 251)
            compose.mainClock.advanceTimeBy(32)
        }
    }

    @Test
    fun warningCrossfadesToCollapsedSpriteAndReturnsWithoutChangingBounds() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        assertTrue(context.assets.list("characters/mobi/normal/sick")!!.none { "transition" in it })
        val sprite = requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context))
        requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        assertTrue(sprite === MobiCollapsedSpriteCache.getOrLoad(context))
        assertTrue(
            sprite.width == MobiCollapsedSpriteCache.CELL &&
                sprite.height == MobiCollapsedSpriteCache.CELL,
        )
        var warning by mutableStateOf(false)
        show {
            MobiIdleBreathAnimation(
                Modifier.size(240.dp).testTag("mobi"),
                vehicleWarning = warning,
                animateNormal = false,
            )
        }
        val bounds = compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot
        val normal = pixels("mobi")
        updateStateAndDraw { warning = true }
        awaitMobiSickArtwork()
        compose.mainClock.advanceTimeBy(800)
        val falling = pixels("mobi")
        assertTrue(normal != falling)
        updateStateAndDraw { warning = false }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(falling != pixels("mobi"))
        updateStateAndDraw { warning = true }
        awaitMobiSickArtwork()
        compose.mainClock.advanceTimeBy(2600)
        val collapsed = pixels("mobi")
        compose.mainClock.advanceTimeBy(700)
        assertTrue("Collapsed sprite animation remains alive", collapsed != pixels("mobi"))
        compose.mainClock.advanceTimeBy(12_000)
        assertTrue("Collapsed sprite keeps animating across repeated cycles", collapsed != pixels("mobi"))
        assertTrue(bounds == compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot)
        updateStateAndDraw { warning = false }
        compose.mainClock.advanceTimeBy(2600)
        assertTrue("Recovery returns to existing normal renderer", normal == pixels("mobi"))
        assertTrue(bounds == compose.onNodeWithTag("mobi").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun collapsedPoseLoadsAndMovesContinuously() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val sprite = requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context))
        requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        assertTrue(
            sprite.width == MobiCollapsedSpriteCache.CELL &&
                sprite.height == MobiCollapsedSpriteCache.CELL,
        )
        show {
            MobiIdleBreathAnimation(
                Modifier.size(240.dp).testTag("collapsed"),
                vehicleWarning = true,
                animateNormal = false,
            )
        }
        awaitMobiSickArtwork()
        compose.mainClock.advanceTimeBy(400)
        val frameA = pixels("collapsed")
        compose.mainClock.advanceTimeBy(500)
        val frameB = pixels("collapsed")
        assertTrue("Collapsed source pose moves continuously over time", frameA != frameB)
    }

    private fun awaitMobiSickArtwork() {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("mobi-sick-layer").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun hungryThoughtHoldDoesNotInheritIdleSway() {
        show { NormalMobiHungryAnimation(Modifier.size(256.dp).testTag("hungry")) }
        pixels("hungry")
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(2_000)
        val thought = pixels("hungry")
        compose.mainClock.advanceTimeBy(200)
        val held = pixels("hungry")

        fun visibleChannel(
            pixel: Int,
            shift: Int,
        ): Int {
            val alpha = pixel ushr 24
            return if (shift == 24) alpha else ((pixel ushr shift) and 255) * alpha / 255
        }
        val maximumChannelChange =
            thought.zip(held).maxOf { (first, second) ->
                listOf(0, 8, 16, 24).maxOf { shift ->
                    kotlin.math.abs(visibleChannel(first, shift) - visibleChannel(second, shift))
                }
            }
        // Premultiplied frame blending can round an unchanged channel by one level.
        assertTrue("Thought hold must not sway: channel change $maximumChannelChange", maximumChannelChange <= 2)
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("Authored hunger expressions still advance", thought != pixels("hungry"))
    }

    @Test
    fun hungryEquipmentSwitchUsesTheNewItemAndReducedMotionStaysStill() {
        var accessory by mutableStateOf<String?>(null)
        show {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                NormalMobiHungryAnimation(Modifier.size(256.dp), accessoryId = accessory)
            }
        }
        var previous = pixels("mobi-hungry-normal")
        for (item in listOf("accessory:mobi_headphones", "accessory:mobi_goggles")) {
            updateStateAndDraw { accessory = item }
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("mobi-hungry-${mobiAppearanceName(item)}").fetchSemanticsNodes().isNotEmpty()
            }
            val equipped = pixels("mobi-hungry-${mobiAppearanceName(item)}")
            assertTrue("Changing equipment must change the hungry pose", previous != equipped)
            compose.mainClock.advanceTimeBy(1_000)
            assertTrue(
                "Reduced-motion hungry pose must stay still",
                equipped == pixels("mobi-hungry-${mobiAppearanceName(item)}"),
            )
            previous = equipped
        }
    }

    private fun show(content: @Composable () -> Unit) {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        requireNotNull(MobiSpriteCache.getOrLoad(context))
        requireNotNull(MobiHungryArtworkCache.getOrLoad(context, null))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme(content = content)
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun awaitLunaAnimation(tag: String) {
        val first = pixels(tag)
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(320)
            first != pixels(tag)
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun updateStateAndDraw(update: () -> Unit) {
        compose.runOnIdle {
            update()
            // Compose 1.6's manual clock does not flush Android's posted snapshot notifications.
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun saveLunaReview(state: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = java.io.File("build/reports/luna-sunglasses-$state.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun pixels(tag: String): List<Int> {
        val area = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        lateinit var result: List<Int>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = area.width.toInt()
            val height = area.height.toInt()
            result =
                IntArray(width * height)
                    .also { bitmap.getPixels(it, 0, width, area.left.toInt(), area.top.toInt(), width, height) }
                    .toList()
            bitmap.recycle()
        }
        return result
    }

    @Test
    fun lunaIdleRendersBetweenSpriteTicksAndReturnsToStillPose() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        LunaIdleArtworkCache.getOrLoad(context)
        var animate by mutableStateOf(false)
        show {
            LunaIdleBreathAnimation(
                modifier = Modifier.size(300.dp).testTag("smooth-idle"),
                animateFrames = animate,
            )
        }
        val still = pixels("smooth-idle")
        updateStateAndDraw { animate = true }
        awaitLunaAnimation("smooth-idle")
        // A 90ms sprite player must repeat images over six 16ms samples.
        // Away from the deliberately crisp blink, every display tick now changes.
        val samples =
            (0 until 6).map {
                compose.mainClock.advanceTimeByFrame()
                pixels("smooth-idle")
            }
        assertTrue("Breathing must update between source frames", samples.zipWithNext().all { (a, b) -> a != b })
        updateStateAndDraw { animate = false }
        assertTrue("Still preview restores canonical frame", still == pixels("smooth-idle"))
        compose.mainClock.advanceTimeBy(300)
        assertTrue("Still preview stays unchanged", still == pixels("smooth-idle"))
    }

    @Test
    fun lunaPlaysHungryAndSickAnimationsWhenRequested() {
        show {
            Row {
                PetAvatar(
                    Modifier.size(180.dp).testTag("mobi-hungry"),
                    friendId = "friend:mobi",
                    vehicleHungry = true,
                )
                PetAvatar(
                    Modifier.size(180.dp).testTag("luna-hungry"),
                    friendId = "friend:luna",
                    vehicleHungry = true,
                )
                PetAvatar(
                    Modifier.size(180.dp).testTag("luna-sick"),
                    friendId = "friend:luna",
                    vehicleWarning = true,
                )
            }
        }
        awaitLunaAnimation("luna-hungry")
        awaitLunaAnimation("luna-sick")
        compose.onNodeWithTag("mobi-hungry-normal", useUnmergedTree = true).assertExists()
        val firstMobiHungry = pixels("mobi-hungry")
        val firstHungry = pixels("luna-hungry")
        val firstSick = pixels("luna-sick")
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Luna animates while hungry", firstHungry != pixels("luna-hungry"))
        assertTrue("Luna animates while sick", firstSick != pixels("luna-sick"))
        // Mobi deliberately holds the carrot and expression; a 320ms window may be stationary.
        var mobiChanged = firstMobiHungry != pixels("mobi-hungry")
        repeat((MobiHungryTimeline.CYCLE_MS / 320).toInt() + 1) {
            if (!mobiChanged) {
                compose.mainClock.advanceTimeBy(320)
                mobiChanged = firstMobiHungry != pixels("mobi-hungry")
            }
        }
        assertTrue("Mobi advances within its hungry cycle", mobiChanged)
    }

    @Test
    fun lunaPlaysHungryAndSickAnimationsWhileMoving() {
        show {
            Row {
                PetAvatar(
                    Modifier.size(180.dp).testTag("luna-moving-hungry"),
                    friendId = "friend:luna",
                    isMoving = true,
                    vehicleHungry = true,
                )
                PetAvatar(
                    Modifier.size(180.dp).testTag("luna-moving-sick"),
                    friendId = "friend:luna",
                    isMoving = true,
                    vehicleWarning = true,
                )
            }
        }
        awaitLunaAnimation("luna-moving-hungry")
        awaitLunaAnimation("luna-moving-sick")
        val firstHungry = pixels("luna-moving-hungry")
        val firstSick = pixels("luna-moving-sick")
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Luna animates while moving hungry", firstHungry != pixels("luna-moving-hungry"))
        assertTrue("Luna animates while moving sick", firstSick != pixels("luna-moving-sick"))
    }

    @Test
    fun lunaIdlePoseMaintainsDirectionForEveryAppearance() {
        var accessory by mutableStateOf<String?>(null)
        var movingLeft by mutableStateOf(true)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp).testTag("luna-idle"),
                friendId = "friend:luna",
                accessoryId = accessory,
                isMoving = false,
                isAnimated = false,
                movingLeft = movingLeft,
            )
        }
        // Freeze breathing so direction is compared at the same pose and pixel origin.
        for (item in listOf(null, "accessory:luna_cap", "accessory:luna_sunglasses")) {
            updateStateAndDraw {
                accessory = item
                movingLeft = true
            }
            val left = pixels("luna-idle")
            updateStateAndDraw { movingLeft = false }
            assertTrue("Idle must not mirror $item", left == pixels("luna-idle"))
        }
    }

    @Test
    fun lunaRunAnimationMirrorsWhenMovingRight() {
        var movingLeft by mutableStateOf(true)
        show {
            PetAvatar(
                modifier = Modifier.size(180.dp).testTag("luna-run"),
                friendId = "friend:luna",
                isMoving = true,
                movingLeft = movingLeft,
            )
        }
        val runLeftPixels = pixels("luna-run")
        updateStateAndDraw { movingLeft = false }
        val runRightPixels = pixels("luna-run")
        assertTrue(
            "Luna running animation must mirror when moving right",
            runLeftPixels != runRightPixels,
        )
    }
}
