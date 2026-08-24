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
import android.os.Handler
import android.os.Looper
import com.choivoo.jarvis.MainActivity
import com.choivoo.jarvis.ai.BrainClient
import com.choivoo.jarvis.memory.LocalMemoryStore
import com.choivoo.jarvis.tools.CommandRouter
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
        private const val CHANNEL_ID = "jarvis_wake_service"
        private const val NOTIFICATION_ID = 7001
        private const val PREFS = "jarvis_wake"
        private const val KEY_ENABLED = "enabled"
    }

    private enum class Mode { WAKE, ACK, COMMAND, RESPONSE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var voice: VoiceController
    private lateinit var router: CommandRouter
    private val brain = BrainClient()
    private var mode = Mode.WAKE
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        router = CommandRouter(this, LocalMemoryStore(this))
        voice = VoiceController(
            context = this,
            onListeningStarted = { updateNotification("호출어 ‘자비스’를 기다리는 중입니다.") },
            onPartialText = {},
            onFinalText = ::handleRecognizedText,
            onError = {
                if (!destroyed) scheduleListen(900)
            },
            onSpeakingStarted = {},
            onSpeakingFinished = ::handleSpeakingFinished
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWakeService()
            return START_NOT_STICKY
        }

        startAsMicrophoneForeground()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        mode = Mode.WAKE
        scheduleListen(350)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleRecognizedText(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) {
            scheduleListen(500)
            return
        }

        when (mode) {
            Mode.WAKE -> {
                val normalized = text.lowercase().replace(" ", "")
                val wakeIndex = normalized.indexOf("자비스")
                if (wakeIndex >= 0) {
                    val originalIndex = text.lowercase().indexOf("자비스")
                    val trailing = if (originalIndex >= 0) text.substring(originalIndex + 3).trim(' ', ',', '.', '!', '?') else ""
                    if (trailing.isNotBlank()) {
                        mode = Mode.COMMAND
                        processCommand(trailing)
                    } else {
                        mode = Mode.ACK
                        updateNotification("호출되었습니다. 명령을 기다리는 중입니다.")
                        voice.speak("네, 말씀하세요.")
                    }
                } else {
                    scheduleListen(450)
                }
            }

            Mode.COMMAND -> processCommand(text)
            Mode.ACK, Mode.RESPONSE -> Unit
        }
    }

    private fun handleSpeakingFinished() {
        when (mode) {
            Mode.ACK -> {
                mode = Mode.COMMAND
                scheduleListen(250)
            }
            Mode.RESPONSE -> {
                mode = Mode.WAKE
                updateNotification("호출어 ‘자비스’를 기다리는 중입니다.")
                scheduleListen(550)
            }
            else -> Unit
        }
    }

    private fun processCommand(command: String) {
        updateNotification("명령 처리 중: ${command.take(36)}")
        val local = router.handle(command)
        if (local.handledLocally) {
            speakResponse(local.response)
            return
        }

        mode = Mode.RESPONSE
        scope.launch {
            val reply = brain.chat(command, emptyList())
            speakResponse(reply)
        }
    }

    private fun speakResponse(reply: String) {
        mode = Mode.RESPONSE
        updateNotification("응답 중입니다.")
        voice.speak(reply)
    }

    private fun scheduleListen(delayMs: Long) {
        if (destroyed) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!destroyed && mode != Mode.ACK && mode != Mode.RESPONSE) {
                voice.startListening()
            }
        }, delayMs)
    }

    private fun startAsMicrophoneForeground() {
        val notification = buildNotification("호출어 ‘자비스’를 기다리는 중입니다.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS Wake Service")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "대기 종료", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Wake Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS가 호출어를 기다리는 동안 표시되는 알림입니다."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopWakeService() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        handler.removeCallbacksAndMessages(null)
        voice.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
