package com.choivoo.jarvis.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.choivoo.jarvis.MainActivity
import com.choivoo.jarvis.core.JarvisAssistantEngine
import com.choivoo.jarvis.overlay.JarvisSubtitleService
import com.choivoo.jarvis.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WakeWordService : Service() {
    companion object {
        const val ACTION_START = "com.choivoo.jarvis.wake.START"
        const val ACTION_STOP = "com.choivoo.jarvis.wake.STOP"
        const val ACTION_LISTEN_NOW = "com.choivoo.jarvis.wake.LISTEN_NOW"
        private const val CHANNEL_ID = "jarvis_wake_service"
        private const val NOTIFICATION_ID = 7001
        private const val POST_TTS_REARM_DELAY_MS = 750L
        const val PREFS = "jarvis_wake"
        const val KEY_ENABLED = "enabled"
        const val KEY_ENGINE = "engine"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_LAST_HEARD = "last_heard"
        const val KEY_STATUS = "status"
    }

    private enum class Mode { WAKE, ACK, COMMAND, RESPONSE }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var voice: VoiceController
    private lateinit var recognizer: WakeRecognizer
    private lateinit var assistant: JarvisAssistantEngine
    private var mode = Mode.WAKE
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        assistant = JarvisAssistantEngine(this)
        voice = VoiceController(
            context = this,
            onListeningStarted = {},
            onPartialText = {},
            onFinalText = {},
            onError = { message ->
                saveDiagnostic(KEY_LAST_ERROR, message)
                updateNotification("음성 출력 오류: ${message.take(48)}")
                if (mode == Mode.RESPONSE && !destroyed) {
                    mode = Mode.WAKE
                    recognizer.start(POST_TTS_REARM_DELAY_MS)
                }
            },
            onSpeakingStarted = {
                recognizer.stop()
                saveDiagnostic(KEY_STATUS, "speaking")
            },
            onSpeakingFinished = ::handleSpeakingFinished,
            enableRecognizer = false
        )
        recognizer = WakeRecognizer(
            context = this,
            onReady = { engine ->
                saveDiagnostic(KEY_ENGINE, engine)
                saveDiagnostic(KEY_STATUS, if (mode == Mode.WAKE) "waiting-wake" else "waiting-command")
                updateNotification(if (mode == Mode.WAKE) "호출어 ‘자비스’를 기다리는 중 · $engine" else "명령을 듣는 중 · $engine")
            },
            onPartial = { partial -> if (mode == Mode.WAKE && containsWakeWord(partial)) recognizer.stop() },
            onFinal = ::handleRecognizedText,
            onRecoverableError = { code, message ->
                saveDiagnostic(KEY_LAST_ERROR, "code=$code $message")
                saveDiagnostic(KEY_STATUS, "recovering")
                updateNotification("자동 복구 중 · $message")
            },
            onFatalError = { code, message ->
                saveDiagnostic(KEY_LAST_ERROR, "FATAL code=$code $message")
                saveDiagnostic(KEY_STATUS, "fatal")
                updateNotification("Wake 오류 · $message")
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWakeService()
            return START_NOT_STICKY
        }
        startAsMicrophoneForeground()
        JarvisSubtitleService.ensureRunning(this)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        if (intent?.action == ACTION_LISTEN_NOW) {
            mode = Mode.COMMAND
            saveDiagnostic(KEY_STATUS, "manual-command")
            recognizer.start()
            return START_STICKY
        }
        mode = Mode.WAKE
        saveDiagnostic(KEY_STATUS, "starting")
        recognizer.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleRecognizedText(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) { recognizer.start(); return }
        saveDiagnostic(KEY_LAST_HEARD, text.take(160))
        saveDiagnostic(KEY_LAST_ERROR, "")
        when (mode) {
            Mode.WAKE -> {
                if (containsWakeWord(text)) {
                    val trailing = trailingAfterWake(text)
                    recognizer.stop()
                    if (trailing.isNotBlank()) {
                        mode = Mode.COMMAND
                        processCommand(trailing)
                    } else {
                        mode = Mode.ACK
                        updateNotification("호출되었습니다. 명령을 기다립니다.")
                        voice.speak("네, 말씀하세요.")
                    }
                } else recognizer.start()
            }
            Mode.COMMAND -> { recognizer.stop(); processCommand(text) }
            Mode.ACK, Mode.RESPONSE -> Unit
        }
    }

    private fun handleSpeakingFinished() {
        if (destroyed) return
        when (mode) {
            Mode.ACK -> {
                mode = Mode.COMMAND
                saveDiagnostic(KEY_STATUS, "waiting-command")
                recognizer.start(POST_TTS_REARM_DELAY_MS)
            }
            Mode.RESPONSE -> {
                mode = Mode.WAKE
                saveDiagnostic(KEY_STATUS, "waiting-wake")
                updateNotification("호출어 ‘자비스’를 기다리는 중 · ${recognizer.engine()}")
                recognizer.start(POST_TTS_REARM_DELAY_MS)
            }
            else -> Unit
        }
    }

    private fun processCommand(command: String) {
        mode = Mode.RESPONSE
        saveDiagnostic(KEY_STATUS, "processing")
        updateNotification("명령 처리 중: ${command.take(36)}")
        scope.launch {
            val result = assistant.process(command)
            speakResponse(result.response)
        }
    }

    private fun speakResponse(reply: String) {
        mode = Mode.RESPONSE
        saveDiagnostic(KEY_STATUS, "speaking")
        updateNotification("응답 중입니다.")
        voice.speak(reply)
    }

    private fun containsWakeWord(text: String): Boolean {
        val normalized = text.lowercase().replace(" ", "").replace(".", "").replace(",", "")
        return normalized.contains("자비스") || normalized.contains("jarvis")
    }

    private fun trailingAfterWake(text: String): String {
        val korean = text.indexOf("자비스", ignoreCase = true)
        if (korean >= 0) return text.substring(korean + 3).trim(' ', ',', '.', '!', '?')
        val english = text.indexOf("jarvis", ignoreCase = true)
        if (english >= 0) return text.substring(english + 6).trim(' ', ',', '.', '!', '?')
        return ""
    }

    private fun startAsMicrophoneForeground() {
        val notification = buildNotification("JARVIS Wake Core 시작 중입니다.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val listenIntent = PendingIntent.getService(
            this, 2,
            Intent(this, WakeWordService::class.java).setAction(ACTION_LISTEN_NOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS MARK III · V2.3")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_btn_speak_now, "지금 듣기", listenIntent)
            .addAction(android.R.drawable.ic_media_pause, "대기 종료", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Wake Core", NotificationManager.IMPORTANCE_LOW).apply {
                description = "JARVIS가 백그라운드에서 호출어를 기다리는 동안 표시됩니다."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun saveDiagnostic(key: String, value: String) =
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, value).apply()

    private fun stopWakeService() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "stopped")
            .apply()
        recognizer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "destroyed")
            .apply()
        recognizer.destroy()
        voice.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
