package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionBackgroundTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun everyHourSelectsItsBackgroundAtPeriodBoundaries() {
        val expected =
            listOf(
                "midnight",
                "midnight",
                "midnight",
                "midnight",
                "midnight",
                "sunrise",
                "sunrise",
                "morning",
                "morning",
                "morning",
                "morning",
                "morning",
                "day",
                "day",
                "day",
                "day",
                "afternoon",
                "afternoon",
                "sunset",
                "sunset",
                "night",
                "night",
                "night",
                "night",
            )
        expected.forEachIndexed { hour, period ->
            assertEquals("Hour $hour", "pet_background_lake_park_$period", resourceName(hour.toString()))
            assertEquals(
                "Cyberpunk hour $hour",
                "pet_background_cyberpunk_city_$period",
                resources.getResourceEntryName(companionBackgroundRes(hour.toString(), "background:cyberpunk_city")),
            )
        }
    }

    @Test
    fun labelsAcceptBothLanguagesAndIgnoreCaseAndWhitespace() {
        mapOf(
            " Sunrise " to "sunrise",
            "일출" to "sunrise",
            "새벽" to "sunrise",
            " Morning " to "morning",
            "아침" to "morning",
            "DAY" to "day",
            "낮" to "day",
            "Afternoon" to "afternoon",
            "늦은 오후" to "afternoon",
            "오후" to "afternoon",
            "Sunset" to "sunset",
            "노을" to "sunset",
            "저녁" to "sunset",
            "NIGHT" to "night",
            "밤" to "night",
            " Midnight " to "midnight",
            "한밤중" to "midnight",
            "자정" to "midnight",
            "01:00" to "midnight",
            "06:00" to "sunrise",
            "09:00" to "morning",
            "14:00" to "day",
            "16:00" to "afternoon",
            "18:00" to "sunset",
            "20:00" to "night",
        ).forEach { (input, period) ->
            assertEquals(input, "pet_background_lake_park_$period", resourceName(input))
            assertEquals(
                input,
                "pet_background_cyberpunk_city_$period",
                resources.getResourceEntryName(companionBackgroundRes(input, "background:cyberpunk_city")),
            )
        }
    }

    @Test
    fun missingOrInvalidTimeKeepsDeterministicNightFallback() {
        listOf(null, "", " ", "unknown", "-1", "24", "99").forEach {
            assertEquals("pet_background_lake_park_night", resourceName(it))
            assertEquals(
                R.drawable.pet_background_cyberpunk_city_night,
                companionBackgroundRes(it, "background:cyberpunk_city"),
            )
        }
    }

    @Test
    fun cyberpunkFramesDecodeAtTheirApprovedCanvasWithoutTransparency() {
        BackgroundPeriod.entries.forEach { period ->
            val bitmap =
                BitmapFactory.decodeResource(
                    resources,
                    companionBackgroundRes(period.key, "background:cyberpunk_city"),
                )
            assertEquals(1672, bitmap.width)
            assertEquals(941, bitmap.height)
            assertTrue(!bitmap.hasAlpha())
            assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
            bitmap.recycle()
        }
    }

    @Test
    fun midnightKeepsNightSpeechBubbleContrast() {
        assertEquals(getSpeechBubbleColors("Night"), getSpeechBubbleColors("Midnight"))
    }

    @Test
    fun allPeriodsDecodeWithTheMasterCanvasDimensions() {
        listOf("Sunrise", "Morning", "Day", "Afternoon", "Sunset", "Night", "Midnight").forEach { period ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, companionBackgroundRes(period), options)
            assertEquals("$period width", 2560, options.outWidth)
            assertEquals("$period height", 1440, options.outHeight)
        }
    }

    @Test
    fun midnightDarkensCityAndReflectionsWithoutChangingTheRoad() {
        val night = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_night)
        val midnight = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_midnight)
        try {
            assertTrue(channelSum(midnight, 80, 700, 790, 855) < channelSum(night, 80, 700, 790, 855) * 0.5)
            assertTrue(channelSum(midnight, 200, 650, 855, 925) < channelSum(night, 200, 650, 855, 925) * 0.75)
            for (y in 950..1100 step 40) {
                for (x in 1200..2200 step 40) {
                    assertEquals("Road pixel ($x, $y)", night.getPixel(x, y), midnight.getPixel(x, y))
                }
            }
        } finally {
            night.recycle()
            midnight.recycle()
        }
    }

    @Test
    fun sunriseSunAndMidnightMoonMatchTheApprovedDiskSizes() {
        val morning = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_morning)
        val sunrise = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_sunrise)
        val night = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_night)
        val midnight = BitmapFactory.decodeResource(resources, R.drawable.pet_background_lake_park_midnight)
        try {
            val morningSun = brightSpan(morning, 490, 685, 780) { r, g, b -> r > 245 && g > 245 && b > 225 }
            val sunriseSun = brightSpan(sunrise, 703, 685, 780) { r, g, b -> r > 245 && g > 245 && b > 225 }
            val nightMoon = brightSpan(night, 412, 2040, 2200) { r, g, b -> r > 200 && g > 175 && b > 150 }
            val midnightMoon = brightSpan(midnight, 262, 885, 1040) { r, g, b -> r > 200 && g > 175 && b > 150 }
            assertTrue("Morning sun exists", morningSun in 55..70)
            assertTrue("Night moon exists", nightMoon in 95..105)
            assertTrue("Sun disk width", kotlin.math.abs(morningSun - sunriseSun) <= 3)
            assertTrue("Moon disk width", kotlin.math.abs(nightMoon - midnightMoon) <= 3)
        } finally {
            morning.recycle()
            sunrise.recycle()
            night.recycle()
            midnight.recycle()
        }
    }

    private fun brightSpan(
        bitmap: Bitmap,
        y: Int,
        left: Int,
        right: Int,
        isBright: (Int, Int, Int) -> Boolean,
    ): Int {
        var first = right
        var last = left
        for (x in left..right) {
            val pixel = bitmap.getPixel(x, y)
            if (isBright(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                first = minOf(first, x)
                last = maxOf(last, x)
            }
        }
        return last - first + 1
    }

    private fun channelSum(
        bitmap: Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): Long {
        var sum = 0L
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                sum += Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
            }
        }
        return sum
    }

    private fun resourceName(value: String?): String = resources.getResourceEntryName(companionBackgroundRes(value))
}
