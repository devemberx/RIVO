package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h600dp-mdpi")
class StarlightYarnBasketTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun basketAnimatesAboveBackground() {
        show(true)
        val initial = pixels("initial")
        compose.mainClock.advanceTimeBy(2500)
        val glowing = pixels("glowing")
        assertNotEquals(initial, glowing)
        val side = minOf(view.width * 0.9f, view.height * 0.94f)
        val x = ((view.width - side) / 2f + side * 411f / 1254f).toInt()
        val y = ((view.height - side) / 2f + side * 895f / 1254f).toInt()
        val dimRed = android.graphics.Color.red(initial[y * view.width + x])
        val brightRed = android.graphics.Color.red(glowing[y * view.width + x])
        assertTrue("Fixed stars visibly dim and brighten", brightRed - dimRed > 50)
        compose.mainClock.advanceTimeBy(2500)
        assertNotEquals(initial, pixels("swaying"))
    }

    @Test fun reducedMotionKeepsBasketStill() {
        show(false)
        val initial = pixels("reduced")
        compose.mainClock.advanceTimeBy(2400)
        assertEquals(initial, pixels("reduced-later"))
    }

    @Test fun basketStaysFixedWhileBallRollsAndCharmsSwing() {
        val right = YarnBasketMotion(kotlin.math.PI.toFloat() / 2f)
        val left = YarnBasketMotion(3f * kotlin.math.PI.toFloat() / 2f)
        assertEquals(1076f, right.ballPoint(1064f, 1026f).x, 0.01f)
        assertEquals(1052f, left.ballPoint(1064f, 1026f).x, 0.01f)
        assertEquals(right.ballPoint(1064f, 1026f).y, left.ballPoint(1064f, 1026f).y, 0.01f)
        for (point in listOf(Offset(630f, 940f), Offset(500f, 1100f), Offset(800f, 600f))) {
            assertEquals(point, right.point(point.x, point.y))
            assertEquals(point, left.point(point.x, point.y))
        }
        assertNotEquals(Offset(1143f, 788f), right.point(1143f, 788f))
        assertNotEquals(Offset(891f, 925f), right.pendantPoint(891f, 925f))
        assertNotEquals(Offset(675f, 487f), right.point(675f, 487f))
        val still = YarnBasketMotion(null)
        assertEquals(Offset(1064f, 1026f), still.ballPoint(1064f, 1026f))
        // A rolling sphere barely moves its contact point horizontally.
        assertEquals(1064f, right.ballPoint(1064f, 1176f).x, 0.1f)
        val first = YarnBasketMotion(0f).mesh()
        val repeated = YarnBasketMotion(2f * kotlin.math.PI.toFloat()).mesh()
        first.indices.forEach { assertEquals(first[it], repeated[it], 0.002f) }
    }

    @Test fun frontStarStaysRigidAndIndependentFromRollingBall() {
        for (frame in 0..24) {
            val motion = YarnBasketMotion(frame * kotlin.math.PI.toFloat() / 12f)
            val a = motion.pendantPoint(840f, 920f)
            val b = motion.pendantPoint(940f, 920f)
            assertEquals(100f, (a - b).getDistance(), 0.002f)
            assertEquals(Offset(891f, 925f), motion.point(891f, 925f))
            val center = motion.ballPoint(1064f, 1026f)
            assertEquals(150f, (motion.ballPoint(1064f, 1176f) - center).getDistance(), 0.002f)
        }
        assertEquals(0f, basketStarGlow(0f), 0.001f)
        assertEquals(1f, basketStarGlow(kotlin.math.PI.toFloat() / 2f), 0.001f)
        assertEquals(0f, basketStarGlow(kotlin.math.PI.toFloat()), 0.001f)
    }

    @Test fun exportSeparatedLayersMotionReview() {
        show(true)
        for (frame in 0 until 40) {
            pixels("motion-%02d".format(frame))
            compose.mainClock.advanceTimeBy(250)
        }
    }

    @Test fun movingMeshNeverFoldsOrTears() {
        for (frame in 0..15) {
            val mesh = YarnBasketMotion(frame * kotlin.math.PI.toFloat() / 8f).mesh()
            for (y in 0 until 96) {
                for (x in 0 until 96) {
                    val a = (y * 97 + x) * 2
                    val b = a + 2
                    val c = a + 97 * 2
                    val d = c + 2

                    fun cross(
                        i: Int,
                        j: Int,
                        k: Int,
                    ) = (mesh[j] - mesh[i]) * (mesh[k + 1] - mesh[i + 1]) -
                        (mesh[j + 1] - mesh[i + 1]) * (mesh[k] - mesh[i])
                    assertTrue(cross(a, b, c) > 0f)
                    assertTrue(cross(b, d, c) > 0f)
                }
            }
        }
    }

    private fun show(motion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motion) {
                Box(Modifier.size(800.dp, 600.dp)) {
                    Image(
                        painterResource(R.drawable.pet_home_background_day),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    StarlightYarnBasket(Modifier.fillMaxSize(), centered = true)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun pixels(name: String): List<Int> {
        lateinit var result: List<Int>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result =
                IntArray(view.width * view.height)
                    .also {
                        bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height)
                    }.toList()
            val output = File("build/reports/yarn-basket/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return result
    }
}
