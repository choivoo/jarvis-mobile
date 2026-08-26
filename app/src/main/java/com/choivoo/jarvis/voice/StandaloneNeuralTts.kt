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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * JARVIS standalone offline neural voice.
 *
 * Stability rules:
 * - only one sherpa-onnx synthesis runs at a time
 * - long replies are synthesized in short chunks to avoid large temporary arrays
 * - a generation token cancels stale playback/callbacks
 */
class StandaloneNeuralTts(private val context: Context) {
    companion object {
        private const val ASSET_DIR = "jarvis_tts/supertonic-3"
        private const val MODEL_VERSION = "supertonic-3-int8-2026-05-11"
        private const val DEFAULT_SID = 6
        private const val MAX_CHUNK_CHARS = 180
    }

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var released = false
    @Volatile private var audioTrack: AudioTrack? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "jarvis-neural-tts") }
    private val generation = AtomicInteger(0)

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

        val token = generation.incrementAndGet()
        stopPlaybackOnly()
        worker.execute {
            try {
                if (released || token != generation.get()) return@execute
                val tts = getOrCreateEngine()
                if (released || token != generation.get()) return@execute

                val speakerCount = runCatching { tts.numSpeakers() }.getOrDefault(0)
                val sid = if (speakerCount > 0) DEFAULT_SID.coerceIn(0, speakerCount - 1) else 0
                val chunks = chunkText(text)
                var started = false

                for (chunk in chunks) {
                    if (released || token != generation.get()) return@execute
                    val audio = tts.generate(
                        text = chunk,
                        sid = sid,
                        speed = speed.coerceIn(0.82f, 1.08f)
                    )
                    if (audio.samples.isEmpty()) continue
                    if (!started) {
                        started = true
                        onStart()
                    }
                    playBlocking(audio.samples, audio.sampleRate, token)
                }

                if (released || token != generation.get()) return@execute
                if (!started) throw IllegalStateException("Neural synthesis returned no samples")
                onDone()
            } catch (t: Throwable) {
                if (!released && token == generation.get()) {
                    onError(t.message ?: "Neural Local synthesis failed")
                }
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
            maxNumSentences = 1,
        )
        return OfflineTts(assetManager = null, config = config).also { engine = it }
    }

    private fun chunkText(text: String): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= MAX_CHUNK_CHARS) return listOf(normalized)

        val sentenceParts = normalized.split(Regex("(?<=[.!?])\\s+"))
        val out = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flush() {
            val value = buffer.toString().trim()
            if (value.isNotEmpty()) out += value
            buffer.clear()
        }

        for (part in sentenceParts) {
            if (part.length > MAX_CHUNK_CHARS) {
                flush()
                var start = 0
                while (start < part.length) {
                    var end = (start + MAX_CHUNK_CHARS).coerceAtMost(part.length)
                    if (end < part.length) {
                        val space = part.lastIndexOf(' ', end)
                        if (space > start + 60) end = space
                    }
                    out += part.substring(start, end).trim()
                    start = end
                    while (start < part.length && part[start].isWhitespace()) start++
                }
            } else if (buffer.isEmpty()) {
                buffer.append(part)
            } else if (buffer.length + 1 + part.length <= MAX_CHUNK_CHARS) {
                buffer.append(' ').append(part)
            } else {
                flush()
                buffer.append(part)
            }
        }
        flush()
        return out.filter { it.isNotBlank() }
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

    private fun playBlocking(samples: FloatArray, sampleRate: Int, token: Int) {
        if (released || token != generation.get()) return
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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

        val block = ShortArray(4096)
        var srcOffset = 0
        while (srcOffset < samples.size && !released && token == generation.get() && audioTrack === track) {
            val count = minOf(block.size, samples.size - srcOffset)
            for (i in 0 until count) {
                block[i] = (samples[srcOffset + i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            val wrote = track.write(block, 0, count, AudioTrack.WRITE_BLOCKING)
            if (wrote <= 0) break
            srcOffset += wrote
        }

        if (audioTrack === track) audioTrack = null
        runCatching { track.stop() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }

    private fun stopPlaybackOnly() {
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        stopPlaybackOnly()
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        generation.incrementAndGet()
        stopPlaybackOnly()
        worker.shutdownNow()
        engine?.runCatching { release() }
        engine = null
    }
}
