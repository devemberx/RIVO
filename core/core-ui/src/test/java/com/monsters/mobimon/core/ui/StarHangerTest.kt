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
@Config(sdk = [34], qualifiers = "w800dp-h400dp-mdpi")
class StarHangerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun hangerAnimatesAboveBackground() {
        show(true)
        val initial = pixels("initial")
        compose.mainClock.advanceTimeBy(1800)
        assertNotEquals(initial, pixels("glowing"))
        compose.mainClock.advanceTimeBy(1800)
        assertNotEquals(initial, pixels("swaying"))
    }

    @Test fun reducedMotionKeepsHangerStill() {
        show(false)
        val initial = pixels("reduced")
        compose.mainClock.advanceTimeBy(2400)
        assertEquals(initial, pixels("reduced-later"))
    }

    @Test fun segmentsLagWhileSuspensionPointAndRigidArtworkStayIntact() {
        val mesh = starHangerMesh(0f)

        fun coordinate(
            row: Int,
            column: Int,
            axis: Int,
        ) = mesh[(row * 9 + column) * 2 + axis]
        assertEquals(512f, coordinate(0, 4, 0), 0.001f)
        assertEquals(0f, coordinate(0, 4, 1), 0.001f)
        val ribbonSlope = coordinate(30, 8, 1) - coordinate(30, 0, 1)
        val starSlope = coordinate(90, 8, 1) - coordinate(90, 0, 1)
        val moonSlope = coordinate(150, 8, 1) - coordinate(150, 0, 1)
        assertTrue(ribbonSlope > starSlope && starSlope > moonSlope)
        // The centerline through the solid star remains 320 pixels long.
        val dx = coordinate(110, 4, 0) - coordinate(70, 4, 0)
        val dy = coordinate(110, 4, 1) - coordinate(70, 4, 1)
        assertEquals(320f, kotlin.math.sqrt(dx * dx + dy * dy), 0.01f)
        val nextCycle = starHangerMesh(2f * kotlin.math.PI.toFloat())
        mesh.indices.forEach { assertEquals(mesh[it], nextCycle[it], 0.002f) }
        val still = starHangerMesh(null)
        assertEquals(512f, still[(192 * 9 + 4) * 2], 0.001f)
        assertEquals(1536f, still[(192 * 9 + 4) * 2 + 1], 0.001f)
    }

    private fun show(motion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motion) {
                Box(Modifier.size(800.dp, 400.dp)) {
                    Image(
                        painterResource(R.drawable.pet_home_background_day),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    StarHanger(Modifier.fillMaxSize())
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
            val output = File("build/reports/star-hanger/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return result
    }
}
