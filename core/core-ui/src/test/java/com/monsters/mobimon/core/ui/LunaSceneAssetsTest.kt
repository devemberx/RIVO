package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LunaSceneAssetsTest {
    @Test
    fun repeatedHoldsKeepTheirTimelineCells() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (appearance in LunaAppearance.entries) {
            val sheet = requireNotNull(LunaDisappearTimeline.load(context, appearance, 512)).asAndroidBitmap()
            val first = IntArray(512 * 512)
            val repeat = IntArray(first.size)
            for ((a, b) in listOf(0 to 1, 7 to 8, 17 to 18, 22 to 23)) {
                sheet.getPixels(first, 0, 512, a % 6 * 512, a / 6 * 512, 512, 512)
                sheet.getPixels(repeat, 0, 512, b % 6 * 512, b / 6 * 512, 512, 512)
                org.junit.Assert.assertArrayEquals("$appearance hold $a/$b", first, repeat)
            }
            sheet.recycle()
        }
    }
}
