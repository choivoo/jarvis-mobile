package com.choivoo.jarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File
import kotlin.concurrent.thread

/**
 * JARVIS standalone offline neural voice.
 * The sherpa-onnx runtime and Supertonic 3 model are packaged inside the APK.
 */
class StandaloneNeuralTts(private val context: Context) {
    companion object {
        private const val ASSET_DIR = "jarvis_tts/supertonic-3"
        private const val MODEL_VERSION = "supertonic-3-int8-2026-05-11"
        private const val DEFAULT_SID = 6
    }

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var released = false
    private var audioTrack: AudioTrack? = null

    fun isAvailable(): Boolean = runCatching {
        context.assets.open("$ASSET_DIR/tts.json").close()
        true
    }.getOrDefault(false)

    fun speak(
        text: String,
        speed: Float = 0.94f,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (text.isBlank()) {
            onDone()
            return
        }
        if (!isAvailable()) {
            onError("Built-in Neural Local voice assets are missing.")
            return
        }
        thread(name = "jarvis-neural-tts") {
            try {
                val tts = getOrCreateEngine()
                if (released) return@thread
                val speakerCount = runCatching { tts.numSpeakers() }.getOrDefault(0)
                val sid = if (speakerCount > 0) DEFAULT_SID.coerceIn(0, speakerCount - 1) else 0
                val audio = tts.generate(
                    text = text,
                    sid = sid,
                    speed = speed.coerceIn(0.80f, 1.10f)
                )
                if (audio.samples.isEmpty()) throw IllegalStateException("Neural synthesis returned no samples")
                onStart()
                playBlocking(audio.samples, audio.sampleRate)
                onDone()
            } catch (t: Throwable) {
                onError(t.message ?: "Neural Local synthesis failed")
            }
        }
    }

    @Synchronized
    private fun getOrCreateEngine(): OfflineTts {
        engine?.let { return it }
        if (released) error("Neural engine was released")
        val dir = ensureModelOnDisk()
        fun req(name: String): String {
            val f = File(dir, name)
            check(f.isFile) { "Supertonic model is missing $name" }
            return f.absolutePath
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = req("duration_predictor.int8.onnx"),
                    textEncoder = req("text_encoder.int8.onnx"),
                    vectorEstimator = req("vector_estimator.int8.onnx"),
                    vocoder = req("vocoder.int8.onnx"),
                    ttsJson = req("tts.json"),
                    unicodeIndexer = req("unicode_indexer.bin"),
                    voiceStyle = req("voice.bin"),
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 2,
        )
        return OfflineTts(assetManager = null, config = config).also { engine = it }
    }

    private fun ensureModelOnDisk(): File {
        val root = File(context.filesDir, "jarvis_neural/$MODEL_VERSION")
        val ready = File(root, ".ready")
        if (ready.exists() && File(root, "vocoder.int8.onnx").exists()) return root
        if (root.exists()) root.deleteRecursively()
        root.mkdirs()
        val required = listOf(
            "duration_predictor.int8.onnx",
            "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx",
            "vocoder.int8.onnx",
            "tts.json",
            "unicode_indexer.bin",
            "voice.bin"
        )
        required.forEach { name ->
            context.assets.open("$ASSET_DIR/$name").use { input ->
                File(root, name).outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            }
        }
        ready.writeText("ok")
        return root
    }

    private fun playBlocking(samples: FloatArray, sampleRate: Int) {
        stop()
        val shorts = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min)
            .build()
        audioTrack = track
        track.play()
        var offset = 0
        while (offset < shorts.size && !released && audioTrack === track) {
            val wrote = track.write(shorts, offset, shorts.size - offset, AudioTrack.WRITE_BLOCKING)
            if (wrote <= 0) break
            offset += wrote
        }
        if (audioTrack === track) {
            runCatching { track.stop() }
            track.release()
            audioTrack = null
        }
    }

    @Synchronized
    fun stop() {
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    @Synchronized
    fun release() {
        released = true
        stop()
        engine?.runCatching { release() }
        engine = null
    }
}
