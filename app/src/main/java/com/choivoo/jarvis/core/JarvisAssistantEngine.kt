package com.choivoo.jarvis.core

import android.content.Context
import android.os.BatteryManager
import com.choivoo.jarvis.ai.BrainClient
import com.choivoo.jarvis.calendar.JarvisCalendar
import com.choivoo.jarvis.context.JarvisContextEngine
import com.choivoo.jarvis.notifications.JarvisNotificationStore
import com.choivoo.jarvis.overlay.JarvisSubtitleService
import com.choivoo.jarvis.tasks.JarvisTaskStore
import com.choivoo.jarvis.tools.CommandRouter
import com.choivoo.jarvis.voice.BritishSpeech
import com.choivoo.jarvis.weather.WeatherClient
import java.util.Calendar

class JarvisAssistantEngine(private val context: Context) {
    companion object {
        const val SPEECH_PREFS = "jarvis_bilingual_output"
        const val KEY_SPEECH = "speech_en_gb"
        const val KEY_SUBTITLE = "subtitle_ko"
    }

    data class Result(
        val response: String,
        val speech: String = response,
        val actionPerformed: Boolean = false
    )

    private val router = CommandRouter(context, com.choivoo.jarvis.memory.LocalMemoryStore(context))
    private val weather = WeatherClient(context)
    private val calendar = JarvisCalendar(context)
    private val notifications = JarvisNotificationStore(context)
    private val contextEngine = JarvisContextEngine(context)
    private val tasks = JarvisTaskStore(context)
    private val brain = BrainClient()

    suspend fun process(command: String, history: List<BrainClient.Turn> = emptyList()): Result {
        val result = processInternal(command, history)
        publish(result)
        return result
    }

    private suspend fun processInternal(command: String, history: List<BrainClient.Turn>): Result {
        val local = router.handle(command)
        if (local.handledLocally) {
            return Result(local.response, BritishSpeech.fromKorean(local.response, command), local.actionPerformed)
        }

        val normalized = command.lowercase().trim()
        handleTaskCommand(command, normalized)?.let { return localised(it, command) }
        handleCalendarCreate(command, normalized)?.let { return localised(it, command) }

        if (containsAny(normalized, "일정 알려", "오늘 일정", "캘린더", "스케줄")) {
            val subtitle = calendar.summary()
            return Result(subtitle, BritishSpeech.fromKorean(subtitle, command))
        }

        if (containsAny(normalized, "날씨", "기온", "비 와", "비오", "우산")) {
            if (!weather.hasLocationPermission()) {
                val subtitle = "현재 위치의 날씨를 확인하려면 위치 권한을 허용해 주세요."
                return Result(subtitle, BritishSpeech.fromKorean(subtitle, command))
            }
            val snapshot = weather.current().getOrElse {
                val subtitle = "현재 위치 또는 날씨 정보를 가져오지 못했습니다. 위치 서비스를 확인해 주세요."
                return Result(subtitle, BritishSpeech.fromKorean(subtitle, command))
            }
            val subtitle = snapshot.spokenSummary()
            return Result(subtitle, BritishSpeech.fromKorean(subtitle, command))
        }

        if (containsAny(normalized, "모닝 브리핑", "아침 브리핑", "오늘 브리핑", "오늘 요약")) {
            val battery = context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val weatherText = if (weather.hasLocationPermission()) {
                weather.current().getOrNull()?.spokenSummary() ?: "날씨 정보는 현재 가져오지 못했습니다."
            } else "날씨 확인을 위한 위치 권한이 없습니다."
            val calendarText = calendar.summary()
            val taskText = tasks.summary()
            val notificationText = notifications.summary(limit = 4)
            val subtitle = "좋은 아침입니다. 배터리는 ${battery}%입니다. $weatherText $calendarText $taskText $notificationText"
            val speech = "Good morning. The battery is at $battery per cent. I've prepared your full weather, calendar and task briefing in the Korean subtitle."
            return Result(subtitle, speech)
        }

        val liveContext = buildString {
            append(contextEngine.snapshot(includeWeather = true))
            append("\nTasks: ")
            append(tasks.summary())
        }
        val reply = brain.chat(command, history, liveContext)
        return Result(reply.subtitle, reply.speech)
    }

    private fun publish(result: Result) {
        context.getSharedPreferences(SPEECH_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SPEECH, result.speech)
            .putString(KEY_SUBTITLE, result.response)
            .apply()
        JarvisSubtitleService.show(context, result.response)
    }

    private fun localised(result: Result, command: String): Result =
        result.copy(speech = BritishSpeech.fromKorean(result.response, command))

    private fun handleTaskCommand(original: String, normalized: String): Result? {
        if (containsAny(normalized, "할 일 보여", "할일 보여", "해야 할 일", "할 일 목록", "할일 목록")) return Result(tasks.summary())
        if (containsAny(normalized, "할 일 추가", "할일 추가", "할 일에", "할일에")) {
            val title = original
                .replace("할 일에", "").replace("할일에", "")
                .replace("할 일 추가", "").replace("할일 추가", "")
                .replace("추가해 주세요", "").replace("추가해주세요", "").replace("추가해줘", "")
                .trim(' ', '.', ',')
            if (title.isBlank()) return Result("추가할 할 일을 말씀해 주세요.")
            tasks.add(title)
            return Result("할 일에 '$title' 항목을 추가했습니다.", actionPerformed = true)
        }
        if (containsAny(normalized, "할 일 완료", "할일 완료", "완료 처리")) {
            val number = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val completed = tasks.completeByIndex(number - 1) ?: return Result("완료 처리할 할 일을 찾지 못했습니다.")
            return Result("'${completed.title}' 항목을 완료 처리했습니다.", actionPerformed = true)
        }
        return null
    }

    private fun handleCalendarCreate(original: String, normalized: String): Result? {
        if (!containsAny(normalized, "일정 추가", "일정 만들어", "캘린더 추가", "스케줄 추가")) return null
        val hour = Regex("(오전|오후)?\\s*(\\d{1,2})\\s*시").find(normalized)
        val hourValue = hour?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: return Result("일정을 추가하려면 시간을 함께 말씀해 주세요. 예: '내일 오후 3시에 학원 일정 추가해 주세요'.")
        val meridiem = hour.groupValues.getOrNull(1).orEmpty()
        var resolvedHour = hourValue.coerceIn(0, 23)
        if (meridiem == "오후" && resolvedHour < 12) resolvedHour += 12
        if (meridiem == "오전" && resolvedHour == 12) resolvedHour = 0
        val minute = Regex("(\\d{1,2})\\s*분").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance().apply {
            if (normalized.contains("내일")) add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, resolvedHour)
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!normalized.contains("내일") && timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val title = original
            .replace("내일", "")
            .replace(Regex("(오전|오후)?\\s*\\d{1,2}\\s*시(\\s*\\d{1,2}\\s*분)?에?"), "")
            .replace("일정 추가해 주세요", "").replace("일정 추가해주세요", "").replace("일정 추가해줘", "")
            .replace("일정 추가", "").replace("일정 만들어 주세요", "").replace("일정 만들어줘", "")
            .trim(' ', '.', ',')
            .ifBlank { "JARVIS 일정" }
        return try {
            context.startActivity(calendar.createEventIntent(title, cal.timeInMillis))
            Result("캘린더에 '$title' 일정을 추가할 준비를 했습니다. 화면에서 저장을 확인해 주세요.", actionPerformed = true)
        } catch (_: Exception) {
            Result("캘린더 앱을 열 수 없습니다.")
        }
    }

    private fun containsAny(input: String, vararg values: String): Boolean = values.any { input.contains(it) }
}
