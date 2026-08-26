package com.choivoo.jarvis

import android.app.Application
import com.choivoo.jarvis.diagnostics.CrashBlackBox

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashBlackBox.install(this)
        CrashBlackBox.note(this, "last_phase", "application-started")
    }
}
