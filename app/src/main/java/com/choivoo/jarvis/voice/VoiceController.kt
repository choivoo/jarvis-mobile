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
import com.choivoo.jarvis.config.JarvisConfig
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
    private val onSpeakingFinished: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voicePreferences = VoicePreferences(context)
    private val voiceCacheDir = File(context.cacheDir, "jarvis_voice_cache").apply { mkdirs() }

    init { initSpeechRecognizer(); initTts() }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("이 기기에서는 음성 인식을 사용할 수 없습니다.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onListeningStarted()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력 오류가 발생했습니다."
                        SpeechRecognizer.ERROR_CLIENT -> "음성 인식이 중단되었습니다."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 네트워크 연결을 확인해 주세요."
                        SpeechRecognizer.ERROR_NO_MATCH -> "잘 듣지 못했습니다. 다시 말씀해 주세요."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다. 잠시 후 다시 시도해 주세요."
                        SpeechRecognizer.ERROR_SERVER -> "음성 인식 서버 오류가 발생했습니다."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀을 기다렸지만 음성이 감지되지 않았습니다."
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "음성 인식 서버 연결이 끊어졌습니다."
                        else -> "음성 인식 오류가 발생했습니다. 코드 $error"
                    }
                    onError(message)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) onError("잘 듣지 못했습니다. 다시 말씀해 주세요.") else onFinalText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onPartialText(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val languageResult = engine.setLanguage(Locale.KOREAN)
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setSpeechRate(0.94f)
                engine.setPitch(0.90f)
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        val recognizer = speechRecognizer ?: run { onError("음성 인식기를 사용할 수 없습니다."); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS")
        }
        recognizer.startListening(intent)
    }

    fun stopListening() = speechRecognizer?.stopListening() ?: Unit
    fun cancelListening() = speechRecognizer?.cancel() ?: Unit

    fun speak(text: String) {
        if (text.isBlank()) { onSpeakingFinished(); return }
        stopSpeaking(); onSpeakingStarted()
        if (JarvisConfig.cloudEnabled) speakCloud(text) else speakLocal(text)
    }

    private fun speakCloud(text: String) {
        val voice = voicePreferences.getVoice()
        val speed = voicePreferences.getSpeed()
        val cacheFile = File(voiceCacheDir, "${sha256("$voice|$speed|$text")}.mp3")
        if (cacheFile.exists() && cacheFile.length() > 256) {
            playCloudFile(cacheFile, deleteAfter = false)
            return
        }

        thread(name = "jarvis-cloud-tts") {
            try {
                val connection = (URL("${JarvisConfig.API_BASE_URL}/v1/tts").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 50_000; doOutput = true
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
                    val parsed = runCatching { JSONObject(detail).optString("error") }.getOrDefault("")
                    val friendly = when {
                        parsed.contains("quota") -> "OpenAI 음성 API 사용 한도 또는 결제 한도에 도달했습니다. API Platform의 Billing/Usage Limits를 확인해 주세요."
                        parsed.contains("rate") || status == 429 -> "OpenAI 음성 API 요청 한도에 잠시 도달했습니다. V0.9가 자동 재시도했지만 아직 제한 중입니다. 잠시 후 다시 시도해 주세요."
                        else -> "Cinematic Voice 서버 오류가 발생했습니다. HTTP $status"
                    }
                    throw IllegalStateException(friendly)
                }
                connection.inputStream.use { input -> cacheFile.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                mainHandler.post { playCloudFile(cacheFile, deleteAfter = false) }
            } catch (e: Exception) {
                mainHandler.post { onError(e.message ?: "Cinematic Voice 연결에 실패했습니다."); onSpeakingFinished() }
            }
        }
    }

    private fun playCloudFile(file: File, deleteAfter: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release(); mediaPlayer = null
                    if (deleteAfter) file.delete()
                    onSpeakingFinished()
                }
                setOnErrorListener { player, _, _ ->
                    player.release(); mediaPlayer = null
                    if (deleteAfter) file.delete()
                    onError("Cinematic Voice 오디오 재생에 실패했습니다."); onSpeakingFinished(); true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            if (deleteAfter) file.delete()
            onError("Cinematic Voice 재생 준비에 실패했습니다: ${e.message?.take(80) ?: "unknown"}")
            onSpeakingFinished()
        }
    }

    private fun speakLocal(text: String) {
        if (!ttsReady) { onError("오프라인 TTS를 사용할 수 없습니다."); onSpeakingFinished(); return }
        val engine = tts ?: run { onSpeakingFinished(); return }
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { mainHandler.post { onSpeakingFinished() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { mainHandler.post { onSpeakingFinished() } }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    fun stopSpeaking() { runCatching { mediaPlayer?.stop() }; mediaPlayer?.release(); mediaPlayer = null; tts?.stop() }
    fun destroy() { speechRecognizer?.destroy(); speechRecognizer = null; stopSpeaking(); tts?.shutdown(); tts = null }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
