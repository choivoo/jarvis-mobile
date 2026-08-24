package com.choivoo.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choivoo.jarvis.ai.BrainClient
import com.choivoo.jarvis.config.JarvisConfig
import com.choivoo.jarvis.core.AssistantState
import com.choivoo.jarvis.memory.LocalMemoryStore
import com.choivoo.jarvis.tools.CommandRouter
import com.choivoo.jarvis.voice.VoiceController
import com.choivoo.jarvis.voice.VoicePreferences
import com.choivoo.jarvis.wake.WakeWordService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisApp() }
    }
}

data class ChatEntry(val user: String, val assistant: String)

data class WakeSnapshot(
    val enabled: Boolean = false,
    val engine: String = "-",
    val status: String = "stopped",
    val lastError: String = "",
    val lastHeard: String = ""
)

@Composable
fun JarvisApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B10)) {
            JarvisHome()
        }
    }
}

@Composable
private fun JarvisHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val surface = Color(0xFF141925)
    val surfaceAlt = Color(0xFF1A2030)
    val primary = Color(0xFF88A6FF)
    val textPrimary = Color(0xFFF5F7FB)
    val textSecondary = Color(0xFF98A3B5)
    val good = Color(0xFF72DDB3)

    var currentTime by remember { mutableStateOf(formatTime()) }
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var heardText by remember { mutableStateOf("마이크를 눌러 말씀해 주세요.") }
    var responseText by remember { mutableStateOf("JARVIS Core V0.8 준비 완료") }
    var history by remember { mutableStateOf(listOf<ChatEntry>()) }
    var wake by remember { mutableStateOf(readWakeSnapshot(context)) }

    val memoryStore = remember { LocalMemoryStore(context) }
    val commandRouter = remember { CommandRouter(context, memoryStore) }
    val brainClient = remember { BrainClient() }
    val voicePreferences = remember { VoicePreferences(context) }
    var selectedVoice by remember { mutableStateOf(voicePreferences.getVoice()) }
    var voiceControllerRef: VoiceController? = null

    fun finishResponse(command: String, reply: String) {
        responseText = reply
        history = (listOf(ChatEntry(command, reply)) + history).take(8)
        voiceControllerRef?.speak(reply)
    }

    fun processCommand(command: String) {
        heardText = command
        state = AssistantState.PROCESSING
        val local = commandRouter.handle(command)
        selectedVoice = voicePreferences.getVoice()
        if (local.handledLocally) {
            if (local.actionPerformed) state = AssistantState.EXECUTING
            finishResponse(command, local.response)
            return
        }

        scope.launch {
            val turns = history.reversed().takeLast(8).map { BrainClient.Turn(it.user, it.assistant) }
            finishResponse(command, brainClient.chat(command, turns))
        }
    }

    val voiceController = remember {
        VoiceController(
            context = context,
            onListeningStarted = {
                state = AssistantState.LISTENING
                heardText = "듣고 있습니다..."
            },
            onPartialText = { heardText = it },
            onFinalText = ::processCommand,
            onError = {
                state = AssistantState.ERROR
                responseText = it
            },
            onSpeakingStarted = { state = AssistantState.SPEAKING },
            onSpeakingFinished = { state = AssistantState.IDLE }
        )
    }
    voiceControllerRef = voiceController

    DisposableEffect(Unit) {
        onDispose { voiceController.destroy() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatTime()
            wake = readWakeSnapshot(context)
            selectedVoice = voicePreferences.getVoice()
            delay(800)
        }
    }

    fun startWakeService() {
        val intent = Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        responseText = "JARVIS Wake Core를 시작했습니다. 홈 화면이나 화면 잠금 상태에서도 호출어를 기다립니다."
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceController.startListening()
        else {
            state = AssistantState.ERROR
            responseText = "마이크 권한이 필요합니다."
        }
    }

    val wakeMicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWakeService()
        else responseText = "Wake Core에는 마이크 권한이 필요합니다."
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun beginListening() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleWake() {
        if (wake.enabled) {
            context.stopService(Intent(context, WakeWordService::class.java))
            context.getSharedPreferences(WakeWordService.PREFS, 0).edit()
                .putBoolean(WakeWordService.KEY_ENABLED, false)
                .putString(WakeWordService.KEY_STATUS, "stopped")
                .apply()
            responseText = "Wake Core를 종료했습니다."
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        } else {
            wakeMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cycleVoice() {
        val voices = VoicePreferences.ALLOWED
        val current = voices.indexOf(selectedVoice).takeIf { it >= 0 } ?: 0
        val next = voices[(current + 1) % voices.size]
        voicePreferences.setVoice(next)
        selectedVoice = next
        responseText = "Voice Lab: $next 음성을 선택했습니다."
        voiceController.speak("Voice Lab 테스트입니다. 현재 선택된 음성은 $next 입니다.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("JARVIS", color = textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(currentTime, color = textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (JarvisConfig.cloudEnabled) "BRAIN ONLINE · PERSONAL JARVIS CORE" else "LOCAL MODE · CLOUD SETUP REQUIRED",
            color = if (JarvisConfig.cloudEnabled) good else textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (wake.enabled) "WAKE CORE · ON · ${wake.engine.uppercase()}" else "WAKE CORE · OFF",
            color = if (wake.enabled) good else textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(22.dp))
        JarvisOrb(state, primary)
        Spacer(Modifier.height(16.dp))
        Text(stateLabel(state), color = stateColor(state, primary), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when (state) {
                AssistantState.LISTENING -> "듣고 있습니다."
                AssistantState.PROCESSING -> "생각 중입니다..."
                AssistantState.EXECUTING -> "실행 중입니다..."
                AssistantState.SPEAKING -> "응답 중입니다."
                AssistantState.ERROR -> "문제가 발생했습니다."
                else -> if (wake.enabled) "백그라운드 호출 대기 중입니다." else "무엇을 도와드릴까요?"
            },
            color = textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        StatusCard(surfaceAlt, textPrimary, textSecondary, primary, heardText, responseText)

        Spacer(Modifier.height(14.dp))
        WakeDiagnosticsCard(surface, textPrimary, textSecondary, good, wake)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    when (state) {
                        AssistantState.LISTENING -> {
                            voiceController.cancelListening()
                            state = AssistantState.IDLE
                            responseText = "듣기를 취소했습니다."
                        }
                        AssistantState.SPEAKING -> {
                            voiceController.stopSpeaking()
                            state = AssistantState.IDLE
                            responseText = "음성 출력을 중지했습니다."
                        }
                        else -> beginListening()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (state == AssistantState.LISTENING) primary else surface),
                shape = CircleShape,
                modifier = Modifier.size(76.dp)
            ) { Text(if (state == AssistantState.LISTENING) "■" else "🎙", fontSize = 27.sp) }

            Button(
                onClick = ::toggleWake,
                colors = ButtonDefaults.buttonColors(containerColor = if (wake.enabled) Color(0xFF21483E) else surface),
                shape = RoundedCornerShape(18.dp)
            ) { Text(if (wake.enabled) "Wake OFF" else "Wake ON") }

            Button(
                onClick = ::cycleVoice,
                colors = ButtonDefaults.buttonColors(containerColor = surface),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Voice\n${selectedVoice.uppercase()}", textAlign = TextAlign.Center) }
        }

        Spacer(Modifier.height(24.dp))
        Text("JARVIS CORE", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(9.dp))
        QuickTools(surface, textPrimary, ::processCommand)

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("RECENT", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(9.dp))
            history.take(3).forEach { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("나 · ${entry.user}", color = textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.assistant, color = textPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("JARVIS Mobile · V0.8.0", color = textSecondary, fontSize = 12.sp)
        Text("Wake Core · Voice Lab · AI Brain · Android Tools", color = textSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusCard(
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    primary: Color,
    heard: String,
    response: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("인식된 음성", color = textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(heard, color = textPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(13.dp))
            Text("JARVIS", color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(response, color = textPrimary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun WakeDiagnosticsCard(
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    good: Color,
    wake: WakeSnapshot
) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WAKE DIAGNOSTICS", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (wake.enabled) "ONLINE" else "OFFLINE", color = if (wake.enabled) good else textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Engine · ${wake.engine}", color = textPrimary, fontSize = 13.sp)
            Text("Status · ${wake.status}", color = textPrimary, fontSize = 13.sp)
            if (wake.lastHeard.isNotBlank()) Text("Last heard · ${wake.lastHeard}", color = textSecondary, fontSize = 12.sp)
            if (wake.lastError.isNotBlank()) Text("Recovery · ${wake.lastError}", color = Color(0xFFFFB36B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickTools(surface: Color, textColor: Color, onCommand: (String) -> Unit) {
    val tools = listOf(
        "시간" to "지금 몇 시인가요",
        "배터리" to "배터리 몇 퍼센트인가요",
        "YouTube" to "유튜브 열어주세요",
        "Voice Lab" to "현재 목소리를 알려주세요",
        "기억" to "기억한 거 보여 주세요",
        "AI" to "오늘 알아두면 좋은 중요한 지식 하나 알려주세요"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { rowTools ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTools.forEach { (label, command) ->
                    Button(
                        onClick = { onCommand(command) },
                        colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = textColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(label, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun JarvisOrb(state: AssistantState, primary: Color) {
    val transition = rememberInfiniteTransition(label = "orb")
    val duration = when (state) {
        AssistantState.LISTENING -> 650
        AssistantState.PROCESSING -> 470
        AssistantState.EXECUTING -> 390
        AssistantState.SPEAKING -> 560
        AssistantState.ERROR -> 850
        AssistantState.IDLE -> 1750
    }
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (state == AssistantState.IDLE) 1.04f else 1.11f,
        animationSpec = infiniteRepeatable(tween(durationMillis = duration), RepeatMode.Reverse),
        label = "orbScale"
    )
    val orb = stateColor(state, primary)
    Box(Modifier.size(158.dp).scale(scale).background(orb.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(102.dp).background(orb.copy(alpha = 0.27f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(54.dp).background(orb, CircleShape))
        }
    }
}

private fun readWakeSnapshot(context: android.content.Context): WakeSnapshot {
    val prefs = context.getSharedPreferences(WakeWordService.PREFS, android.content.Context.MODE_PRIVATE)
    return WakeSnapshot(
        enabled = prefs.getBoolean(WakeWordService.KEY_ENABLED, false),
        engine = prefs.getString(WakeWordService.KEY_ENGINE, "-") ?: "-",
        status = prefs.getString(WakeWordService.KEY_STATUS, "stopped") ?: "stopped",
        lastError = prefs.getString(WakeWordService.KEY_LAST_ERROR, "") ?: "",
        lastHeard = prefs.getString(WakeWordService.KEY_LAST_HEARD, "") ?: ""
    )
}

private fun stateLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "Idle"
    AssistantState.LISTENING -> "Listening"
    AssistantState.PROCESSING -> "Thinking"
    AssistantState.EXECUTING -> "Executing"
    AssistantState.SPEAKING -> "Speaking"
    AssistantState.ERROR -> "Error"
}

private fun stateColor(state: AssistantState, primary: Color): Color = when (state) {
    AssistantState.ERROR -> Color(0xFFFF7F8A)
    AssistantState.PROCESSING -> Color(0xFFB49CFF)
    AssistantState.EXECUTING -> Color(0xFF72DDB3)
    AssistantState.SPEAKING -> Color(0xFF65C8FF)
    else -> primary
}

private fun formatTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
