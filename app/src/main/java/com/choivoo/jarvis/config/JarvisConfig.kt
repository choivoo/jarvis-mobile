package com.choivoo.jarvis.config

object JarvisConfig {
    /**
     * Set this after the Cloudflare Worker is deployed.
     * Example: https://jarvis-brain.<your-subdomain>.workers.dev
     */
    const val API_BASE_URL = ""

    val cloudEnabled: Boolean
        get() = API_BASE_URL.startsWith("https://")
}
