package com.choivoo.jarvis.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/** V2.5 allow-listed app, media and communication actions. */
class CommunicationCore(private val context: Context) {
    data class Result(val subtitle: String, val actionPerformed: Boolean = true)

    private val packageAliases = linkedMapOf(
        "유튜브 뮤직" to "com.google.android.apps.youtube.music",
        "유튜브" to "com.google.android.youtube",
        "스포티파이" to "com.spotify.music",
        "카카오톡" to "com.kakao.talk",
        "카톡" to "com.kakao.talk",
        "크롬" to "com.android.chrome",
        "지도" to "com.google.android.apps.maps",
        "구글 지도" to "com.google.android.apps.maps",
        "갤러리" to "com.sec.android.gallery3d",
        "삼성 뮤직" to "com.sec.android.app.music",
        "계산기" to "com.sec.android.app.popupcalculator"
    )

    fun handle(rawInput: String): Result? {
        val input = rawInput.trim()
        val normal = input.lowercase()
        parsePhone(input, normal)?.let { return it }
        parseSms(input, normal)?.let { return it }
        parseMusic(input, normal)?.let { return it }
        parseAppLaunch(input, normal)?.let { return it }
        return null
    }

    private fun parsePhone(input: String, normal: String): Result? {
        if (!containsAny(normal, "전화 걸어", "전화걸어", "전화해", "통화해")) return null
        val number = extractPhoneNumber(input)
            ?: return Result("전화번호를 함께 말씀해 주세요. 전화는 다이얼 화면에서 마지막으로 확인해야 연결됩니다.", false)
        return launch(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")),
            "$number 번호를 다이얼 화면에 준비했습니다. 통화 버튼을 눌러 확인해 주세요."
        )
    }

    private fun parseSms(input: String, normal: String): Result? {
        if (!containsAny(normal, "문자 보내", "문자보내", "메시지 보내", "메세지 보내")) return null
        val number = extractPhoneNumber(input)
            ?: return Result("받는 사람의 전화번호를 함께 말씀해 주세요.", false)
        val body = input
            .replace(number, "")
            .replace(Regex("(에게|한테)?\\s*(문자|메시지|메세지)\\s*(보내\\s*줘|보내줘|보내 주세요|보내주세요|보내)?"), "")
            .trim(' ', ',', '.', ':')
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", body)
        val message = if (body.isBlank()) "$number 번호의 문자 작성 화면을 열었습니다."
        else "$number 번호에 보낼 문자를 작성했습니다. 내용을 확인한 뒤 전송 버튼을 눌러 주세요."
        return launch(intent, message)
    }

    private fun parseMusic(input: String, normal: String): Result? {
        if (containsAny(normal, "음악 멈춰", "노래 멈춰", "일시정지", "음악 정지")) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return Result("음악을 일시정지했습니다.")
        }
        if (containsAny(normal, "다음 곡", "다음곡", "다음 노래")) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            return Result("다음 곡으로 넘겼습니다.")
        }
        if (containsAny(normal, "이전 곡", "이전곡", "이전 노래")) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return Result("이전 곡으로 이동했습니다.")
        }
        if (!containsAny(normal, "음악 틀어", "노래 틀어", "재생해", "음악 재생")) return null
        val query = input
            .replace(Regex("(음악|노래)?\\s*(틀어\\s*줘|틀어줘|틀어 주세요|틀어주세요|재생해\\s*줘|재생해줘|재생해 주세요|재생해주세요|재생해)"), "")
            .trim(' ', ',', '.')
        if (query.isBlank()) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return Result("마지막 음악을 다시 재생했습니다.")
        }
        val search = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
        return launch(Intent(Intent.ACTION_VIEW, search), "$query 음악 검색 결과를 열었습니다. 곡을 확인해 선택해 주세요.")
    }

    private fun parseAppLaunch(input: String, normal: String): Result? {
        if (!containsAny(normal, " 켜", " 열어", "실행해")) return null
        val requested = input
            .replace(Regex("(앱|어플)?\\s*(켜\\s*줘|켜줘|켜 주세요|켜주세요|켜|열어\\s*줘|열어줘|열어 주세요|열어주세요|열어|실행해\\s*줘|실행해줘|실행해 주세요|실행해주세요|실행해)"), "")
            .trim(' ', ',', '.')
        if (requested.isBlank()) return Result("실행할 앱 이름을 말씀해 주세요.", false)
        val aliasPackage = packageAliases.entries.firstOrNull { requested.lowercase().contains(it.key) }?.value
        val packageName = aliasPackage ?: findLauncherPackage(requested)
            ?: return Result("'$requested' 앱을 설치된 실행 가능 앱에서 찾지 못했습니다.", false)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result("'$requested' 앱의 실행 화면을 찾지 못했습니다.", false)
        return launch(launchIntent, "$requested 앱을 실행했습니다.")
    }

    private fun findLauncherPackage(requested: String): String? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(query, 0)
            .firstOrNull { info ->
                info.loadLabel(context.packageManager).toString().contains(requested, ignoreCase = true)
            }?.activityInfo?.packageName
    }

    private fun extractPhoneNumber(input: String): String? =
        Regex("(?:\\+?82[- ]?)?0?1[016789](?:[- ]?\\d{3,4}){2}")
            .find(input)?.value?.replace(" ", "")?.replace("-", "")

    private fun dispatchMediaKey(keyCode: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun launch(intent: Intent, success: String): Result = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result(success)
    } catch (_: Exception) {
        Result("이 기기에서 해당 앱이나 확인 화면을 열 수 없습니다.", false)
    }

    private fun containsAny(input: String, vararg values: String): Boolean = values.any(input::contains)
}
