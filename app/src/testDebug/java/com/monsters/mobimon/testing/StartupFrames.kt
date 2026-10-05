package com.monsters.mobimon.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import com.monsters.mobimon.MainActivity
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Robolectric does not submit the native window's first draw before Compose attaches. */
internal fun drawStartupFrame(scenario: ActivityScenario<MainActivity>) {
    scenario.onActivity { activity ->
        val root = activity.window.decorView
        val bitmap =
            Bitmap.createBitmap(
                root.width.coerceAtLeast(1),
                root.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        root.draw(Canvas(bitmap))
        bitmap.recycle()
    }
    // Exercise the bounded no-system-splash-callback path and its next frame.
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
}
