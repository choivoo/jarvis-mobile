package com.choivoo.jarvis.vision

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choivoo.jarvis.core.JarvisAssistantEngine
import com.choivoo.jarvis.overlay.JarvisSubtitleService
import com.choivoo.jarvis.telemetry.SystemTelemetry
import com.choivoo.jarvis.voice.VoiceController
import kotlinx.coroutines.launch

class VisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val client = remember { VisionClient() }
            val telemetry = remember { SystemTelemetry(this) }
            var status by remember { mutableStateOf("VISION CORE STANDBY") }
            var subtitle by remember { mutableStateOf("카메라로 장면을 캡처하면 JARVIS가 분석합니다.") }
            var question by remember { mutableStateOf("") }
            var previousObservation by remember { mutableStateOf("") }
            var scanCount by remember { mutableStateOf(0) }
            val voice = remember {
                VoiceController(
                    context = this,
                    enableRecognizer = false,
                    onListeningStarted = {}, onPartialText = {}, onFinalText = {}, onError = {},
                    onSpeakingStarted = {}, onSpeakingFinished = {}
                )
            }
            val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
                if (bitmap == null) {
                    status = "CAPTURE CANCELLED"
                    return@rememberLauncherForActivityResult
                }
                status = "ANALYSING"
                scope.launch {
                    val prompt = buildString {
                        append(question.ifBlank { "장면을 정확히 분석하고 유용하거나 행동 가능한 정보를 알려줘." })
                        if (previousObservation.isNotBlank()) {
                            append("\n직전 스캔 결과: ")
                            append(previousObservation.take(1800))
                            append("\n직전 장면과 비교해 달라진 점도 알려줘.")
                        }
                    }
                    val reply = client.analyze(bitmap, prompt)
                    subtitle = reply.subtitle
                    previousObservation = reply.subtitle
                    scanCount += 1
                    question = ""
                    status = "VISION COMPLETE"
                    getSharedPreferences(JarvisAssistantEngine.SPEECH_PREFS, MODE_PRIVATE).edit()
                        .putString(JarvisAssistantEngine.KEY_SPEECH, reply.speech)
                        .putString(JarvisAssistantEngine.KEY_SUBTITLE, reply.subtitle)
                        .apply()
                    JarvisSubtitleService.show(this@VisionActivity, reply.subtitle)
                    voice.speak(reply.subtitle)
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF020B12)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("J.A.R.V.I.S. · VISION CORE", color = Color(0xFF19E6F2), fontSize = 22.sp)
                        Text("$status · SESSION SCAN $scanCount", color = Color(0xFF6EA7B2), fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(subtitle, color = Color(0xFFE5FBFF), fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        val t = telemetry.snapshot()
                        Text("SYSTEM TELEMETRY", color = Color(0xFF19E6F2), fontSize = 13.sp)
                        Text(t.compact(), color = Color(0xFFB9EAF0), fontSize = 12.sp)
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it.take(240) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("VISION QUESTION") },
                            placeholder = { Text("예: 이전 장면에서 무엇이 달라졌어?") },
                            singleLine = false
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { camera.launch(null) }, modifier = Modifier.weight(1f)) { Text(if (scanCount == 0) "CAMERA SCAN" else "SCAN AGAIN") }
                            Button(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text("RETURN") }
                        }
                    }
                }
            }
        }
    }
}
