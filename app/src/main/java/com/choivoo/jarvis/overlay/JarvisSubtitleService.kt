package com.choivoo.jarvis.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class JarvisSubtitleService : Service() {
    companion object {
        const val ACTION_SHOW = "com.choivoo.jarvis.subtitle.SHOW"
        const val ACTION_HIDE = "com.choivoo.jarvis.subtitle.HIDE"
        const val ACTION_START = "com.choivoo.jarvis.subtitle.START"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL = "jarvis_subtitles"
        private const val NOTIFICATION_ID = 2202

        fun show(context: Context, text: String) {
            if (text.isBlank()) return
            if (!Settings.canDrawOverlays(context)) {
                context.getSharedPreferences("jarvis_subtitle", Context.MODE_PRIVATE)
                    .edit().putString("pending", text.take(500)).apply()
                if (context is Activity) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
                return
            }
            val intent = Intent(context, JarvisSubtitleService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_TEXT, text.take(500))
            startCompat(context, intent)
        }

        fun ensureRunning(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            startCompat(context, Intent(context, JarvisSubtitleService::class.java).setAction(ACTION_START))
        }

        private fun startCompat(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }

    private lateinit var wm: WindowManager
    private var view: TextView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "JARVIS Korean subtitles", NotificationManager.IMPORTANCE_MIN).apply {
                description = "다른 앱 위에 JARVIS 한국어 자막을 표시합니다."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hide()
            ACTION_SHOW -> showOverlay(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_START -> {
                val pending = getSharedPreferences("jarvis_subtitle", MODE_PRIVATE).getString("pending", "").orEmpty()
                if (pending.isNotBlank()) showOverlay(pending)
            }
        }
        return START_STICKY
    }

    private fun showOverlay(text: String) {
        if (text.isBlank() || !Settings.canDrawOverlays(this)) return
        if (view == null) {
            view = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                maxLines = 4
                setPadding(34, 18, 34, 18)
                setBackgroundColor(0xE3091820.toInt())
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 120
            }
            wm.addView(view, params)
        }
        view?.text = text
        getSharedPreferences("jarvis_subtitle", MODE_PRIVATE).edit().putString("pending", text).apply()
    }

    private fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("JARVIS Subtitle HUD")
            .setContentText("한국어 자막 오버레이가 준비되어 있습니다.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() { hide(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
