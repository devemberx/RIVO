package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobiWarningAnimationTest {
    @Test
    fun motionAdvancesWithinOldFrameIntervalsAndClosesTheLoop() {
        val first = MobiSickMotion.at(0)
        val next = MobiSickMotion.at(16_000_000)
        assertTrue(first.lean != next.lean)
        assertEquals(first, MobiSickMotion.at(4_800_000_000))
        val last = MobiSickMotion.at(4_799_999_999)
        assertEquals(first.breath, last.breath, .000001f)
        assertEquals(first.lean, last.lean, .000001f)
        assertEquals(MobiSickMotion.x(first.orbit), MobiSickMotion.x(last.orbit), .000001)
        assertEquals(MobiSickMotion.y(first.orbit), MobiSickMotion.y(last.orbit), .000001)
    }

    @Test
    fun eyesBrieflyOpenAndReturnToOriginalClosedPose() {
        assertEquals(0f, MobiSickMotion.at(0).eyes)
        assertEquals(.5f, MobiSickMotion.at(1_650_000_000).eyes)
        assertEquals(1f, MobiSickMotion.at(2_200_000_000).eyes)
        assertEquals(1f, MobiSickMotion.at(3_300_000_000).eyes)
        assertEquals(0f, MobiSickMotion.at(3_800_000_000).eyes)
    }

    @Test
    fun movingGapsKeepClearanceAndNeverEraseACompleteArc() {
        for (ms in 0 until 2400 step 4) {
            val orbit = MobiSickMotion.at(ms * 1_000_000L).orbit
            for (i in 0..2) {
                val a = orbit + i * 2 * PI / 3
                val b = a + 2 * PI / 3
                assertTrue(a + MobiSickMotion.gap(a, 1) < b - MobiSickMotion.gap(b, -1))
                for (direction in listOf(-1, 1)) {
                    val end = a + direction * MobiSickMotion.gap(a, direction)
                    val distance =
                        hypot(
                            MobiSickMotion.x(end) - MobiSickMotion.x(a),
                            MobiSickMotion.y(end) - MobiSickMotion.y(a),
                        )
                    assertTrue(
                        "Rounded caps stay five pixels outside each star",
                        distance - MobiSickMotion.radius(a) - 5 >= 4.999,
                    )
                }
            }
        }
    }

    @Test
    fun everyEquippedAppearanceLoadsSinglePoseAndSharedStar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            val image = requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context, accessory))
            val expectedSize = if (accessory == null) 408 else 1254
            assertEquals(expectedSize, image.width)
            assertEquals(expectedSize, image.height)
            val files = context.assets.list("characters/mobi/${mobiAppearanceName(accessory)}/sick")!!
            assertTrue(files.none { "sprite" in it })
        }
        assertNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
    }
}
