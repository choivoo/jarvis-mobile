package com.choivoo.jarvis.wake

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.choivoo.jarvis.MainActivity

class JarvisTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val enabled = getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE)
            .getBoolean(WakeWordService.KEY_ENABLED, false)

        if (enabled) {
            val listenIntent = Intent(this, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_LISTEN_NOW)
            startService(listenIntent)
            qsTile?.subtitle = "Listening"
            qsTile?.updateTile()
            return
        }

        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                88,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE)
            .getBoolean(WakeWordService.KEY_ENABLED, false)
        tile.label = "JARVIS"
        tile.subtitle = if (enabled) "Wake ON" else "Open to enable"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
