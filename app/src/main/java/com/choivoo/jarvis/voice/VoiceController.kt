package com.choivoo.jarvis.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.choivoo.jarvis.config.JarvisConfig
import com.choivoo.jarvis.core.JarvisAssistantEngine
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

class VoiceController(
    private val context: Context,
    private val onListeningStarted: () -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingFinished: () -> Unit,
    private val enableRecognizer: Boolean = true
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var basicReady = false
    private var basicInitFinished = false
    private var player: MediaPlayer? = null
    private var neural: StandaloneNeuralTts? = null
    private val main = Handler(Looper.getMainLooper())
    private val prefs = VoicePreferences(context)
    private val cache = File(context.cacheDir, "jarvis_voice_cache").apply { mkdirs() }

    init {
        if (enableRecognizer) initRecognizer()
        initBasicTts()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onListeningStarted()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = onError("음성 인식 오류가 발생했습니다. 코드 $error")
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onFinalText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartialText)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun initBasicTts() {
        tts = TextToSpeech(context) { status ->
            basicInitFinished = true
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val language = engine.setLanguage(Locale.UK)
            basicReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            engine.setSpeechRate(0.92f)
            engine.setPitch(0.86f)
            chooseBestBritishVoice(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post(onSpeakingFinished) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post(onSpeakingFinished) }
            })
        }
    }

    private fun chooseBestBritishVoice(engine: TextToSpeech) {
        val voices = engine.voices.orEmpty()
        val gb = voices.filter { it.locale.language == "en" && it.locale.country == "GB" }
        val english = if (gb.isNotEmpty()) gb else voices.filter { it.locale.language == "en" }
        val selected: Voice? = english
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality })
            .firstOrNull()
        if (selected != null) runCatching { engine.voice = selected }
    }

    fun startListening() {
        if (!enableRecognizer) return
        stopSpeaking()
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    fun stopListening() { recognizer?.stopListening() }
    fun cancelListening() { recognizer?.cancel() }

    fun speak(text: String) {
        if (text.isBlank()) { onSpeakingFinished(); return }
        val speech = resolveBritishSpeech(text)
        stopSpeaking()
        onSpeakingStarted()
        when (prefs.getProvider()) {
            "cloud" -> speakCloud(speech, fallbackToBasic = true)
            "neural" -> speakNeural(speech, allowBasicFallback = true, reason = "neural-forced")
            "local" -> speakBasic(speech, "basic-forced")
            else -> {
                // Crash-safe AUTO: never enter native sherpa-onnx automatically.
                // Cloud is preferred when configured; Android Basic UK is the safe fallback.
                if (JarvisConfig.cloudEnabled) speakCloud(speech, fallbackToBasic = true)
                else speakBasic(speech, "basic-auto-safe")
            }
        }
    }

    private fun resolveBritishSpeech(input: String): String {
        val bilingual = context.getSharedPreferences(JarvisAssistantEngine.SPEECH_PREFS, Context.MODE_PRIVATE)
        val subtitle = bilingual.getString(JarvisAssistantEngine.KEY_SUBTITLE, "").orEmpty()
        val speech = bilingual.getString(JarvisAssistantEngine.KEY_SPEECH, "").orEmpty()
        if (input == subtitle && speech.isNotBlank()) return speech
        val containsHangul = input.any { it.code in 0xAC00..0xD7A3 }
        return if (containsHangul) BritishSpeech.fromKorean(input) else input
    }

    private fun speakCloud(text: String, fallbackToBasic: Boolean) {
        val voice = prefs.getVoice()
        val speed = prefs.getSpeed()
        val file = File(cache, "${sha256("en-GB|$voice|$speed|$text")}.mp3")
        if (file.exists() && file.length() > 256) {
            prefs.recordProvider("cloud-cache")
            playCloud(file, text, fallbackToBasic)
            return
        }
        thread(name = "jarvis-cloud-tts") {
            try {
                val connection = (URL("${JarvisConfig.API_BASE_URL}/v1/tts").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 50_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "audio/mpeg")
                    setRequestProperty("X-Jarvis-Token", JarvisConfig.APP_TOKEN)
                }
                val body = JSONObject().put("text", text).put("voice", voice).put("speed", speed).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    connection.disconnect()
                    throw IllegalStateException("Cloud voice HTTP $status ${detail.take(100)}")
                }
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                prefs.recordProvider("cloud")
                main.post { playCloud(file, text, fallbackToBasic) }
            } catch (t: Throwable) {
                prefs.recordProvider("cloud-failed", t.message.orEmpty())
                main.post {
                    if (fallbackToBasic) speakBasic(text, "basic-cloud-fallback")
                    else {
                        onError("Cloud Cinematic Voice 연결에 실패했습니다.")
                        onSpeakingFinished()
                    }
                }
            }
        }
    }

    private fun playCloud(file: File, fallbackText: String, fallbackToBasic: Boolean) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    player = null
                    onSpeakingFinished()
                }
                setOnErrorListener { p, _, _ ->
                    p.release()
                    player = null
                    if (fallbackToBasic) speakBasic(fallbackText, "basic-cloud-playback-fallback")
                    else onSpeakingFinished()
                    true
                }
                prepareAsync()
            }
        } catch (_: Throwable) {
            if (fallbackToBasic) speakBasic(fallbackText, "basic-cloud-playback-fallback")
            else onSpeakingFinished()
        }
    }

    private fun getOrCreateNeural(): StandaloneNeuralTts {
        neural?.let { return it }
        return StandaloneNeuralTts(context).also { neural = it }
    }

    private fun speakNeural(text: String, allowBasicFallback: Boolean, reason: String) {
        val engine = runCatching { getOrCreateNeural() }.getOrElse {
            if (allowBasicFallback) speakBasic(text, "basic-neural-init-fallback")
            else {
                onError("Neural Local Voice 초기화에 실패했습니다.")
                onSpeakingFinished()
            }
            return
        }
        if (!engine.isAvailable()) {
            if (allowBasicFallback) speakBasic(text, "basic-no-neural")
            else {
                onError("APK 내부 Neural Local 모델을 찾을 수 없습니다.")
                onSpeakingFinished()
            }
            return
        }
        prefs.recordProvider(reason)
        engine.speak(
            text = text,
            speed = prefs.getSpeed().toFloat(),
            onStart = {},
            onDone = { main.post { prefs.recordProvider("neural"); onSpeakingFinished() } },
            onError = { message ->
                main.post {
                    prefs.recordProvider("neural-failed", message)
                    runCatching { neural?.release() }
                    neural = null
                    if (allowBasicFallback) speakBasic(text, "basic-neural-fallback")
                    else {
                        onError("Neural Local Voice 오류가 발생했습니다.")
                        onSpeakingFinished()
                    }
                }
            }
        )
    }

    private fun speakBasic(text: String, label: String, attempt: Int = 0) {
        prefs.recordProvider(label)
        if (!basicReady) {
            if (!basicInitFinished && attempt < 16) {
                main.postDelayed({ speakBasic(text, label, attempt + 1) }, 250L)
                return
            }
            onError("이 기기에서 British English 기본 TTS를 사용할 수 없습니다.")
            onSpeakingFinished()
            return
        }
        val engine = tts
        if (engine == null) {
            onSpeakingFinished()
            return
        }
        engine.setSpeechRate(0.92f)
        engine.setPitch(0.86f)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-en-gb-${System.nanoTime()}")
        if (result == TextToSpeech.ERROR) {
            onError("British English 기본 TTS 재생을 시작하지 못했습니다.")
            onSpeakingFinished()
        }
    }

    fun isNeuralReady(): Boolean = runCatching {
        context.assets.open("jarvis_tts/supertonic-3/tts.json").close()
        true
    }.getOrDefault(false)

    fun isNeuralInstalled(): Boolean = isNeuralReady()

    fun stopSpeaking() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        runCatching { neural?.stop() }
        runCatching { tts?.stop() }
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        stopSpeaking()
        runCatching { neural?.release() }
        neural = null
        tts?.shutdown()
        tts = null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
