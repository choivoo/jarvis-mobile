package com.choivoo.jarvis.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.choivoo.jarvis.automation.JarvisAutomationScheduler
import com.choivoo.jarvis.automation.JarvisAutomationStore
import com.choivoo.jarvis.calendar.JarvisCalendar
import com.choivoo.jarvis.memory.LocalMemoryStore
import com.choivoo.jarvis.notifications.JarvisNotificationStore
import com.choivoo.jarvis.voice.VoicePreferences
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CommandRouter(
    private val context: Context,
    private val memoryStore: LocalMemoryStore
) {
    data class Result(
        val response: String,
        val actionPerformed: Boolean = false,
        val handledLocally: Boolean = true
    )

    private val voicePreferences = VoicePreferences(context)
    private val calendar = JarvisCalendar(context)
    private val notifications = JarvisNotificationStore(context)
    private val automationStore = JarvisAutomationStore(context)
    private val automationScheduler = JarvisAutomationScheduler(context)
    private val actionCore = ActionCore(context)

    fun handle(rawInput: String): Result {
        val input = rawInput.trim()
        val normalized = input.lowercase()
        if (input.isBlank()) return Result("잘 듣지 못했습니다. 다시 말씀해 주세요.")
        if (normalized == "취소" || normalized == "그만") return Result("알겠습니다. 중지하겠습니다.")

        actionCore.handle(input)?.let { action ->
            return Result(action.subtitle, action.actionPerformed)
        }

        if (containsAny(normalized, "현재 목소리", "지금 목소리", "음성 설정")) {
            return Result("현재 Cinematic Voice는 ${voicePreferences.getVoice()}입니다.")
        }
        if (containsAny(normalized, "목소리", "보이스", "voice")) {
            val target = VoicePreferences.ALLOWED.firstOrNull { normalized.contains(it) }
            if (target != null) {
                voicePreferences.setVoice(target)
                return Result("Cinematic Voice를 $target 음성으로 변경했습니다.", true)
            }
            if (containsAny(normalized, "테스트", "비교", "목록", "뭐 있어")) {
                return Result("Voice Lab에서는 marin, cedar, onyx, echo 음성을 사용할 수 있습니다.")
            }
        }

        if (containsAny(normalized, "몇 시", "현재 시간", "지금 시간", "시간 알려")) {
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("a h시 mm분"))
            return Result("현재 시간은 $time 입니다.")
        }
        if (containsAny(normalized, "오늘 날짜", "오늘 며칠", "날짜 알려")) {
            val today = LocalDate.now()
            return Result("오늘은 ${today.year}년 ${today.monthValue}월 ${today.dayOfMonth}일입니다.")
        }
        if (containsAny(normalized, "배터리", "배터리 몇")) {
            val battery = context.getSystemService(BatteryManager::class.java)
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return Result("현재 배터리는 ${level}%입니다.")
        }

        if (containsAny(normalized, "오늘 일정", "일정 알려", "일정 보여", "다음 일정")) {
            return Result(calendar.summary())
        }

        if (containsAny(normalized, "알림 요약", "알림 정리", "최근 알림", "알림 읽어")) {
            return Result(notifications.summary())
        }
        if (containsAny(normalized, "알림 접근 설정", "알림 권한 설정")) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "알림 접근 설정 화면을 열었습니다.", "알림 접근 설정을 열 수 없습니다.")
        }

        if (containsAny(normalized, "자동화 보여", "자동화 목록", "루틴 보여")) {
            val items = automationStore.all().filter { it.enabled }
            if (items.isEmpty()) return Result("현재 등록된 자동화가 없습니다.")
            return Result("등록된 자동화는 " + items.take(6).joinToString(". ") {
                "매일 ${it.hour}시 ${it.minute}분, ${it.message}"
            } + "입니다.")
        }

        parseDailyAutomation(input, normalized)?.let { parsed ->
            val item = automationScheduler.createDaily(parsed.first, parsed.second, parsed.third)
            return Result("매일 ${item.hour}시 ${item.minute}분에 '${item.message}' 알림을 드리도록 자동화를 만들었습니다.", true)
        }

        if (containsAny(normalized, "유튜브 켜", "유튜브 열어", "youtube 켜")) {
            return openPackageOrWeb("com.google.android.youtube", "https://www.youtube.com", "유튜브를 열었습니다.")
        }
        if (containsAny(normalized, "크롬 켜", "크롬 열어", "브라우저 켜")) {
            return openPackageOrWeb("com.android.chrome", "https://www.google.com", "브라우저를 열었습니다.")
        }
        if (containsAny(normalized, "카메라 켜", "카메라 열어")) {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "카메라를 열었습니다.", "카메라를 열 수 없습니다.")
        }
        if (containsAny(normalized, "설정 켜", "설정 열어")) {
            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "설정을 열었습니다.", "설정을 열 수 없습니다.")
        }

        if (normalized.startsWith("검색 ") || normalized.contains("검색해")) {
            val query = input.removePrefix("검색 ")
                .replace("검색해 주세요", "").replace("검색해주세요", "")
                .replace("검색해줘", "").replace("검색해", "").trim()
            if (query.isBlank()) return Result("무엇을 검색할지 말씀해 주세요.")
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            return launch(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "$query 검색 화면을 열었습니다.", "검색 화면을 열 수 없습니다.")
        }

        if (containsAny(normalized, "타이머", "분 뒤 알려", "초 뒤 알려", "시간 뒤 알려")) {
            val seconds = parseDurationSeconds(normalized)
            if (seconds != null && seconds > 0) {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, input.take(40))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch(intent, "${formatDuration(seconds)} 타이머를 시작했습니다.", "이 기기에서 타이머를 바로 만들 수 없습니다.")
            }
            return Result("타이머 시간을 찾지 못했습니다. 예를 들어 '10분 타이머'라고 말씀해 주세요.")
        }

        if (normalized.startsWith("기억해") || normalized.endsWith("기억해") || normalized.endsWith("기억해 주세요") || normalized.endsWith("기억해주세요")) {
            val content = input.removePrefix("기억해")
                .removeSuffix("기억해 주세요").removeSuffix("기억해주세요").removeSuffix("기억해")
                .trim(' ', '.', ',')
            if (content.isBlank()) return Result("무엇을 기억할지 말씀해 주세요.")
            memoryStore.save(content)
            return Result("기억했습니다. '$content'", true)
        }
        if (containsAny(normalized, "기억 보여", "뭘 기억", "기억한 거", "메모리 보여")) {
            val memories = memoryStore.getAll().take(5)
            return if (memories.isEmpty()) Result("아직 저장된 기억이 없습니다.")
            else Result("최근 기억은 ${memories.joinToString(". ")} 입니다.")
        }

        if (containsAny(normalized, "볼륨 올려", "소리 키워")) {
            context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return Result("미디어 볼륨을 올렸습니다.", true)
        }
        if (containsAny(normalized, "볼륨 내려", "소리 줄여")) {
            context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return Result("미디어 볼륨을 낮췄습니다.", true)
        }

        return Result(response = "", handledLocally = false)
    }

    private fun parseDailyAutomation(input: String, normalized: String): Triple<Int, Int, String>? {
        if (!containsAny(normalized, "매일", "매일 아침", "매일 저녁")) return null
        if (!containsAny(normalized, "알려", "알림", "리마인드", "깨워")) return null
        val match = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?").find(normalized) ?: return null
        val ampm = match.groupValues.getOrNull(1).orEmpty()
        var hour = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        if (ampm == "오후" && hour in 1..11) hour += 12
        if (ampm == "오전" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val message = input.replace(match.value, "")
            .replace("매일", "").replace("알려 주세요", "").replace("알려줘", "")
            .replace("알림", "").trim(' ', ',', '.', '을', '를')
            .ifBlank { "예약된 시간입니다." }
        return Triple(hour, minute, message)
    }

    private fun parseDurationSeconds(input: String): Int? {
        val hour = Regex("(\\d+)\\s*시간").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minute = Regex("(\\d+)\\s*분").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val second = Regex("(\\d+)\\s*초").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 3600 + minute * 60 + second).takeIf { it > 0 }
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds % 3600 == 0 -> "${seconds / 3600}시간"
        seconds % 60 == 0 -> "${seconds / 60}분"
        else -> "${seconds}초"
    }
    private fun openPackageOrWeb(packageName: String, fallbackUrl: String, success: String): Result {
        val packageIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (packageIntent != null) {
            packageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(packageIntent, success, "앱을 열 수 없습니다.")
        }
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), success, "앱이나 웹페이지를 열 수 없습니다.")
    }
    private fun launch(intent: Intent, success: String, failure: String): Result = try {
        context.startActivity(intent); Result(success, true)
    } catch (_: Exception) { Result(failure) }
    private fun containsAny(input: String, vararg values: String): Boolean = values.any { input.contains(it) }
}
