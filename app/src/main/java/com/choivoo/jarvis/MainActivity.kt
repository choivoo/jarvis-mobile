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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import kotlin.math.PI
import kotlin.math.sin

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

private val HudBg = Color(0xFF020B12)
private val HudPanel = Color(0xFF041520)
private val HudPanel2 = Color(0xFF061B28)
private val HudCyan = Color(0xFF19E6F2)
private val HudCyanSoft = Color(0xFF0AA7BB)
private val HudBlue = Color(0xFF3A8DFF)
private val HudAmber = Color(0xFFFFC84A)
private val HudRed = Color(0xFFFF4E61)
private val HudText = Color(0xFFE5FBFF)
private val HudMuted = Color(0xFF6EA7B2)

@Composable
fun JarvisApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = HudBg) { JarvisHudHome() }
    }
}

@Composable
private fun JarvisHudHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assistantEngine = remember { JarvisAssistantEngine(context) }
    val voicePrefs = remember { VoicePreferences(context) }
    val taskStore = remember { JarvisTaskStore(context) }
    val automationStore = remember { JarvisAutomationStore(context) }

    var currentTime by remember { mutableStateOf(formatTime()) }
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var heardText by remember { mutableStateOf("대기 중입니다. ‘자비스’라고 부르거나 MIC DIRECT INPUT을 누르세요.") }
    var responseText by remember { mutableStateOf("JARVIS MARK II 시스템이 준비되었습니다.") }
    var history by remember { mutableStateOf(listOf<ChatEntry>()) }
    var wake by remember { mutableStateOf(readWakeSnapshot(context)) }
    var selectedVoice by remember { mutableStateOf(voicePrefs.getVoice()) }
    var provider by remember { mutableStateOf(voicePrefs.getProvider()) }
    var lastProvider by remember { mutableStateOf(voicePrefs.getLastProvider()) }
    var taskCount by remember { mutableStateOf(taskStore.pending().size) }
    var automationCount by remember { mutableStateOf(automationStore.all().size) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var calendarGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var neuralReady by remember { mutableStateOf(false) }
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
            onListeningStarted = { state = AssistantState.LISTENING; heardText = "음성 버퍼 수신 중..." },
            onPartialText = { heardText = it },
            onFinalText = ::processCommand,
            onError = { state = AssistantState.ERROR; responseText = it },
            onSpeakingStarted = { state = AssistantState.SPEAKING },
            onSpeakingFinished = { state = AssistantState.IDLE }
        )
    }
    voiceControllerRef = voiceController

    DisposableEffect(Unit) { onDispose { voiceController.destroy() } }

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
            neuralReady = voiceController.isNeuralReady()
            delay(800)
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
        responseText = "Context 권한 갱신 완료. LOCATION ${if (locationGranted) "ONLINE" else "OFFLINE"}, CALENDAR ${if (calendarGranted) "ONLINE" else "OFFLINE"}."
    }

    fun beginListening() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceController.startListening()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWakeService(context)
        else wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun cycleProvider() {
        val providers = VoicePreferences.PROVIDERS
        val current = providers.indexOf(provider).takeIf { it >= 0 } ?: 0
        val next = providers[(current + 1) % providers.size]
        voicePrefs.setProvider(next)
        provider = next
        responseText = "VOICE ROUTER를 ${next.uppercase()} 모드로 전환했습니다."
    }

    fun cycleVoice() {
        val voices = VoicePreferences.ALLOWED
        val current = voices.indexOf(selectedVoice).takeIf { it >= 0 } ?: 0
        val next = voices[(current + 1) % voices.size]
        voicePrefs.setVoice(next)
        selectedVoice = next
        voiceController.speak("음성 시스템 테스트입니다. 현재 클라우드 보이스는 $next 입니다.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .background(HudBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        HudTopBar(currentTime, wake, state)
        Spacer(Modifier.height(10.dp))

        HudPanel(title = "SYSTEM DIAGNOSTICS", code = "SYS.02") {
            DiagnosticRow("ARC CORE / UI", 0.984f, "98.4%", HudCyan)
            DiagnosticRow("VOICE RECOGNITION BUFFER", if (wake.enabled) 1f else 0.28f, if (wake.enabled) "ONLINE" else "STANDBY", if (wake.enabled) HudCyan else HudAmber)
            DiagnosticRow("NEURAL LOCAL VOICE", if (neuralReady) 1f else 0.22f, if (neuralReady) "ONLINE" else "OPTIONAL", if (neuralReady) HudCyan else HudAmber)
            DiagnosticRow("CONTEXT MATRIX", contextProgress(locationGranted, calendarGranted), contextLabel(locationGranted, calendarGranted), HudBlue)
        }

        Spacer(Modifier.height(10.dp))
        HudPanel(title = "PRIMARY CORE & AUDIO VISUALIZER", code = "MARK II") {
            JarvisCore(state)
            Spacer(Modifier.height(6.dp))
            AudioWaveform(state)
            Spacer(Modifier.height(10.dp))
            ConsoleLine("INPUT", heardText, HudMuted)
            Spacer(Modifier.height(6.dp))
            ConsoleLine("JARVIS", responseText, HudCyan)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("MIC DIRECT INPUT", Modifier.weight(1f)) {
                    when (state) {
                        AssistantState.LISTENING -> { voiceController.cancelListening(); state = AssistantState.IDLE }
                        AssistantState.SPEAKING -> { voiceController.stopSpeaking(); state = AssistantState.IDLE }
                        else -> beginListening()
                    }
                }
                HudButton(if (wake.enabled) "WAKE OFF" else "WAKE ON", Modifier.weight(1f), if (wake.enabled) HudAmber else HudCyan, ::toggleWake)
            }
        }

        Spacer(Modifier.height(10.dp))
        HudPanel(title = "AI / VOICE CONFIGURATION", code = "AUDIO.20") {
            TelemetryLine("BRAIN", if (JarvisConfig.cloudEnabled) "CLOUD + LOCAL HYBRID" else "LOCAL CORE")
            TelemetryLine("VOICE ROUTER", provider.uppercase())
            TelemetryLine("CLOUD VOICE", selectedVoice.uppercase())
            TelemetryLine("NEURAL LOCAL", if (neuralReady) "READY · SHERPA PATH" else "NOT INSTALLED / NOT READY")
            TelemetryLine("LAST AUDIO PATH", lastProvider.uppercase())
            TelemetryLine("WAKE ENGINE", wake.engine.uppercase())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("ROUTER ${provider.uppercase()}", Modifier.weight(1f), HudCyan, ::cycleProvider)
                HudButton("VOICE ${selectedVoice.uppercase()}", Modifier.weight(1f), HudCyan, ::cycleVoice)
            }
            Spacer(Modifier.height(8.dp))
            HudButton("CONTEXT PERMISSIONS", Modifier.fillMaxWidth(), HudBlue) {
                contextPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CALENDAR))
            }
        }

        Spacer(Modifier.height(10.dp))
        HudPanel(title = "OPERATIONS MATRIX", code = "OPS.20") {
            TelemetryLine("TASKS", taskCount.toString())
            TelemetryLine("AUTOMATIONS", automationCount.toString())
            TelemetryLine("LOCATION", if (locationGranted) "ONLINE" else "OFFLINE")
            TelemetryLine("CALENDAR", if (calendarGranted) "ONLINE" else "OFFLINE")
            Spacer(Modifier.height(8.dp))
            OperationsGrid(::processCommand)
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HudPanel(title = "RECENT OPERATIONS LOG", code = "LOG.20") {
                history.take(3).forEachIndexed { index, entry ->
                    Text("${index + 1}. ${entry.user}", color = HudMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(entry.assistant, color = HudText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("JARVIS MOBILE · MARK II · V2.0.0", color = HudMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HudTopBar(time: String, wake: WakeSnapshot, state: AssistantState) {
    Card(colors = CardDefaults.cardColors(containerColor = HudPanel), border = BorderStroke(1.dp, HudCyanSoft.copy(alpha = 0.55f)), shape = RoundedCornerShape(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("J.A.R.V.I.S.", color = HudCyan, fontSize = 19.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(time, color = HudCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (wake.enabled) "STANDBY · SAY ‘자비스’" else "WAKE CORE OFFLINE", color = if (wake.enabled) HudCyan else HudAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("STATE:${state.name}  NET:${if (JarvisConfig.cloudEnabled) "SECURE" else "LOCAL"}", color = HudMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun HudPanel(title: String, code: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = HudPanel.copy(alpha = 0.96f)), border = BorderStroke(1.dp, HudCyanSoft.copy(alpha = 0.55f)), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = HudCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, fontFamily = FontFamily.Monospace)
                Text(code, color = HudCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(HudCyanSoft.copy(alpha = 0.35f)))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, progress: Float, status: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HudText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(status, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = color, trackColor = HudCyanSoft.copy(alpha = 0.18f))
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun JarvisCore(state: AssistantState) {
    val transition = rememberInfiniteTransition(label = "core")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (state == AssistantState.PROCESSING) 1200 else 4200), RepeatMode.Restart), label = "rotation")
    val counter by transition.animateFloat(360f, 0f, infiniteRepeatable(tween(6200), RepeatMode.Restart), label = "counter")
    val pulse by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(if (state == AssistantState.SPEAKING) 430 else 900), RepeatMode.Reverse), label = "pulse")
    val coreColor = when (state) {
        AssistantState.ERROR -> HudRed
        AssistantState.EXECUTING -> Color(0xFF65FFB3)
        AssistantState.PROCESSING -> Color(0xFF7C9CFF)
        else -> HudCyan
    }

    Canvas(Modifier.fillMaxWidth().height(260.dp)) {
        val c = center
        val r = size.minDimension * 0.34f
        drawCircle(coreColor.copy(alpha = 0.06f), radius = r * 1.25f, center = c)
        drawCircle(coreColor.copy(alpha = 0.18f * pulse), radius = r * 0.32f, center = c)
        drawCircle(coreColor.copy(alpha = 0.95f), radius = r * 0.10f, center = c)
        drawCircle(coreColor.copy(alpha = 0.55f), radius = r * 0.43f, center = c, style = Stroke(2f))
        drawCircle(coreColor.copy(alpha = 0.75f), radius = r * 0.72f, center = c, style = Stroke(3f))
        drawCircle(coreColor.copy(alpha = 0.38f), radius = r, center = c, style = Stroke(2f))

        for (i in 0 until 12) {
            val start = rotation + i * 30f
            drawArc(coreColor.copy(alpha = 0.9f), startAngle = start, sweepAngle = 15f, useCenter = false,
                topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f), size = Size(r * 1.72f, r * 1.72f), style = Stroke(8f))
        }
        for (i in 0 until 6) {
            val start = counter + i * 60f
            drawArc(coreColor.copy(alpha = 0.6f), startAngle = start, sweepAngle = 28f, useCenter = false,
                topLeft = Offset(c.x - r * 0.57f, c.y - r * 0.57f), size = Size(r * 1.14f, r * 1.14f), style = Stroke(3f))
        }
        for (i in 0 until 3) {
            val angle = (rotation + i * 120f) * PI.toFloat() / 180f
            val p1 = Offset(c.x + (r * 0.18f * kotlin.math.cos(angle)), c.y + (r * 0.18f * kotlin.math.sin(angle)))
            val p2 = Offset(c.x + (r * 0.49f * kotlin.math.cos(angle)), c.y + (r * 0.49f * kotlin.math.sin(angle)))
            drawLine(coreColor, p1, p2, strokeWidth = 4f)
        }
    }
}

@Composable
private fun AudioWaveform(state: AssistantState) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(if (state == AssistantState.SPEAKING) 650 else 1500), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.fillMaxWidth().height(52.dp).background(HudBg.copy(alpha = 0.35f))) {
        val amp = if (state == AssistantState.SPEAKING || state == AssistantState.LISTENING) size.height * 0.32f else size.height * 0.11f
        var previous = Offset(0f, size.height / 2f)
        for (x in 1..100) {
            val px = size.width * x / 100f
            val y = size.height / 2f + sin((x / 9f) + phase) * amp
            val current = Offset(px, y)
            drawLine(HudCyan.copy(alpha = 0.65f), previous, current, strokeWidth = 2f)
            previous = current
        }
    }
}

@Composable
private fun ConsoleLine(prefix: String, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().background(HudBg.copy(alpha = 0.45f)).padding(9.dp)) {
        Text("$prefix > ", color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(text, color = HudText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TelemetryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HudMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = HudText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End)
    }
}

@Composable
private fun HudButton(label: String, modifier: Modifier = Modifier, accent: Color = HudCyan, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = HudPanel2, contentColor = accent), border = BorderStroke(1.dp, accent.copy(alpha = 0.75f)), shape = RoundedCornerShape(2.dp)) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OperationsGrid(onCommand: (String) -> Unit) {
    val tools = listOf(
        "MORNING BRIEF" to "오늘 모닝 브리핑 해 주세요",
        "WEATHER" to "현재 날씨 알려 주세요",
        "CALENDAR" to "오늘 일정 알려 주세요",
        "TASK MATRIX" to "할 일 목록 보여 주세요",
        "YOUTUBE" to "유튜브 열어 주세요",
        "AUTOMATIONS" to "자동화 목록 보여 주세요"
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        tools.chunked(2).forEach { rowTools ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                rowTools.forEach { (label, command) -> HudButton(label, Modifier.weight(1f)) { onCommand(command) } }
            }
        }
    }
}

private fun contextProgress(location: Boolean, calendar: Boolean): Float = ((if (location) 1 else 0) + (if (calendar) 1 else 0)) / 2f
private fun contextLabel(location: Boolean, calendar: Boolean): String = when {
    location && calendar -> "ONLINE"
    location || calendar -> "PARTIAL"
    else -> "OFFLINE"
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
