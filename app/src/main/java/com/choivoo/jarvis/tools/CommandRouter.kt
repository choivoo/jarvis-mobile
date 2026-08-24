package com.choivoo.jarvis.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.choivoo.jarvis.memory.LocalMemoryStore
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

    fun handle(rawInput: String): Result {
        val input = rawInput.trim()
        val normalized = input.lowercase()

        if (input.isBlank()) return Result("잘 듣지 못했습니다. 다시 말씀해 주세요.")

        if (normalized == "취소" || normalized == "그만") {
            return Result("알겠습니다. 중단하겠습니다.")
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

        if (containsAny(normalized, "유튜브 켜", "유튜브 열어", "youtube 켜")) {
            return openPackageOrWeb("com.google.android.youtube", "https://www.youtube.com", "유튜브를 열겠습니다.")
        }

        if (containsAny(normalized, "크롬 켜", "크롬 열어", "브라우저 켜")) {
            return openPackageOrWeb("com.android.chrome", "https://www.google.com", "브라우저를 열겠습니다.")
        }

        if (containsAny(normalized, "카메라 켜", "카메라 열어")) {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "카메라를 열겠습니다.", "카메라를 열 수 없습니다.")
        }

        if (containsAny(normalized, "설정 켜", "설정 열어")) {
            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "설정을 열겠습니다.", "설정을 열 수 없습니다.")
        }

        if (normalized.startsWith("검색 ") || normalized.contains("검색해")) {
            val query = input
                .removePrefix("검색 ")
                .replace("검색해줘", "")
                .replace("검색해", "")
                .trim()
            if (query.isBlank()) return Result("검색할 내용을 말씀해 주세요.")
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(intent, "$query 검색을 열겠습니다.", "검색 화면을 열 수 없습니다.")
        }

        if (containsAny(normalized, "타이머", "분 뒤 알려", "초 뒤 알려", "시간 뒤 알려")) {
            val seconds = parseDurationSeconds(normalized)
            if (seconds != null && seconds > 0) {
                val label = input.take(40)
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch(intent, "${formatDuration(seconds)} 타이머를 시작하겠습니다.", "이 기기에서 타이머를 바로 만들 수 없습니다.")
            }
            return Result("타이머 시간을 확인하지 못했습니다. 예를 들어 '10분 타이머'라고 말씀해 주세요.")
        }

        if (normalized.startsWith("기억해") || normalized.endsWith("기억해")) {
            val content = input
                .removePrefix("기억해")
                .removeSuffix("기억해")
                .trim(' ', '.', ',')
            if (content.isBlank()) return Result("기억할 내용을 말씀해 주세요.")
            memoryStore.save(content)
            return Result("기억해 두겠습니다. '$content'", true)
        }

        if (containsAny(normalized, "기억 보여", "뭘 기억", "기억한 거", "메모리 보여")) {
            val memories = memoryStore.getAll().take(5)
            return if (memories.isEmpty()) {
                Result("아직 저장된 기억이 없습니다.")
            } else {
                Result("최근 기억은 ${memories.joinToString(". ")} 입니다.")
            }
        }

        if (containsAny(normalized, "볼륨 올려", "소리 키워")) {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return Result("미디어 볼륨을 올리겠습니다.", true)
        }

        if (containsAny(normalized, "볼륨 내려", "소리 줄여")) {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return Result("미디어 볼륨을 낮추겠습니다.", true)
        }

        return Result(response = "", handledLocally = false)
    }

    private fun parseDurationSeconds(input: String): Int? {
        val hour = Regex("(\\d+)\\s*시간").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minute = Regex("(\\d+)\\s*분").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val second = Regex("(\\d+)\\s*초").find(input)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val total = hour * 3600 + minute * 60 + second
        return total.takeIf { it > 0 }
    }

    private fun formatDuration(seconds: Int): String {
        return when {
            seconds % 3600 == 0 -> "${seconds / 3600}시간"
            seconds % 60 == 0 -> "${seconds / 60}분"
            else -> "${seconds}초"
        }
    }

    private fun openPackageOrWeb(packageName: String, fallbackUrl: String, success: String): Result {
        val packageIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (packageIntent != null) {
            packageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(packageIntent, success, "앱을 열 수 없습니다.")
        }
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(fallback, success, "앱이나 웹페이지를 열 수 없습니다.")
    }

    private fun launch(intent: Intent, success: String, failure: String): Result {
        return try {
            context.startActivity(intent)
            Result(success, true)
        } catch (_: Exception) {
            Result(failure)
        }
    }

    private fun containsAny(input: String, vararg values: String): Boolean {
        return values.any { input.contains(it) }
    }
}
