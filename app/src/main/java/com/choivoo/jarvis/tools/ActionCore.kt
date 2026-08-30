package com.choivoo.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings

/**
 * Allow-listed device actions for JARVIS Omni Core.
 *
 * This layer deliberately exposes only reversible actions or Android confirmation
 * screens. The cloud brain never receives a Context and cannot execute an Intent.
 */
class ActionCore(private val context: Context) {
    data class Result(val subtitle: String, val actionPerformed: Boolean = true)

    fun handle(rawInput: String): Result? {
        val input = rawInput.trim()
        val normal = input.lowercase()

        parseNavigation(input, normal)?.let { return it }
        parseAlarm(input, normal)?.let { return it }
        parseShare(input, normal)?.let { return it }

        return when {
            containsAny(normal, "와이파이 설정", "wi-fi 설정", "wifi 설정") ->
                open(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi 설정을 열었습니다.")
            containsAny(normal, "블루투스 설정", "bluetooth 설정") ->
                open(Settings.ACTION_BLUETOOTH_SETTINGS, "블루투스 설정을 열었습니다.")
            containsAny(normal, "화면 설정", "디스플레이 설정", "밝기 설정") ->
                open(Settings.ACTION_DISPLAY_SETTINGS, "화면 설정을 열었습니다.")
            containsAny(normal, "소리 설정", "사운드 설정") ->
                open(Settings.ACTION_SOUND_SETTINGS, "소리 설정을 열었습니다.")
            else -> null
        }
    }

    private fun parseNavigation(input: String, normal: String): Result? {
        if (!containsAny(normal, "길 안내", "길안내", "내비", "지도에서", "가는 길")) return null
        val destination = input
            .replace(Regex("(?i)(까지|으로|로)?\\s*(길\\s*안내|길안내|내비게이션|내비|지도에서|가는 길)(해\\s*줘|해줘|해 주세요|해주세요|열어)?"), "")
            .trim(' ', ',', '.')
        if (destination.isBlank()) return Result("목적지를 함께 말씀해 주세요.", false)
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val fallback = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        return launchWithFallback(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, fallback),
            "$destination 길 안내를 열었습니다."
        )
    }

    private fun parseAlarm(input: String, normal: String): Result? {
        if (!containsAny(normal, "알람", "깨워")) return null
        val match = Regex("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?").find(normal)
            ?: return Result("알람 시간을 함께 말씀해 주세요. 예: '오전 7시 알람'.", false)
        var hour = match.groupValues[2].toIntOrNull() ?: return Result("알람 시간을 확인하지 못했습니다.", false)
        val minute = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        when (match.groupValues[1]) {
            "오후" -> if (hour in 1..11) hour += 12
            "오전" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return Result("올바른 알람 시간을 말씀해 주세요.", false)
        val label = input.replace(match.value, "").replace("알람", "").replace("깨워", "").trim().ifBlank { "JARVIS" }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.take(40))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        return launch(intent, "%02d시 %02d분 알람 설정 화면을 열었습니다.".format(hour, minute))
    }

    private fun parseShare(input: String, normal: String): Result? {
        if (!containsAny(normal, "공유해", "공유 해", "공유하기")) return null
        val text = input
            .replace(Regex("(이걸|이것을|내용을)?\\s*(공유해\\s*줘|공유해줘|공유해 주세요|공유해주세요|공유하기)"), "")
            .trim(' ', ',', '.')
        if (text.isBlank()) return Result("공유할 내용을 함께 말씀해 주세요.", false)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        return launch(Intent.createChooser(send, "JARVIS로 공유"), "공유할 앱을 선택해 주세요.")
    }

    private fun open(action: String, message: String): Result = launch(Intent(action), message)

    private fun launch(intent: Intent, success: String): Result = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result(success)
    } catch (_: Exception) {
        Result("이 기기에서는 해당 화면을 열 수 없습니다.", false)
    }

    private fun launchWithFallback(primary: Intent, fallback: Intent, success: String): Result = try {
        context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result(success)
    } catch (_: Exception) {
        launch(fallback, success)
    }

    private fun containsAny(input: String, vararg values: String): Boolean = values.any(input::contains)
}
