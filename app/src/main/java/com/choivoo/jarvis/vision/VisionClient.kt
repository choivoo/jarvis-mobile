package com.choivoo.jarvis.vision

import android.graphics.Bitmap
import android.util.Base64
import com.choivoo.jarvis.config.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class VisionClient {
    data class Reply(val speech: String, val subtitle: String)

    suspend fun analyze(bitmap: Bitmap, prompt: String = "Describe what you see and highlight anything useful or actionable."): Reply = withContext(Dispatchers.IO) {
        if (!JarvisConfig.cloudEnabled) {
            return@withContext Reply(
                "Cloud vision is not configured on this build.",
                "Cloud Vision 연결이 아직 구성되지 않았습니다. 사진 캡처 자체는 정상입니다."
            )
        }
        runCatching {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val connection = (URL("${JarvisConfig.API_BASE_URL}/v1/vision").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Jarvis-Token", JarvisConfig.APP_TOKEN)
            }
            val body = JSONObject()
                .put("prompt", prompt)
                .put("image_base64", b64)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) return@runCatching Reply("Vision analysis failed with HTTP $status.", "Vision 분석 연결 오류가 발생했습니다. HTTP $status")
            val json = JSONObject(raw)
            Reply(json.optString("speech").ifBlank { "Vision analysis is complete." }, json.optString("subtitle").ifBlank { "Vision 분석이 완료되었습니다." })
        }.getOrElse {
            Reply("I could not analyse that image just now.", "이미지를 분석하지 못했습니다. 네트워크 상태를 확인해 주세요.")
        }
    }
}
