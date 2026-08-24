package com.choivoo.jarvis.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class JarvisAutomationStore(context: Context) {
    data class Automation(
        val id: Int,
        val hour: Int,
        val minute: Int,
        val message: String,
        val enabled: Boolean = true
    )

    private val prefs = context.getSharedPreferences("jarvis_automations", Context.MODE_PRIVATE)

    fun all(): List<Automation> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        Automation(
                            id = o.optInt("id"),
                            hour = o.optInt("hour"),
                            minute = o.optInt("minute"),
                            message = o.optString("message"),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(hour: Int, minute: Int, message: String): Automation {
        val item = Automation(
            id = (System.currentTimeMillis() and 0x7fffffff).toInt(),
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            message = message.take(120)
        )
        save(all() + item)
        return item
    }

    fun remove(id: Int) = save(all().filterNot { it.id == id })

    private fun save(items: List<Automation>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("hour", it.hour)
                    .put("minute", it.minute)
                    .put("message", it.message)
                    .put("enabled", it.enabled)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}

class JarvisAutomationScheduler(private val context: Context) {
    private val store = JarvisAutomationStore(context)

    fun createDaily(hour: Int, minute: Int, message: String): JarvisAutomationStore.Automation {
        val item = store.add(hour, minute, message)
        schedule(item)
        return item
    }

    fun rescheduleAll() {
        store.all().filter { it.enabled }.forEach(::schedule)
    }

    fun cancel(id: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingIntent(id, ""))
        store.remove(id)
    }

    private fun schedule(item: JarvisAutomationStore.Automation) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(item.id, item.message)
        )
    }

    private fun pendingIntent(id: Int, message: String): PendingIntent {
        val intent = Intent(context, JarvisAutomationReceiver::class.java)
            .putExtra("id", id)
            .putExtra("message", message)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class JarvisAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val message = intent?.getStringExtra("message")?.ifBlank { "JARVIS 자동화 알림입니다." }
            ?: "JARVIS 자동화 알림입니다."
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "jarvis_automation"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "JARVIS Automations", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("JARVIS")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        } else {
            android.app.Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("JARVIS")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        }
        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}

class JarvisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            JarvisAutomationScheduler(context).rescheduleAll()
        }
    }
}
