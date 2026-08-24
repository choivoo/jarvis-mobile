package com.choivoo.jarvis.voice

import android.content.Context

class VoicePreferences(context: Context) {
    companion object {
        private const val PREFS = "jarvis_voice"
        private const val KEY_VOICE = "voice"
        private const val KEY_SPEED = "speed"
        val ALLOWED = listOf("marin", "cedar", "onyx", "echo")
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getVoice(): String = prefs.getString(KEY_VOICE, "marin")
        ?.takeIf { it in ALLOWED }
        ?: "marin"

    fun setVoice(voice: String): Boolean {
        val normalized = voice.lowercase()
        if (normalized !in ALLOWED) return false
        prefs.edit().putString(KEY_VOICE, normalized).apply()
        return true
    }

    fun getSpeed(): Double = prefs.getFloat(KEY_SPEED, 0.92f).toDouble().coerceIn(0.75, 1.15)

    fun setSpeed(speed: Double) {
        prefs.edit().putFloat(KEY_SPEED, speed.coerceIn(0.75, 1.15).toFloat()).apply()
    }
}
