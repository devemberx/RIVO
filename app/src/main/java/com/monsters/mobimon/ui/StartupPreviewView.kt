package com.monsters.mobimon.ui

import android.content.Context
import android.graphics.Canvas
import android.widget.ImageView
import com.monsters.mobimon.R

/** A real first frame, before feature injection or Compose initialization. */
internal class StartupPreviewView(
    context: Context,
    onDrawn: () -> Unit,
) : ImageView(context) {
    private val openContent = Runnable(onDrawn)
    private var scheduled = false

    init {
        scaleType = ScaleType.CENTER_CROP
        setImageResource(R.drawable.pet_background_startup_common)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        // Match the opening Compose sky, including its initial crop.
        canvas.scale(1.055f, 1.055f, width / 2f, height / 2f)
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
        if (!scheduled) {
            scheduled = true
            // Enqueue initialization after this traversal has submitted the image frame.
            post(openContent)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(openContent)
        super.onDetachedFromWindow()
    }
}
