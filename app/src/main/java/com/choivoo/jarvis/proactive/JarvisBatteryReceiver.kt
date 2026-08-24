package com.choivoo.jarvis.proactive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build

class JarvisBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val prefs = context.getSharedPreferences("jarvis_proactive", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("battery_alerts", true)) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) (level * 100 / scale) else -1

        when (action) {
            Intent.ACTION_BATTERY_LOW -> notify(
                context,
                "배터리가 부족합니다",
                if (percent >= 0) "현재 배터리는 ${percent}%입니다. 충전을 권장합니다." else "배터리가 부족합니다. 충전을 권장합니다.",
                9101
            )
            Intent.ACTION_POWER_CONNECTED -> {
                if (prefs.getBoolean("charging_alerts", false)) {
                    notify(context, "충전을 시작했습니다", "전원이 연결되었습니다.", 9102)
                }
            }
        }
    }

    private fun notify(context: Context, title: String, text: String, id: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "jarvis_proactive"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channel, "JARVIS Proactive", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "JARVIS가 중요한 기기 상태를 먼저 알려줍니다."
                }
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            Notification.Builder(context)
        }
        notification
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("JARVIS · $title")
            .setContentText(text)
            .setAutoCancel(true)
        manager.notify(id, notification.build())
    }
}
