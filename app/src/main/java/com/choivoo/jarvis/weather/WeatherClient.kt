package com.choivoo.jarvis.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherClient(private val context: Context) {
    data class WeatherSnapshot(
        val latitude: Double,
        val longitude: Double,
        val temperature: Double,
        val feelsLike: Double,
        val precipitation: Double,
        val windSpeed: Double,
        val precipitationProbability: Int?
    ) {
        fun spokenSummary(): String {
            val rain = precipitationProbability?.let { " 강수 확률은 약 ${it}%입니다." }.orEmpty()
            return "현재 기온은 ${temperature.toInt()}도, 체감 온도는 ${feelsLike.toInt()}도입니다. " +
                "현재 강수량은 ${String.format("%.1f", precipitation)}밀리미터이고 바람은 시속 ${windSpeed.toInt()}킬로미터입니다.$rain"
        }
    }

    suspend fun current(): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        val location = bestLastKnownLocation()
            ?: return@withContext Result.failure(IllegalStateException("location_unavailable"))

        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${location.latitude}&longitude=${location.longitude}" +
                    "&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m" +
                    "&hourly=precipitation_probability&forecast_days=1&timezone=auto"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("weather_http_$status")
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val root = JSONObject(raw)
            val current = root.getJSONObject("current")
            val hourly = root.optJSONObject("hourly")
            val rainProbability = hourly?.optJSONArray("precipitation_probability")?.let { array ->
                if (array.length() > 0) array.optInt(0) else null
            }

            WeatherSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                temperature = current.optDouble("temperature_2m"),
                feelsLike = current.optDouble("apparent_temperature"),
                precipitation = current.optDouble("precipitation"),
                windSpeed = current.optDouble("wind_speed_10m"),
                precipitationProbability = rainProbability
            )
        }
    }

    fun hasLocationPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun bestLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
}
