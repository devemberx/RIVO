package com.monsters.mobimon.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import com.monsters.mobimon.testing.ComposeTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupPreviewViewTest {
    @Test
    fun rendersTheSkyBeforeFeatureInitializationAndInitializesOnlyOnce() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            var initialized = 0
            val preview = StartupPreviewView(activity) { initialized++ }
            activity.setContentView(preview)
            assertEquals(0, initialized)

            preview.layout(0, 0, 800, 400)
            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(bitmap))
            assertEquals(0, initialized)
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(400, 200)))
            assertNotEquals(bitmap.getPixel(0, 0), bitmap.getPixel(400, 200))

            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, initialized)
            preview.draw(Canvas(bitmap))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, initialized)
        }
    }

    @Test
    fun detachedPreviewDoesNotInitializeAnAbandonedScreen() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            var initialized = false
            val preview = StartupPreviewView(activity) { initialized = true }
            activity.setContentView(preview)
            preview.layout(0, 0, 800, 400)
            preview.draw(Canvas(Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)))
            activity.setContentView(View(activity))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(false, initialized)
        }
    }
}
