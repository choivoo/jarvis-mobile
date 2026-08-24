package com.choivoo.jarvis.config

import com.choivoo.jarvis.BuildConfig

object JarvisConfig {
    val API_BASE_URL: String
        get() = BuildConfig.JARVIS_API_BASE_URL.trimEnd('/')

    val APP_TOKEN: String
        get() = BuildConfig.JARVIS_APP_TOKEN

    val cloudEnabled: Boolean
        get() = API_BASE_URL.startsWith("https://") && APP_TOKEN.isNotBlank()
}
