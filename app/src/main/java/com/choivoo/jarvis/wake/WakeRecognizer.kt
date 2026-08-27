package com.choivoo.jarvis.wake

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class WakeRecognizer(
    private val context: Context,
    private val onReady: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onRecoverableError: (Int, String) -> Unit,
    private val onFatalError: (Int, String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false
    private var paused = true
    private var listening = false
    private var retryCount = 0
    private var noMatchStreak = 0
    private var lastStartAt = 0L
    private var engineName = "not-initialized"

    fun start(delayMs: Long = 0L) {
        if (destroyed) return
        paused = false
        handler.removeCallbacksAndMessages(null)
        ensureRecognizer()
        if (delayMs > 0L) handler.postDelayed({ if (!destroyed && !paused) startInternal() }, delayMs)
        else startInternal()
    }

    fun ensureActive() {
        if (destroyed || paused || listening) return
        startInternal()
    }

    fun stop() {
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        destroyed = true
        paused = true
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun engine(): String = engineName

    private fun ensureRecognizer(forceRecreate: Boolean = false) {
        if (destroyed) return
        if (forceRecreate) {
            runCatching { recognizer?.destroy() }
            recognizer = null
            listening = false
        }
        if (recognizer != null) return

        recognizer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                engineName = "on-device"
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                engineName = "system"
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (_: Throwable) {
            engineName = "system"
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (paused || destroyed) return
                listening = true
                retryCount = 0
                onReady(engineName)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                listening = false
                if (destroyed || paused) return
                val message = errorMessage(error)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal background-listening churn. Never surface code 7/6 to the user.
                        noMatchStreak++
                        if (noMatchStreak >= 4) {
                            noMatchStreak = 0
                            recreateAndRestart(700L)
                        } else {
                            scheduleRestart(650L)
                        }
                    }
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        onRecoverableError(error, message)
                        scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 650L)
                    }
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                        onRecoverableError(error, message)
                        recreateAndRestart(1_000L)
                    }
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        onRecoverableError(error, message)
                        if (engineName != "on-device") recreateAndRestart(backoffDelay()) else scheduleRestart(backoffDelay())
                    }
                    SpeechRecognizer.ERROR_AUDIO -> {
                        onRecoverableError(error, message)
                        recreateAndRestart(backoffDelay())
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onFatalError(error, message)
                    else -> {
                        onRecoverableError(error, message)
                        recreateAndRestart(backoffDelay())
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                if (destroyed || paused) return
                retryCount = 0
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    noMatchStreak = 0
                    onFinal(text)
                } else scheduleRestart(550L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (destroyed || paused) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startInternal() {
        if (destroyed || paused || listening) return
        val r = recognizer ?: run { ensureRecognizer(); recognizer } ?: return
        val now = System.currentTimeMillis()
        val sinceLast = now - lastStartAt
        if (sinceLast in 0..300) {
            scheduleRestart(350L)
            return
        }
        lastStartAt = now

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, engineName == "on-device")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }

        try {
            r.startListening(intent)
            listening = true
        } catch (_: Throwable) {
            listening = false
            recreateAndRestart(backoffDelay())
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (destroyed || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!destroyed && !paused) startInternal() }, delayMs)
    }

    private fun recreateAndRestart(delayMs: Long) {
        if (destroyed || paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (destroyed || paused) return@postDelayed
            ensureRecognizer(forceRecreate = true)
            startInternal()
        }, delayMs)
    }

    private fun backoffDelay(): Long {
        retryCount = (retryCount + 1).coerceAtMost(6)
        return (450L * retryCount).coerceAtMost(3_000L)
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "오디오 입력 오류"
        SpeechRecognizer.ERROR_CLIENT -> "인식 세션이 중단됨"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한 없음"
        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간 초과"
        SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 음성 없음"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 사용 중"
        SpeechRecognizer.ERROR_SERVER -> "음성 서버 오류"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간 초과"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "음성 서버 연결 끊김"
        else -> "음성 인식 오류 $error"
    }
}
