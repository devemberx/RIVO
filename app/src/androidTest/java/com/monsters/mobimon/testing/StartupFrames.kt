package com.monsters.mobimon.testing

import androidx.test.core.app.ActivityScenario
import com.monsters.mobimon.MainActivity

/** Device tests wait for the real window draw through the journey's Compose-root wait. */
@Suppress("UNUSED_PARAMETER")
internal fun drawStartupFrame(scenario: ActivityScenario<MainActivity>) = Unit
