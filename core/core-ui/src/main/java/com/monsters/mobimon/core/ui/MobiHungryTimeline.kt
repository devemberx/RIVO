package com.monsters.mobimon.core.ui

/** Keep the carrot readable, then two restrained hunger shakes. */
internal object MobiHungryTimeline {
    const val CYCLE_MS = 6_000L
    const val STILL_MS = 3_545L

    data class Pose(
        val dip: Float,
        val thought: Float,
        val expression: Float,
        val rumble: Float,
    )

    data class Sway(
        val x: Float,
        val y: Float,
        val degrees: Float,
    )

    fun poseAt(elapsedNanos: Long): Pose {
        val t = milliseconds(elapsedNanos)
        return Pose(
            2.2f * smooth(2640f, 3020f, t) * (1f - smooth(4200f, 5700f, t)),
            smooth(850f, 1250f, t) * (1f - smooth(2640f, 2940f, t)),
            smooth(2640f, 3020f, t) * (1f - smooth(4590f, 4990f, t)),
            smooth(3010f, 3280f, t) * (1f - smooth(4530f, 4790f, t)),
        )
    }

    private val keys = floatArrayOf(0f, 180f, 390f, 595f, 795f, 990f)
    private val curves =
        arrayOf(
            arrayOf(
                floatArrayOf(0f, .125f, 2.529f, 3.171f, 1.862f, 0f),
                floatArrayOf(0f, .016f, -.015f, .732f, .086f, 0f),
                floatArrayOf(0f, -.353f, 6.983f, 10.555f, 6.967f, 0f),
            ),
            arrayOf(
                floatArrayOf(0f, -.230f, -1.924f, -3.756f, -1.748f, 0f),
                floatArrayOf(0f, .077f, .630f, 1.601f, 1.613f, 0f),
                floatArrayOf(0f, -1.397f, -3.006f, -10.016f, -3.473f, 0f),
            ),
        )

    fun swayAt(
        elapsedNanos: Long,
        side: Int,
    ): Sway {
        val t = milliseconds(elapsedNanos)
        val position = if (t < 3010f || t >= 4790f) 0f else (t - 3010f) % 890f * 990f / 890f
        return Sway(
            curve(curves[side][0], position),
            curve(curves[side][1], position),
            curve(curves[side][2], position),
        )
    }

    private fun milliseconds(nanos: Long): Float = (nanos.coerceAtLeast(0) % (CYCLE_MS * 1_000_000L)) / 1_000_000f

    internal fun smooth(
        start: Float,
        end: Float,
        time: Float,
    ): Float {
        val x = ((time - start) / (end - start)).coerceIn(0f, 1f)
        return (x * x * x * (x * (x * 6f - 15f) + 10f)).coerceIn(0f, 1f)
    }

    private fun curve(
        values: FloatArray,
        time: Float,
    ): Float {
        if (time <= 0f) return values.first()
        if (time >= keys.last()) return values.last()

        fun slope(i: Int): Float {
            if (i == 0 || i == keys.lastIndex) return 0f
            val h0 = keys[i] - keys[i - 1]
            val h1 = keys[i + 1] - keys[i]
            val d0 = (values[i] - values[i - 1]) / h0
            val d1 = (values[i + 1] - values[i]) / h1
            if (d0 * d1 <= 0f) return 0f
            val w0 = 2 * h1 + h0
            val w1 = h1 + 2 * h0
            return (w0 + w1) / (w0 / d0 + w1 / d1)
        }
        var i = 0
        while (time > keys[i + 1]) i++
        val h = keys[i + 1] - keys[i]
        val u = (time - keys[i]) / h
        val u2 = u * u
        val u3 = u2 * u
        return (2 * u3 - 3 * u2 + 1) * values[i] + (u3 - 2 * u2 + u) * h * slope(i) +
            (-2 * u3 + 3 * u2) * values[i + 1] + (u3 - u2) * h * slope(i + 1)
    }
}
