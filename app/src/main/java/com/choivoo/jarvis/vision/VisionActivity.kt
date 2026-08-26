package com.choivoo.jarvis.vision

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            var speech by remember { mutableStateOf("Vision core standing by.") }
            val voice = remember {
                VoiceController(
                    context = this,
                    enableRecognition = false,
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
                    val reply = client.analyze(bitmap)
                    speech = reply.speech
                    subtitle = reply.subtitle
                    status = "VISION COMPLETE"
                    JarvisSubtitleService.show(this@VisionActivity, subtitle)
                    voice.speak(subtitle)
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF020B12)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("J.A.R.V.I.S. · VISION CORE", color = Color(0xFF19E6F2), fontSize = 22.sp)
                        Text(status, color = Color(0xFF6EA7B2), fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(subtitle, color = Color(0xFFE5FBFF), fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        val t = telemetry.snapshot()
                        Text("SYSTEM TELEMETRY", color = Color(0xFF19E6F2), fontSize = 13.sp)
                        Text(t.compact(), color = Color(0xFFB9EAF0), fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { camera.launch(null) }, modifier = Modifier.weight(1f)) { Text("CAMERA SCAN") }
                            Button(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text("RETURN") }
                        }
                    }
                }
            }
        }
    }
}
