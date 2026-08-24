package com.choivoo.jarvis

import android.Manifest
import android.content.Context
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
import com.choivoo.jarvis.automation.JarvisAutomationStore
import com.choivoo.jarvis.config.JarvisConfig
import com.choivoo.jarvis.core.AssistantState
import com.choivoo.jarvis.core.JarvisAssistantEngine
import com.choivoo.jarvis.tasks.JarvisTaskStore
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

    val assistantEngine = remember { JarvisAssistantEngine(context) }
    val voicePrefs = remember { VoicePreferences(context) }
    val taskStore = remember { JarvisTaskStore(context) }
    val automationStore = remember { JarvisAutomationStore(context) }

    var currentTime by remember { mutableStateOf(formatTime()) }
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var heardText by remember { mutableStateOf("마이크를 누르거나 ‘자비스’라고 불러 주세요.") }
    var responseText by remember { mutableStateOf("JARVIS V1.0 Personal Operations System 준비 완료") }
    var history by remember { mutableStateOf(listOf<ChatEntry>()) }
    var wake by remember { mutableStateOf(readWakeSnapshot(context)) }
    var selectedVoice by remember { mutableStateOf(voicePrefs.getVoice()) }
    var provider by remember { mutableStateOf(voicePrefs.getProvider()) }
    var lastProvider by remember { mutableStateOf(voicePrefs.getLastProvider()) }
    var taskCount by remember { mutableStateOf(taskStore.pending().size) }
    var automationCount by remember { mutableStateOf(automationStore.all().size) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var calendarGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var voiceControllerRef: VoiceController? = null

    fun processCommand(command: String) {
        heardText = command
        state = AssistantState.PROCESSING
        scope.launch {
            val turns = history.reversed().takeLast(10).map { BrainClient.Turn(it.user, it.assistant) }
            val result = assistantEngine.process(command, turns)
            responseText = result.response
            history = (listOf(ChatEntry(command, result.response)) + history).take(12)
            taskCount = taskStore.pending().size
            automationCount = automationStore.all().size
            voiceControllerRef?.speak(result.response)
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
            selectedVoice = voicePrefs.getVoice()
            provider = voicePrefs.getProvider()
            lastProvider = voicePrefs.getLastProvider()
            taskCount = taskStore.pending().size
            automationCount = automationStore.all().size
            locationGranted = hasLocationPermission(context)
            calendarGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            delay(900)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceController.startListening() else responseText = "마이크 권한이 필요합니다."
    }
    val wakePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWakeService(context) else responseText = "Wake Core에는 마이크 권한이 필요합니다."
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val contextPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = hasLocationPermission(context)
        calendarGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        responseText = "Context 권한을 갱신했습니다. 위치 ${if (locationGranted) "ON" else "OFF"}, 캘린더 ${if (calendarGranted) "ON" else "OFF"}입니다."
    }

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
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService(context)
        } else {
            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cycleVoice() {
        val voices = VoicePreferences.ALLOWED
        val current = voices.indexOf(selectedVoice).takeIf { it >= 0 } ?: 0
        val next = voices[(current + 1) % voices.size]
        voicePrefs.setVoice(next)
        selectedVoice = next
        responseText = "Cinematic Voice를 $next 음성으로 변경했습니다."
        voiceController.speak("음성 테스트입니다. 현재 선택된 보이스는 $next 입니다.")
    }

    fun cycleProvider() {
        val providers = VoicePreferences.PROVIDERS
        val current = providers.indexOf(provider).takeIf { it >= 0 } ?: 0
        val next = providers[(current + 1) % providers.size]
        voicePrefs.setProvider(next)
        provider = next
        responseText = when (next) {
            "auto" -> "음성 Provider를 AUTO로 설정했습니다. 클라우드 실패 시 로컬 음성으로 자동 전환합니다."
            "cloud" -> "음성 Provider를 CLOUD 전용으로 설정했습니다."
            else -> "음성 Provider를 LOCAL 전용으로 설정했습니다."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("JARVIS", color = textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(currentTime, color = textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (JarvisConfig.cloudEnabled) "PERSONAL OPERATIONS SYSTEM · V1.0" else "LOCAL OPERATIONS SYSTEM · V1.0",
            color = if (JarvisConfig.cloudEnabled) good else textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (wake.enabled) "WAKE · ON · ${wake.engine.uppercase()}" else "WAKE · OFF",
            color = if (wake.enabled) good else textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))
        JarvisOrb(state, primary)
        Spacer(Modifier.height(14.dp))
        Text(stateLabel(state), color = stateColor(state, primary), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when (state) {
                AssistantState.LISTENING -> "듣고 있습니다."
                AssistantState.PROCESSING -> "상황을 분석하고 있습니다..."
                AssistantState.EXECUTING -> "실행 중입니다..."
                AssistantState.SPEAKING -> "응답 중입니다."
                AssistantState.ERROR -> "확인이 필요한 문제가 있습니다."
                else -> if (wake.enabled) "백그라운드 호출 대기 중입니다." else "무엇을 도와드릴까요?"
            },
            color = textPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        StatusCard(surfaceAlt, textPrimary, textSecondary, primary, heardText, responseText)

        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("SYSTEM CORE", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Location · ${if (locationGranted) "ON" else "OFF"}    Calendar · ${if (calendarGranted) "ON" else "OFF"}", color = textPrimary, fontSize = 13.sp)
                Text("Tasks · $taskCount    Automations · $automationCount", color = textPrimary, fontSize = 13.sp)
                Text("Voice · ${selectedVoice.uppercase()}    Provider · ${provider.uppercase()}", color = textPrimary, fontSize = 13.sp)
                Text("Last voice path · ${lastProvider.uppercase()}", color = if (lastProvider.startsWith("local")) Color(0xFFFFC66D) else good, fontSize = 12.sp)
                if (wake.lastError.isNotBlank()) Text("Wake recovery · ${wake.lastError}", color = Color(0xFFFFB36B), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    when (state) {
                        AssistantState.LISTENING -> { voiceController.cancelListening(); state = AssistantState.IDLE }
                        AssistantState.SPEAKING -> { voiceController.stopSpeaking(); state = AssistantState.IDLE }
                        else -> beginListening()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (state == AssistantState.LISTENING) primary else surface),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) { Text(if (state == AssistantState.LISTENING) "■" else "🎙", fontSize = 25.sp) }

            Button(onClick = ::toggleWake, colors = ButtonDefaults.buttonColors(containerColor = if (wake.enabled) Color(0xFF21483E) else surface), shape = RoundedCornerShape(17.dp)) {
                Text(if (wake.enabled) "Wake OFF" else "Wake ON")
            }
            Button(onClick = ::cycleProvider, colors = ButtonDefaults.buttonColors(containerColor = surface), shape = RoundedCornerShape(17.dp)) {
                Text(provider.uppercase())
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = ::cycleVoice, colors = ButtonDefaults.buttonColors(containerColor = surface), modifier = Modifier.weight(1f)) {
                Text("Voice ${selectedVoice.uppercase()}")
            }
            Button(
                onClick = { contextPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CALENDAR)) },
                colors = ButtonDefaults.buttonColors(containerColor = surface),
                modifier = Modifier.weight(1f)
            ) { Text("Context 권한") }
        }

        Spacer(Modifier.height(22.dp))
        Text("OPERATIONS", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(9.dp))
        OperationsGrid(surface, textPrimary, ::processCommand)

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("RECENT", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            history.take(3).forEach { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("나 · ${entry.user}", color = textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.assistant, color = textPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("JARVIS Mobile · V1.0.0", color = textSecondary, fontSize = 12.sp)
        Text("Wake · Context · Weather · Calendar · Tasks · Automations · Memory · AI · Resilient Voice", color = textSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusCard(surface: Color, textPrimary: Color, textSecondary: Color, primary: Color, heard: String, response: String) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("인식된 음성", color = textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(heard, color = textPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text("JARVIS", color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(response, color = textPrimary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun OperationsGrid(surface: Color, textColor: Color, onCommand: (String) -> Unit) {
    val tools = listOf(
        "Morning Brief" to "오늘 모닝 브리핑 해 주세요",
        "Weather" to "현재 날씨 알려 주세요",
        "Calendar" to "오늘 일정 알려 주세요",
        "Tasks" to "할 일 목록 보여 주세요",
        "YouTube" to "유튜브 열어 주세요",
        "Automations" to "자동화 목록 보여 주세요"
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
private fun JarvisOrb(state: AssistantState, primaryColor: Color) {
    val transition = rememberInfiniteTransition(label = "orb")
    val duration = when (state) {
        AssistantState.LISTENING -> 700
        AssistantState.PROCESSING -> 500
        AssistantState.EXECUTING -> 420
        AssistantState.SPEAKING -> 620
        AssistantState.ERROR -> 900
        AssistantState.IDLE -> 1900
    }
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (state == AssistantState.IDLE) 1.04f else 1.10f,
        animationSpec = infiniteRepeatable(tween(durationMillis = duration), RepeatMode.Reverse),
        label = "orbScale"
    )
    val orbColor = stateColor(state, primaryColor)
    Box(Modifier.size(168.dp).scale(scale).background(orbColor.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(108.dp).background(orbColor.copy(alpha = 0.27f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(56.dp).background(orbColor, CircleShape))
        }
    }
}

private fun stateLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "Idle"
    AssistantState.LISTENING -> "Listening"
    AssistantState.PROCESSING -> "Thinking"
    AssistantState.EXECUTING -> "Executing"
    AssistantState.SPEAKING -> "Speaking"
    AssistantState.ERROR -> "Attention"
}

private fun stateColor(state: AssistantState, primary: Color): Color = when (state) {
    AssistantState.ERROR -> Color(0xFFFF7F8A)
    AssistantState.PROCESSING -> Color(0xFFB49CFF)
    AssistantState.EXECUTING -> Color(0xFF72DDB3)
    AssistantState.SPEAKING -> Color(0xFF65C8FF)
    else -> primary
}

private fun formatTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

private fun readWakeSnapshot(context: Context): WakeSnapshot {
    val p = context.getSharedPreferences(WakeWordService.PREFS, 0)
    return WakeSnapshot(
        enabled = p.getBoolean(WakeWordService.KEY_ENABLED, false),
        engine = p.getString(WakeWordService.KEY_ENGINE, "-") ?: "-",
        status = p.getString(WakeWordService.KEY_STATUS, "stopped") ?: "stopped",
        lastError = p.getString(WakeWordService.KEY_LAST_ERROR, "") ?: "",
        lastHeard = p.getString(WakeWordService.KEY_LAST_HEARD, "") ?: ""
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun startWakeService(context: Context) {
    val intent = Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}
