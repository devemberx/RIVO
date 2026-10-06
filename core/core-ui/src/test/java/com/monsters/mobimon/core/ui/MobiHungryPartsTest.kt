package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiHungryPartsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appearances = listOf("normal", "headphones", "goggles")

    @Test
    fun missingPartsReturnToFallbackWithoutPoisoningTheCache() {
        MobiHungryArtworkCache.clear()
        val unavailable =
            object : ContextWrapper(context) {
                override fun getAssets(): AssetManager = throw FileNotFoundException("Unavailable packaged part")
            }
        assertNull(MobiHungryArtworkCache.getOrLoad(unavailable, null))
        assertNull(MobiHungryArtworkCache.peek(null))
        assertTrue(MobiHungryArtworkCache.getOrLoad(context, null) != null)
    }

    @Test
    fun packagedPartsExactlyMatchReviewedSources() {
        val expected =
            resource("assets.sha256").bufferedReader().useLines { lines ->
                lines.associate { line ->
                    val (hash, path) = line.split("  ", limit = 2)
                    path to hash
                }
            }
        val actualPaths =
            appearances
                .flatMap { name ->
                    val directory = "characters/mobi/$name/hungry"
                    context.assets.list(directory)!!.map { "$directory/$it" }
                }.toSet()
        assertEquals(expected.keys, actualPaths)
        for ((path, hash) in expected) {
            val actual = context.assets.open(path).use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
            assertEquals(path, hash, actual.joinToString("") { "%02x".format(it) })
        }
    }

    @Test
    fun webpPartsMatchReviewedNativeLoopAtEvery25Milliseconds() {
        // Native Skia rasterization differs between macOS ARM64 and Linux x86_64. Compare
        // area-averaged premultiplied ARGB, while the separate asset test pins exact source bytes.
        // All 723 reviewed samples still cover the six-second timeline and loop seam.
        for (name in appearances) {
            val art = artwork(name)
            val renderer = MobiHungryRenderer(art)
            var worstChannel = 0
            var worstMean = 0.0
            DataInputStream(GZIPInputStream(resource("$name.argb.gz"))).use { reference ->
                for (ms in 0L..6000L step 25) {
                    val expected = ByteArray(64 * 64 * 4)
                    reference.readFully(expected)
                    val difference = difference(expected, render(renderer, ms))
                    worstChannel = maxOf(worstChannel, difference.maxChannel)
                    worstMean = maxOf(worstMean, difference.meanChannel)
                    assertTrue("$name at ${ms}ms: $difference", difference.matches)
                }
                assertEquals("Unexpected reference frames for $name", -1, reference.read())
            }
            println("$name native reference: maxChannel=$worstChannel, maxMean=$worstMean")
            assertTrue(
                art.base.allocationByteCount +
                    art.thought.allocationByteCount + art.rumble.allocationByteCount <
                    1_900_000,
            )
        }
    }

    @Test
    fun referenceComparisonRejectsMissingBubbleWrongEquipmentAndWrongTiming() {
        val normal = MobiHungryRenderer(artwork("normal"))
        val headphones = MobiHungryRenderer(artwork("headphones"))
        val bubble = referenceAt("normal", 2000)
        assertFalse("Missing carrot bubble", difference(bubble, render(normal, 0)).matches)
        assertFalse("Wrong equipment", difference(bubble, render(headphones, 2000)).matches)
        assertFalse(
            "Shifted head dip/expression",
            difference(referenceAt("normal", 2800), render(normal, 3050)).matches,
        )
    }

    private fun artwork(name: String): MobiHungryArtwork {
        val accessory = if (name == "normal") null else "accessory:mobi_$name"
        return requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
    }

    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/mobi-hungry/$name"))

    private fun referenceAt(
        name: String,
        ms: Int,
    ): ByteArray =
        DataInputStream(GZIPInputStream(resource("$name.argb.gz"))).use { input ->
            ByteArray(64 * 64 * 4).also { frame ->
                repeat(ms / 25 + 1) { input.readFully(frame) }
            }
        }

    private fun render(
        renderer: MobiHungryRenderer,
        ms: Long,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            renderer.draw(Canvas(bitmap), ms * 1_000_000)
            val pixels = IntArray(256 * 256)
            bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
            return averagedPremultipliedPixels(pixels)
        } finally {
            bitmap.recycle()
        }
    }

    private data class Difference(
        val maxChannel: Int,
        val meanChannel: Double,
    ) {
        // Four levels in any 4x4 cell and at most 0.35 over the frame allow native rounding,
        // but reject missing artwork, incorrect equipment and shifted animation poses.
        val matches: Boolean get() = maxChannel <= 4 && meanChannel <= .35
    }

    private fun difference(
        expected: ByteArray,
        actual: ByteArray,
    ): Difference {
        require(expected.size == actual.size)
        var maximum = 0
        var total = 0L
        for (index in expected.indices) {
            val delta = abs((expected[index].toInt() and 255) - (actual[index].toInt() and 255))
            maximum = maxOf(maximum, delta)
            total += delta
        }
        return Difference(maximum, total.toDouble() / expected.size)
    }

    private fun averagedPremultipliedPixels(pixels: IntArray): ByteArray =
        ByteArray(64 * 64 * 4).also { result ->
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    val sums = IntArray(4)
                    for (dy in 0 until 4) {
                        for (dx in 0 until 4) {
                            val pixel = pixels[(y * 4 + dy) * 256 + x * 4 + dx]
                            val alpha = (pixel ushr 24) and 255
                            sums[0] += alpha
                            for (channel in 1..3) {
                                sums[channel] += (((pixel ushr (24 - channel * 8)) and 255) * alpha + 127) / 255
                            }
                        }
                    }
                    for (channel in 0..3) {
                        result[(y * 64 + x) * 4 + channel] = ((sums[channel] + 8) / 16).toByte()
                    }
                }
            }
        }
}
