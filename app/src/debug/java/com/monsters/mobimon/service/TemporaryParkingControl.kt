package com.monsters.mobimon.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.debug.DebugStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TEMPORARY: delete both variant files and the service hooks after overlay QA. */
class TemporaryParkingControl
    @Inject
    constructor(
        private val debugStore: DebugStore,
        private val settings: SettingsRepository,
    ) {
        private var button: Button? = null
        private var manager: WindowManager? = null
        private var observation: Job? = null

        fun attach(
            context: Context,
            scope: CoroutineScope,
        ) {
            val wm = context.getSystemService(WindowManager::class.java)
            val control =
                Button(context).apply {
                    text = "TEST P / D"
                    setOnClickListener {
                        scope.launch {
                            settings.setDebugModeEnabled(true)
                            debugStore.updateState { state ->
                                val park = state.gear != "P"
                                state.copy(
                                    raw =
                                        state.raw.copy(
                                            selectedGear = if (park) 126 else 127,
                                            vehicleIsMoving = !park,
                                            vehicleSpeedKmh = if (park) 0f else 10f,
                                        ),
                                    overrides = state.overrides.copy(gear = null, isMoving = null, speed = null),
                                )
                            }
                        }
                    }
                }
            val params =
                WindowManager
                    .LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = Gravity.TOP or Gravity.END
                        y = (48 * context.resources.displayMetrics.density).toInt()
                    }
            try {
                wm.addView(control, params)
                manager = wm
                button = control
                observation =
                    scope.launch {
                        debugStore.state.collect {
                            control.text =
                                "TEST ${it.gear} → ${if (it.gear == "P") "D" else "P"}"
                        }
                    }
            } catch (_: Exception) {
                // A missing QA control must not crash the companion service.
            }
        }

        fun setVisible(visible: Boolean) {
            button?.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun detach() {
            observation?.cancel()
            button?.let { runCatching { manager?.removeView(it) } }
            button = null
            manager = null
        }
    }
