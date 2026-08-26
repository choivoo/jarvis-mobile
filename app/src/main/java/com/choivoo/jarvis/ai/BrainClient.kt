package com.choivoo.jarvis.ai

import com.choivoo.jarvis.config.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BrainClient {
    data class Turn(val user: String, val assistant: String)
    data class Reply(val speech: String, val subtitle: String)

    suspend fun chat(message: String, history: List<Turn>, contextJson: String = "{}"): Reply = withContext(Dispatchers.IO) {
        if (!JarvisConfig.cloudEnabled) {
            return@withContext Reply(
                "The cloud brain is not configured yet. Please check the Worker connection.",
                "AI Brain 설정이 아직 완료되지 않았습니다. Worker 연결 상태를 확인해 주세요."
            )
        }

        try {
            val connection = (URL("${JarvisConfig.API_BASE_URL}/v1/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Jarvis-Token", JarvisConfig.APP_TOKEN)
            }

            val historyJson = JSONArray().apply {
                history.takeLast(8).forEach { turn ->
                    put(JSONObject().put("role", "user").put("content", turn.user))
                    put(JSONObject().put("role", "assistant").put("content", turn.assistant))
                }
            }
            val contextObject = runCatching { JSONObject(contextJson) }.getOrElse { JSONObject() }
            val body = JSONObject()
                .put("message", message)
                .put("history", historyJson)
                .put("context", contextObject)
                .toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (status == 401) return@withContext Reply("JARVIS authentication failed.", "JARVIS 앱 인증에 실패했습니다. 앱 토큰 설정을 확인해 주세요.")
            if (status == 429) return@withContext Reply("The AI service is temporarily at its usage limit.", "AI Brain 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.")
            if (status !in 200..299) return@withContext Reply("The AI service returned HTTP $status.", "AI Brain 연결 오류가 발생했습니다. HTTP $status")

            val json = JSONObject(raw)
            val speech = json.optString("speech").ifBlank { "I received an empty response from the AI service." }
            val subtitle = json.optString("subtitle").ifBlank { "AI Brain에서 빈 응답을 받았습니다." }
            Reply(speech, subtitle)
        } catch (_: Exception) {
            Reply(
                "I cannot reach the AI service at the moment. Please check the network connection.",
                "AI Brain에 연결하지 못했습니다. 네트워크나 백엔드 상태를 확인해 주세요."
            )
        }
    }
}
