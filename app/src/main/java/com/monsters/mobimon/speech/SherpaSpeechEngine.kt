package com.monsters.mobimon.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/** Native objects are created, used and released only on the inference executor. */
internal class SherpaSpeechEngine(
    private val assets: AssetManager,
) : LocalSpeechEngine {
    private val recognizer =
        OfflineRecognizer(
            assetManager = assets,
            config =
                OfflineRecognizerConfig(
                    modelConfig =
                        OfflineModelConfig(
                            senseVoice =
                                OfflineSenseVoiceModelConfig(
                                    model = "stt/model.int8.onnx",
                                    language = "ko",
                                    useInverseTextNormalization = true,
                                ),
                            tokens = "stt/tokens.txt",
                            numThreads = 2,
                            provider = "cpu",
                        ),
                ),
        )

    override fun detector(): SpeechDetector {
        val vad =
            Vad(
                assetManager = assets,
                config =
                    VadModelConfig(
                        sileroVadModelConfig =
                            SileroVadModelConfig(
                                model = "stt/silero_vad.onnx",
                                threshold = 0.5f,
                                minSilenceDuration = 0.5f,
                                minSpeechDuration = 0.25f,
                                windowSize = SpeechAudio.VAD_WINDOW,
                                maxSpeechDuration = 60f,
                            ),
                        sampleRate = SpeechAudio.SAMPLE_RATE,
                        numThreads = 1,
                        provider = "cpu",
                    ),
            )
        return object : SpeechDetector {
            override fun accept(samples: FloatArray) = vad.acceptWaveform(samples)

            override fun speechDetected() = vad.isSpeechDetected()

            override fun poll(): SpeechRange? {
                if (vad.empty()) return null
                val segment = vad.front()
                vad.pop()
                return SpeechRange(segment.start, segment.start + segment.samples.size)
            }

            override fun flush() = vad.flush()

            override fun close() = vad.release()
        }
    }

    override fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SpeechAudio.SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}
