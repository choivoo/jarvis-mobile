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

    suspend fun chat(message: String, history: List<Turn>): String = withContext(Dispatchers.IO) {
        if (!JarvisConfig.cloudEnabled) {
            return@withContext "AI Brain 서버가 아직 연결되지 않았어. V0.6 백엔드를 연결하면 자유 대화를 시작할 수 있어."
        }

        try {
            val connection = (URL("${JarvisConfig.API_BASE_URL.trimEnd('/')}/v1/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val historyJson = JSONArray().apply {
                history.takeLast(6).forEach { turn ->
                    put(JSONObject().put("role", "user").put("content", turn.user))
                    put(JSONObject().put("role", "assistant").put("content", turn.assistant))
                }
            }
            val body = JSONObject()
                .put("message", message)
                .put("history", historyJson)
                .toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (status !in 200..299) {
                return@withContext "AI Brain 연결 오류가 발생했어. HTTP $status"
            }

            JSONObject(raw).optString("reply").ifBlank {
                "AI Brain에서 빈 응답이 왔어."
            }
        } catch (_: Exception) {
            "AI Brain에 연결하지 못했어. 인터넷이나 백엔드 상태를 확인해줘."
        }
    }
}
