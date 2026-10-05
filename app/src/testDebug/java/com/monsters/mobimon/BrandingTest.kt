package com.monsters.mobimon

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.ui.StartupBlue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandingTest {
    @Test
    fun debugVariantUsesMobiMonApplicationId() {
        assertEquals("com.monsters.mobimon.demo", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun debugLauncherDisplaysMobiMonName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = context.packageManager.getApplicationLabel(context.applicationInfo)

        assertEquals("MobiMon Demo", label.toString())
    }

    @Test
    fun debugInstallExposesOnlyTheMainLauncher() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
        val activities = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals(listOf("com.monsters.mobimon.MainActivity"), activities.map { it.activityInfo.name })
    }

    @Test
    fun launchWindowUsesApprovedSkyWithoutADuplicateNativeIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = context.resources.newTheme().apply { applyStyle(context.applicationInfo.theme, true) }
        val window = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowBackground, window, true))
        assertEquals(R.drawable.pet_background_startup_common, window.resourceId)
        val background = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, background, true))
        assertEquals(StartupBlue.toArgb(), background.data)
        listOf(
            android.R.attr.windowSplashScreenAnimatedIcon,
            android.R.attr.windowSplashScreenIconBackgroundColor,
        ).forEach { attribute ->
            val value = TypedValue()
            assertTrue("Launch theme must define $attribute", theme.resolveAttribute(attribute, value, true))
            assertEquals(Color.TRANSPARENT, value.data)
        }
    }

    @Test
    fun launcherLayersKeepArtworkInsideAdaptiveSafeZone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layers =
            listOf(context.applicationInfo.icon, R.mipmap.ic_launcher_round).flatMap { resource ->
                val icon = context.getDrawable(resource) as AdaptiveIconDrawable
                listOf(icon.foreground, requireNotNull(icon.monochrome))
            }
        layers.forEach { layer ->
            val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
            layer.setBounds(0, 0, 108, 108)
            layer.draw(Canvas(bitmap))
            var visiblePixels = 0
            for (y in 0 until 108) {
                for (x in 0 until 108) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 32) {
                        visiblePixels++
                        val distanceSquared = (x - 53.5) * (x - 53.5) + (y - 53.5) * (y - 53.5)
                        assertTrue("Artwork clips outside the 66dp safe circle at $x,$y", distanceSquared <= 33 * 33)
                    }
                }
            }
            assertTrue("Launcher layer must contain visible artwork", visiblePixels > 500)
            bitmap.recycle()
        }
    }

    @Test
    fun renderLauncherReviewAtSmallSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.rgb(32, 45, 62)) }
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f
            }
        listOf("Circle", "Rounded square", "Themed light", "Themed dark").forEachIndexed { column, label ->
            canvas.drawText(label, column * 160f + 12f, 24f, text)
            listOf(48, 64, 96).forEachIndexed { row, size ->
                val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
                val left = column * 160f + (160 - size) / 2f
                val top = 50f + row * 112f
                canvas.save()
                canvas.translate(left, top)
                val mask =
                    Path().apply {
                        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
                        if (column == 1) {
                            addRoundRect(bounds, size / 4f, size / 4f, Path.Direction.CW)
                        } else {
                            addOval(bounds, Path.Direction.CW)
                        }
                    }
                canvas.clipPath(mask)
                val background = if (column == 2) Color.rgb(211, 227, 253) else MobiMonColors.background.toArgb()
                canvas.drawColor(background)
                val foreground = if (column < 2) icon.foreground else requireNotNull(icon.monochrome).mutate()
                if (column >= 2) foreground.setTint(if (column == 2) Color.rgb(9, 21, 37) else Color.rgb(135, 218, 245))
                // AdaptiveIconDrawable expands its layers by 1.5 before applying the launcher mask.
                val extra = size / 4
                foreground.setBounds(-extra, -extra, size + extra, size + extra)
                foreground.draw(canvas)
                canvas.restore()
                canvas.drawText("${size}px", column * 160f + 12f, top + size + 17f, text)
            }
        }
        val directory = File("build/reports/branding").apply { mkdirs() }
        File(directory, "launcher-preview.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
