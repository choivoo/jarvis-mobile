package com.choivoo.jarvis.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
            stopService(Intent(this, WakeWordService::class.java))
            getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE).edit()
                .putBoolean(WakeWordService.KEY_ENABLED, false)
                .apply()
            refreshTile()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val open = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivityAndCollapse(open)
            return
        }

        val service = Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(WakeWordService.KEY_ENABLED, true)
            .apply()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE)
            .getBoolean(WakeWordService.KEY_ENABLED, false)
        tile.label = "JARVIS"
        tile.subtitle = if (enabled) "Wake ON" else "Wake OFF"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
