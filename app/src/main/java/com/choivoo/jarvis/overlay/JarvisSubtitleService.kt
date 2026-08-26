package com.choivoo.jarvis.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
        const val EXTRA_TEXT = "text"
        private const val CHANNEL = "jarvis_subtitles"
        fun show(context: Context, text: String) {
            if (!Settings.canDrawOverlays(context)) return
            val i = Intent(context, JarvisSubtitleService::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }

    private lateinit var wm: WindowManager
    private var view: TextView? = null

    override fun onCreate() {
        super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "JARVIS subtitles", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("JARVIS").setContentText("한국어 자막 오버레이 실행 중").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(2202, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hide()
            ACTION_SHOW -> show(intent.getStringExtra(EXTRA_TEXT).orEmpty())
        }
        return START_STICKY
    }

    private fun show(text: String) {
        if (text.isBlank() || !Settings.canDrawOverlays(this)) return
        if (view == null) {
            view = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
                setPadding(28, 18, 28, 18); setBackgroundColor(0xD9101720.toInt())
            }
            val p = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; p.y = 110
            wm.addView(view, p)
        }
        view?.text = text
    }

    private fun hide() { view?.let { runCatching { wm.removeView(it) } }; view = null }
    override fun onDestroy() { hide(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
