package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native decoder allocations and all cell crops; optional original fixtures support pixel review. */
@RunWith(AndroidJUnit4::class)
class MobiAtlasDeviceTest {
    @Test
    fun nativeAtlasDecodingPreservesEveryCellWithinTheOverlayBudget() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val directory = File(context.filesDir, "mobi-atlas").apply { mkdirs() }
        val originalDirectory = File(context.filesDir, "mobi-atlas-baseline")
        val measurements = mutableListOf("appearance,action,target_px,width,height,allocation_bytes,decode_nanos")
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            val appearance = mobiAppearanceName(accessory)
            for (action in MobiAtlasAction.entries) {
                for (target in listOf(169, 505)) {
                    val start = System.nanoTime()
                    val sheet =
                        requireNotNull(MobiAnimationAtlas.load(context, action, accessory, target)).asAndroidBitmap()
                    val decodeNanos = System.nanoTime() - start
                    val cell = if (target == 169) 256 else 512
                    assertEquals(cell * 6, sheet.width)
                    assertEquals(cell * 4, sheet.height)
                    assertEquals(if (target == 169) 6_291_456 else 25_165_824, sheet.allocationByteCount)
                    measurements +=
                        "$appearance,${action.directory},$target,${sheet.width},${sheet.height}," +
                        "${sheet.allocationByteCount},$decodeNanos"
                    if (target == 169) {
                        for (index in 0 until 24) {
                            saveFrame(
                                sheet,
                                index,
                                169,
                                File(directory, "$appearance-${action.directory}-$index-after.png"),
                            )
                        }
                    }
                    sheet.recycle()
                }
                val originalFile = File(originalDirectory, "${appearance}_${action.directory}.png")
                if (originalFile.isFile) {
                    val options =
                        BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inScaled = false
                        }
                    val original = requireNotNull(BitmapFactory.decodeFile(originalFile.path, options))
                    assertEquals(37_740_384, original.allocationByteCount)
                    for (index in 0 until 24) {
                        saveFrame(
                            original,
                            index,
                            169,
                            File(directory, "$appearance-${action.directory}-$index-before.png"),
                        )
                    }
                    original.recycle()
                }
            }
        }
        File(directory, "allocations.csv").writeText(measurements.joinToString("\n") + "\n")
        File(directory, "scope.txt").writeText(
            "Android BitmapFactory + Canvas all-cell export at 169px. Original fixtures available: " +
                "${originalDirectory.isDirectory}. Not Compose GPU frame pacing or continuous-playback acceptance.\n",
        )
    }

    private fun saveFrame(
        sheet: Bitmap,
        index: Int,
        side: Int,
        output: File,
    ) {
        val cell = sheet.width / 6
        val source = Rect(index % 6 * cell, index / 6 * cell, (index % 6 + 1) * cell, (index / 6 + 1) * cell)
        val frame = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(frame).drawBitmap(sheet, source, Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG))
        output.outputStream().use { assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        frame.recycle()
    }
}
