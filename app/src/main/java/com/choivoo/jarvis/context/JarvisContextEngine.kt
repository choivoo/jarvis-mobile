package com.choivoo.jarvis.context

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.choivoo.jarvis.calendar.JarvisCalendar
import com.choivoo.jarvis.notifications.JarvisNotificationStore
import com.choivoo.jarvis.weather.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

class JarvisContextEngine(private val context: Context) {
    private val weather = WeatherClient(context)
    private val calendar = JarvisCalendar(context)
    private val notifications = JarvisNotificationStore(context)

    suspend fun snapshot(includeWeather: Boolean = true): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("localTime", LocalDateTime.now().toString())

        val battery = context.getSystemService(BatteryManager::class.java)
        root.put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        root.put("network", networkState())

        weather.bestLastKnownLocation()?.let {
            root.put("location", JSONObject().put("latitude", it.latitude).put("longitude", it.longitude))
        }

        if (includeWeather && weather.hasLocationPermission()) {
            weather.current().getOrNull()?.let { w ->
                root.put(
                    "weather",
                    JSONObject()
                        .put("temperatureC", w.temperature)
                        .put("feelsLikeC", w.feelsLike)
                        .put("precipitationMm", w.precipitation)
                        .put("windKmh", w.windSpeed)
                        .put("precipitationProbability", w.precipitationProbability)
                )
            }
        }

        val events = JSONArray()
        calendar.upcoming(limit = 5).forEach {
            events.put(JSONObject().put("title", it.title).put("startMillis", it.startMillis))
        }
        root.put("upcomingCalendar", events)

        val recent = JSONArray()
        notifications.getAll().take(8).forEach {
            recent.put(
                JSONObject()
                    .put("app", it.packageName)
                    .put("title", it.title)
                    .put("text", it.text.take(140))
                    .put("timestamp", it.timestamp)
            )
        }
        root.put("recentNotifications", recent)
        root.toString()
    }

    private fun networkState(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "offline"
        val caps = manager.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "online"
            else -> "offline"
        }
    }
}
