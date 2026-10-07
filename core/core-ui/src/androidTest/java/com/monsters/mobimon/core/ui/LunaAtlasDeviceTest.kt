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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

/** Native allocation/crop checks; optional original fixtures retain before/after decoder evidence. */
@RunWith(AndroidJUnit4::class)
class LunaAtlasDeviceTest {
    @Test
    fun allEquippedClipsDecodeAtThePhysicalSceneSize() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val directory = File(context.filesDir, "luna-atlas").apply { mkdirs() }
        val baseline = File(context.filesDir, "luna-atlas-baseline")
        val measurements = mutableListOf("appearance,action,target_px,allocation_bytes,decode_nanos,baseline_bytes")
        for (appearance in LunaAppearance.entries) {
            for (action in LunaAtlasAction.entries) {
                val scale =
                    when (action) {
                        LunaAtlasAction.RUN -> 1f
                        LunaAtlasAction.APPEAR -> LunaAppearTimeline.SCENE_SCALE
                        LunaAtlasAction.DISAPPEAR -> LunaDisappearTimeline.SCENE_SCALE
                    }
                val displaySide = (124 * scale).roundToInt()
                val targets = listOf(LunaScene.requiredFrameSidePx(124f, scale), 512)
                for (target in targets) {
                    val start = System.nanoTime()
                    val sheet =
                        requireNotNull(
                            LunaAnimationAtlas.load(context, action, appearance, target),
                        ).asAndroidBitmap()
                    val nanos = System.nanoTime() - start
                    val cell = if (target <= 256) 256 else 512
                    assertEquals(cell * 6, sheet.width)
                    assertEquals(cell * 4, sheet.height)
                    assertEquals(cell * cell * 24 * 4, sheet.allocationByteCount)
                    var originalBytes = 0L
                    if (target == targets.first()) {
                        val list = File(baseline, "${appearance.assetName}-${action.directory}.txt")
                        val paths = if (list.isFile) list.readLines() else emptyList()
                        val decoded = mutableMapOf<String, Bitmap>()
                        for (frame in 0 until 24) {
                            saveFrame(
                                sheet,
                                frame,
                                displaySide,
                                File(directory, "${appearance.assetName}-${action.directory}-$frame-after.png"),
                            )
                            if (paths.isNotEmpty()) {
                                val original =
                                    decoded.getOrPut(paths[frame]) {
                                        val options =
                                            BitmapFactory.Options().apply {
                                                inSampleSize = 2
                                                inScaled = false
                                            }
                                        requireNotNull(
                                            BitmapFactory.decodeFile(File(baseline, paths[frame]).path, options),
                                        )
                                    }
                                saveOriginal(
                                    original,
                                    displaySide,
                                    File(directory, "${appearance.assetName}-${action.directory}-$frame-before.png"),
                                )
                            }
                        }
                        originalBytes = decoded.values.sumOf { it.allocationByteCount.toLong() }
                        decoded.values.forEach { it.recycle() }
                    }
                    measurements +=
                        "${appearance.assetName},${action.directory},$target," +
                        "${sheet.allocationByteCount},$nanos,$originalBytes"
                    sheet.recycle()
                }
            }
        }
        File(directory, "allocations.csv").writeText(measurements.joinToString("\n") + "\n")
    }

    private fun saveFrame(
        sheet: Bitmap,
        index: Int,
        side: Int,
        file: File,
    ) {
        val cell = sheet.width / 6
        val x = index % 6 * cell
        val y = index / 6 * cell
        save(sheet, Rect(x, y, x + cell, y + cell), side, file)
    }

    private fun saveOriginal(
        bitmap: Bitmap,
        side: Int,
        file: File,
    ) {
        save(bitmap, Rect(0, 0, bitmap.width, bitmap.height), side, file)
    }

    private fun save(
        bitmap: Bitmap,
        source: Rect,
        side: Int,
        file: File,
    ) {
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, source, Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG))
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()
    }
}
