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

    init {
        initSpeechRecognizer()
        initTts()
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("이 기기에서 음성 인식을 사용할 수 없어.")
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
                        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력 오류가 발생했어."
                        SpeechRecognizer.ERROR_CLIENT -> "음성 인식이 중단됐어."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 네트워크 연결을 확인해줘."
                        SpeechRecognizer.ERROR_NO_MATCH -> "잘 못 들었어. 다시 말해줘."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중이야. 잠시 후 다시 눌러줘."
                        SpeechRecognizer.ERROR_SERVER -> "음성 인식 서버 오류가 발생했어."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말소리를 기다렸는데 들리지 않았어."
                        else -> "음성 인식 오류가 발생했어. 코드 $error"
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) onError("잘 못 들었어. 다시 말해줘.") else onFinalText(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
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
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setSpeechRate(0.96f)
                engine.setPitch(0.92f)
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        val recognizer = speechRecognizer ?: run {
            onError("음성 인식기를 사용할 수 없어.")
            return
        }

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

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
    }

    fun speak(text: String) {
        if (text.isBlank()) {
            onSpeakingFinished()
            return
        }
        stopSpeaking()
        onSpeakingStarted()

        if (JarvisConfig.cloudEnabled) {
            speakCloudOrFallback(text)
        } else {
            speakLocal(text)
        }
    }

    private fun speakCloudOrFallback(text: String) {
        thread(name = "jarvis-cloud-tts") {
            try {
                val connection = (URL("${JarvisConfig.API_BASE_URL.trimEnd('/')}/v1/tts").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "audio/mpeg")
                }
                val body = JSONObject().put("text", text).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("HTTP $status")

                val file = File.createTempFile("jarvis_voice_", ".mp3", context.cacheDir)
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()

                mainHandler.post { playCloudFile(file, text) }
            } catch (_: Exception) {
                mainHandler.post { speakLocal(text) }
            }
        }
    }

    private fun playCloudFile(file: File, fallbackText: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    file.delete()
                    onSpeakingFinished()
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    mediaPlayer = null
                    file.delete()
                    speakLocal(fallbackText)
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            file.delete()
            speakLocal(fallbackText)
        }
    }

    private fun speakLocal(text: String) {
        if (!ttsReady) {
            onSpeakingFinished()
            return
        }
        val engine = tts ?: run {
            onSpeakingFinished()
            return
        }
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = mainHandler.post { onSpeakingFinished() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = mainHandler.post { onSpeakingFinished() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    fun stopSpeaking() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}
