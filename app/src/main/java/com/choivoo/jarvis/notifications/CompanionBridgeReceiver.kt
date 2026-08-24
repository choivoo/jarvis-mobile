package com.choivoo.jarvis.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CompanionBridgeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_NOTIFICATION = "com.choivoo.jarvis.COMPANION_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_NOTIFICATION) return
        val packageName = intent.getStringExtra("packageName").orEmpty().take(160)
        val title = intent.getStringExtra("title").orEmpty().take(240)
        val text = intent.getStringExtra("text").orEmpty().take(600)
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        if (packageName.isBlank() && title.isBlank() && text.isBlank()) return

        JarvisNotificationStore(context).add(
            JarvisNotificationStore.Item(
                packageName = packageName,
                title = title,
                text = text,
                timestamp = timestamp
            )
        )
    }
}
