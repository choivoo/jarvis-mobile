package com.choivoo.jarvis.voice

import android.content.Context

class VoicePreferences(context: Context) {
    companion object {
        private const val PREFS = "jarvis_voice"
        private const val KEY_VOICE = "voice"
        private const val KEY_SPEED = "speed"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_LAST_PROVIDER = "last_provider"
        private const val KEY_LAST_ERROR = "last_error"

        val ALLOWED = listOf("marin", "cedar", "onyx", "echo")
        val PROVIDERS = listOf("auto", "cloud", "neural", "local")
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

    fun getSpeed(): Double = prefs.getFloat(KEY_SPEED, 0.92f).toDouble().coerceIn(0.80, 1.10)

    fun setSpeed(speed: Double) {
        prefs.edit().putFloat(KEY_SPEED, speed.coerceIn(0.80, 1.10).toFloat()).apply()
    }

    fun getProvider(): String = prefs.getString(KEY_PROVIDER, "auto")
        ?.takeIf { it in PROVIDERS }
        ?: "auto"

    fun setProvider(provider: String): Boolean {
        val normalized = provider.lowercase()
        if (normalized !in PROVIDERS) return false
        prefs.edit().putString(KEY_PROVIDER, normalized).apply()
        return true
    }

    fun recordProvider(provider: String, error: String = "") {
        prefs.edit()
            .putString(KEY_LAST_PROVIDER, provider)
            .putString(KEY_LAST_ERROR, error.take(240))
            .apply()
    }

    fun getLastProvider(): String = prefs.getString(KEY_LAST_PROVIDER, "-") ?: "-"
    fun getLastError(): String = prefs.getString(KEY_LAST_ERROR, "") ?: ""
}
