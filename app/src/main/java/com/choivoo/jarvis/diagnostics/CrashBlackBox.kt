package com.choivoo.jarvis.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant

object CrashBlackBox {
    private const val PREFS = "jarvis_crash_black_box"
    private const val FILE_NAME = "last_crash.txt"

    fun note(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, value.take(1200)).apply()
    }

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val wake = context.getSharedPreferences("jarvis_wake", Context.MODE_PRIVATE)
                val voice = context.getSharedPreferences("jarvis_voice", Context.MODE_PRIVATE)
                val report = buildString {
                    appendLine("JARVIS Crash Black Box")
                    appendLine("time=${Instant.now()}")
                    appendLine("thread=${thread.name}")
                    appendLine("exception=${throwable::class.java.name}")
                    appendLine("message=${throwable.message.orEmpty()}")
                    appendLine("last_command=${prefs.getString("last_command", "").orEmpty()}")
                    appendLine("last_phase=${prefs.getString("last_phase", "").orEmpty()}")
                    appendLine("wake_status=${wake.getString("status", "").orEmpty()}")
                    appendLine("wake_last_heard=${wake.getString("last_heard", "").orEmpty()}")
                    appendLine("voice_provider=${voice.getString("provider", "").orEmpty()}")
                    appendLine("voice_last_provider=${voice.getString("last_provider", "").orEmpty()}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                }
                File(context.filesDir, FILE_NAME).writeText(report)
                prefs.edit()
                    .putBoolean("has_crash", true)
                    .putString("last_exception", throwable::class.java.simpleName)
                    .putString("last_message", throwable.message.orEmpty().take(500))
                    .apply()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrash(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("has_crash", false)

    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("has_crash", false)) return "저장된 앱 크래시 기록이 없습니다."
        val exception = prefs.getString("last_exception", "Unknown").orEmpty()
        val message = prefs.getString("last_message", "").orEmpty()
        return "마지막 앱 크래시: $exception${if (message.isBlank()) "" else " · $message"}"
    }

    fun reportFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching { reportFile(context).delete() }
    }
}
