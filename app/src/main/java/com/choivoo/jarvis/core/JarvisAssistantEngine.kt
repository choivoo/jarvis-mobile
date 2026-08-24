package com.choivoo.jarvis.core

import android.content.Context
import android.os.BatteryManager
import com.choivoo.jarvis.ai.BrainClient
import com.choivoo.jarvis.calendar.JarvisCalendar
import com.choivoo.jarvis.context.JarvisContextEngine
import com.choivoo.jarvis.notifications.JarvisNotificationStore
import com.choivoo.jarvis.tools.CommandRouter
import com.choivoo.jarvis.weather.WeatherClient

class JarvisAssistantEngine(private val context: Context) {
    data class Result(val response: String, val actionPerformed: Boolean = false)

    private val router = CommandRouter(context, com.choivoo.jarvis.memory.LocalMemoryStore(context))
    private val weather = WeatherClient(context)
    private val calendar = JarvisCalendar(context)
    private val notifications = JarvisNotificationStore(context)
    private val contextEngine = JarvisContextEngine(context)
    private val brain = BrainClient()

    suspend fun process(command: String, history: List<BrainClient.Turn> = emptyList()): Result {
        val local = router.handle(command)
        if (local.handledLocally) return Result(local.response, local.actionPerformed)

        val normalized = command.lowercase()
        if (containsAny(normalized, "날씨", "기온", "비 와", "비오", "우산")) {
            if (!weather.hasLocationPermission()) {
                return Result("현재 위치의 날씨를 확인하려면 위치 권한을 허용해 주세요.")
            }
            val snapshot = weather.current().getOrElse {
                return Result("현재 위치 또는 날씨 정보를 가져오지 못했습니다. 위치 서비스를 켠 뒤 다시 시도해 주세요.")
            }
            return Result(snapshot.spokenSummary())
        }

        if (containsAny(normalized, "모닝 브리핑", "아침 브리핑", "오늘 브리핑", "오늘 요약")) {
            val battery = context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val weatherText = if (weather.hasLocationPermission()) {
                weather.current().getOrNull()?.spokenSummary() ?: "날씨 정보는 현재 가져오지 못했습니다."
            } else "날씨 확인을 위한 위치 권한이 없습니다."
            val calendarText = calendar.summary()
            val notificationText = notifications.summary(limit = 4)
            return Result("좋은 아침입니다. 배터리는 ${battery}%입니다. $weatherText $calendarText $notificationText")
        }

        val liveContext = contextEngine.snapshot(includeWeather = true)
        return Result(brain.chat(command, history, liveContext))
    }

    private fun containsAny(input: String, vararg values: String): Boolean = values.any { input.contains(it) }
}
