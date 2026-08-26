package com.choivoo.jarvis.voice

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converts local Korean tool results into concise British-English speech.
 * The original Korean string remains the on-screen/overlay subtitle.
 */
object BritishSpeech {
    fun fromKorean(subtitle: String, command: String = ""): String {
        val text = subtitle.trim()
        val lower = command.lowercase()

        Regex("배터리는\\s*(\\d+)%").find(text)?.groupValues?.getOrNull(1)?.let {
            return "The battery is at $it per cent."
        }
        if (text.contains("현재 시간")) {
            return "The time is ${LocalTime.now().format(DateTimeFormatter.ofPattern("h mm a", Locale.UK))}."
        }
        if (text.startsWith("오늘은")) {
            return "Today is ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.UK))}."
        }
        if (text.contains("유튜브를 열었습니다")) return "YouTube is open."
        if (text.contains("브라우저를 열었습니다")) return "The browser is open."
        if (text.contains("카메라를 열었습니다")) return "The camera is open."
        if (text.contains("설정을 열었습니다")) return "Settings are open."
        if (text.contains("검색 화면을 열었습니다")) return "I've opened the search results."
        if (text.contains("타이머를 시작했습니다")) return "The timer is running."
        if (text.contains("기억했습니다")) return "Understood. I've stored that in memory."
        if (text.contains("볼륨을 올렸습니다")) return "I've raised the media volume."
        if (text.contains("볼륨을 낮췄습니다")) return "I've lowered the media volume."
        if (text.contains("등록된 자동화가 없습니다")) return "There are no active automations."
        if (text.contains("자동화를 만들었습니다")) return "The automation has been created."
        if (text.contains("앞으로 24시간 안에 등록된 일정이 없습니다")) return "There are no calendar events in the next twenty-four hours."
        if (text.contains("일정을 추가할 준비")) return "I've prepared the calendar event. Please confirm it on screen."
        if (text.contains("할 일을 추가했습니다") || text.contains("항목을 추가했습니다")) return "I've added that to your task list."
        if (text.contains("완료 처리했습니다")) return "That task is marked as complete."
        if (text.contains("할 일이 없습니다")) return "There are no pending tasks."
        if (text.contains("위치 권한")) return "Location permission is required for that request."
        if (text.contains("날씨 정보를") && text.contains("못했습니다")) return "I cannot retrieve the weather just now."
        if (lower.contains("날씨") || lower.contains("기온") || lower.contains("비") || lower.contains("우산")) {
            val temperature = Regex("(-?\\d+(?:\\.\\d+)?)\\s*도").find(text)?.groupValues?.getOrNull(1)
            return if (temperature != null) "The current temperature is $temperature degrees Celsius. The full forecast is shown in the Korean subtitle."
            else "I've put the current forecast in the Korean subtitle."
        }
        if (lower.contains("일정") || lower.contains("캘린더") || lower.contains("스케줄")) {
            return "Your current schedule is shown in the Korean subtitle."
        }
        if (lower.contains("할 일") || lower.contains("할일")) return "Your task details are shown in the Korean subtitle."
        if (lower.contains("모닝 브리핑") || lower.contains("아침 브리핑")) return "Your morning briefing is ready. I've put the full details in the Korean subtitle."
        if (text.contains("잘 듣지 못했습니다")) return "I didn't quite catch that. Please say it again."
        if (text.contains("중지하겠습니다")) return "Certainly. Stopping now."

        return "Done. I've put the full details in the Korean subtitle."
    }
}
