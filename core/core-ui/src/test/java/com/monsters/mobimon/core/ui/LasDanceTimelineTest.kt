package com.monsters.mobimon.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LasDanceTimelineTest {
    @Test
    fun playsEveryFrameInOrderBeforeRepeating() {
        repeat(2) { cycle ->
            repeat(24) { frame ->
                val startMs = (cycle * 24 + frame) * 85L
                assertEquals("Frame at $startMs ms", frame, LasDanceTimeline.frame(startMs))
                assertEquals(frame, LasDanceTimeline.frame(startMs + 84L))
            }
        }
        assertEquals(0, LasDanceTimeline.frame(4080L))
    }

    @Test
    fun negativeElapsedTimeKeepsFirstFrame() {
        assertEquals(0, LasDanceTimeline.frame(-1L))
        assertEquals(0, LasDanceTimeline.frame(Long.MIN_VALUE))
    }
}
