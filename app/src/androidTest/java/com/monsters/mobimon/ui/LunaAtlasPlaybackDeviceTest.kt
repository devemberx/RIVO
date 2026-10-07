package com.monsters.mobimon.ui

import android.os.Debug
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.PetAvatar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Controlled-clock hardware playback; process PSS includes instrumentation overhead. */
@RunWith(AndroidJUnit4::class)
class LunaAtlasPlaybackDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recordsEquippedOverlayPlaybackAndProcessMemory() {
        val args = InstrumentationRegistry.getArguments()
        val label = args.getString("probe_label") ?: "candidate"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "test-screenshots/luna-atlas/$label").apply { mkdirs() }
        var appearance by mutableStateOf("normal")
        var phase by mutableStateOf("empty")
        var entered = 0
        var departed = 0
        val sampling = AtomicBoolean(true)
        val samples = mutableListOf<String>()
        val samplePhase = AtomicReference("empty")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MobiMonTheme {
                Column(Modifier.background(Color(0xff24354b)).padding(24.dp)) {
                    Text("$label / $appearance / $phase / 124dp / controlled 16ms clock")
                    Box(
                        Modifier.size(258.dp, 176.dp).padding(8.dp).testTag("atlas-stage"),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (phase != "empty") {
                            PetAvatar(
                                Modifier.size(124.dp),
                                friendId = "friend:luna",
                                accessoryId = if (appearance == "normal") null else "accessory:luna_$appearance",
                                isMoving = phase == "run",
                                isAppearing = phase == "appear",
                                isDisappearing = phase == "disappear",
                                onAppeared = { entered++ },
                                onDisappeared = { departed++ },
                            )
                        }
                    }
                }
            }
        }
        val start = SystemClock.elapsedRealtime()
        val monitor =
            Thread {
                while (sampling.get()) {
                    val memory = Debug.MemoryInfo()
                    Debug.getMemoryInfo(memory)
                    val runtime = Runtime.getRuntime()
                    synchronized(samples) {
                        samples +=
                            "${SystemClock.elapsedRealtime() - start},${samplePhase.get()},${memory.totalPss}," +
                            "${memory.nativePss},${memory.dalvikPss},${Debug.getNativeHeapAllocatedSize()}," +
                            "${runtime.totalMemory() - runtime.freeMemory()}"
                    }
                    SystemClock.sleep(250)
                }
            }.apply { start() }

        fun play(milliseconds: Long) {
            repeat(((milliseconds + 15) / 16).toInt()) {
                compose.mainClock.advanceTimeByFrame()
                SystemClock.sleep(16)
            }
        }

        fun change(next: String) {
            samplePhase.set("$appearance-$next")
            compose.runOnUiThread {
                phase = next
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
        }

        try {
            play(1000)
            for (name in listOf("normal", "cap", "sunglasses")) {
                compose.runOnUiThread {
                    appearance = name
                    Snapshot.sendApplyNotifications()
                }
                change("appear")
                val beforeEntry = entered
                play(2600)
                assertEquals("$name entrance completes once", beforeEntry + 1, entered)
                change("idle")
                play(1000)
                change("run")
                play(4000)
                change("idle")
                play(2000)
                change("disappear")
                val beforeExit = departed
                play(3400)
                assertEquals("$name departure completes once", beforeExit + 1, departed)
                change("empty")
                play(1000)
            }
            compose.runOnUiThread { Runtime.getRuntime().gc() }
            samplePhase.set("empty-after-gc")
            play(1500)
        } finally {
            sampling.set(false)
            monitor.join(2000)
            File(output, "memory.csv").writeText(
                "elapsed_ms,phase,total_pss_kib,native_pss_kib,dalvik_pss_kib,native_heap_bytes,java_heap_bytes\n" +
                    synchronized(samples) { samples.joinToString("\n") } + "\n",
            )
            File(output, "scope.txt").writeText(
                "124dp avatar in 258x176dp overlay-equivalent stage. All equipped clips; three run loops each. " +
                    "Test-controlled 16ms frame clock, wall-clock sleeps; " +
                    "not an OEM overlay or frame-time benchmark. " +
                    "${context.resources.displayMetrics.densityDpi}dpi; process PSS includes the test runner.\n",
            )
        }
    }
}
