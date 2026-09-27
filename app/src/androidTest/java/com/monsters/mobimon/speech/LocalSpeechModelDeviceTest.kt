package com.monsters.mobimon.speech

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in native model test. Supply public/synthetic 16 kHz mono PCM16 WAV fixtures externally. */
@RunWith(AndroidJUnit4::class)
class LocalSpeechModelDeviceTest {
    @Test
    fun bundledModelsDecodeFixturesAndRemainStableAcrossDrafts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localSpeechModels") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "stt-fixtures")
        val manifest = JSONArray(File(directory, "fixtures.json").readText())
        val results = JSONArray()
        val started = SystemClock.elapsedRealtime()
        val engine = SherpaSpeechEngine(context.assets)
        val initializationMs = SystemClock.elapsedRealtime() - started
        engine.use {
            val transcripts = mutableMapOf<String, String>()
            for (index in 0 until manifest.length()) {
                val fixture = manifest.getJSONObject(index)
                val name = fixture.getString("file")
                val audio = readWave(File(directory, name))
                val before = SystemClock.elapsedRealtime()
                val text = recognize(engine, audio)
                transcripts[name] = text
                val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                results.put(
                    JSONObject()
                        .put("file", name)
                        .put("expected", fixture.getString("expected"))
                        .put("actual", text)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - before)
                        .put("pssKiB", memory.totalPss),
                )
                if (fixture.getString("expected").isBlank()) {
                    assertEquals(name, "", text)
                } else {
                    assertTrue("No speech from $name", text.isNotBlank())
                }
            }
            val repeated = readWave(File(directory, "greeting.wav"))
            repeat(20) { assertEquals(transcripts.getValue("greeting.wav"), recognize(engine, repeated)) }
        }
        // Releasing and loading again exercises native lifecycle, independently of the reusable path.
        SherpaSpeechEngine(context.assets).use { reloaded ->
            assertTrue(recognize(reloaded, readWave(File(directory, "greeting.wav"))).isNotBlank())
        }
        File(context.filesDir, "local-stt-results.json").writeText(
            JSONObject()
                .put("initializationMs", initializationMs)
                .put("stableRepeats", 20)
                .put("results", results)
                .toString(2),
        )
    }

    private fun recognize(
        engine: LocalSpeechEngine,
        audio: FloatArray,
    ): String =
        SpeechProcessor(engine).use { processor ->
            var offset = 0
            while (offset < audio.size) {
                val end = minOf(offset + 1024, audio.size)
                processor.accept(audio.copyOfRange(offset, end))
                offset = end
            }
            processor.finish()
        }

    private fun readWave(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")
        var position = 12
        var formatValid = false
        while (position + 8 <= bytes.size) {
            val tag = String(bytes, position, 4, Charsets.US_ASCII)
            val size = buffer.getInt(position + 4)
            require(size >= 0 && position + 8L + size <= bytes.size)
            val start = position + 8
            if (tag == "fmt ") {
                require(size >= 16)
                formatValid = buffer.getShort(start).toInt() == 1 &&
                    buffer.getShort(start + 2).toInt() == 1 &&
                    buffer.getInt(start + 4) == SpeechAudio.SAMPLE_RATE &&
                    buffer.getShort(start + 14).toInt() == 16
            }
            if (tag == "data") {
                require(formatValid && size % 2 == 0)
                return FloatArray(size / 2) { buffer.getShort(start + it * 2) / 32768f }
            }
            position = start + size + size % 2
        }
        error("Missing PCM data")
    }
}
