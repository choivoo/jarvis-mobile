package com.choivoo.jarvis

import android.Manifest
import android.content.pm.PackageManager
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
import com.choivoo.jarvis.core.AssistantState
import com.choivoo.jarvis.memory.LocalMemoryStore
import com.choivoo.jarvis.tools.CommandRouter
import com.choivoo.jarvis.voice.VoiceController
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisApp() }
    }
}

data class ChatEntry(
    val user: String,
    val assistant: String
)

@Composable
fun JarvisApp() {
    val background = Color(0xFF090B10)
    val surface = Color(0xFF141925)
    val surfaceAlt = Color(0xFF1A2030)
    val primary = Color(0xFF88A6FF)
    val textPrimary = Color(0xFFF5F7FB)
    val textSecondary = Color(0xFF98A3B5)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            JarvisHome(
                surfaceColor = surface,
                surfaceAlt = surfaceAlt,
                primaryColor = primary,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
    }
}

@Composable
private fun JarvisHome(
    surfaceColor: Color,
    surfaceAlt: Color,
    primaryColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(formatTime()) }
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var heardText by remember { mutableStateOf("마이크를 눌러 말해봐.") }
    var responseText by remember { mutableStateOf("V0.5 준비 완료") }
    var history by remember { mutableStateOf(listOf<ChatEntry>()) }

    val memoryStore = remember { LocalMemoryStore(context) }
    val commandRouter = remember { CommandRouter(context, memoryStore) }

    lateinit var voiceController: VoiceController
    voiceController = remember {
        VoiceController(
            context = context,
            onListeningStarted = {
                state = AssistantState.LISTENING
                heardText = "듣고 있어..."
            },
            onPartialText = { text ->
                heardText = text
            },
            onFinalText = { text ->
                heardText = text
                state = AssistantState.PROCESSING
                val result = commandRouter.handle(text)
                responseText = result.response
                history = (listOf(ChatEntry(text, result.response)) + history).take(6)
                voiceController.speak(result.response)
            },
            onError = { message ->
                state = AssistantState.ERROR
                responseText = message
            },
            onSpeakingStarted = {
                state = AssistantState.SPEAKING
            },
            onSpeakingFinished = {
                state = AssistantState.IDLE
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { voiceController.destroy() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatTime()
            delay(1_000)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening()
        } else {
            state = AssistantState.ERROR
            responseText = "마이크 권한을 허용해야 음성 명령을 들을 수 있어."
        }
    }

    fun beginListening() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun executeQuickCommand(command: String) {
        heardText = command
        state = AssistantState.PROCESSING
        val result = commandRouter.handle(command)
        responseText = result.response
        history = (listOf(ChatEntry(command, result.response)) + history).take(6)
        voiceController.speak(result.response)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "JARVIS",
            color = textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = currentTime, color = textSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(30.dp))

        JarvisOrb(state = state, primaryColor = primaryColor)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stateLabel(state),
            color = stateColor(state, primaryColor),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (state) {
                AssistantState.LISTENING -> "듣고 있어."
                AssistantState.PROCESSING -> "생각 중..."
                AssistantState.SPEAKING -> "대답하는 중"
                AssistantState.ERROR -> "문제가 생겼어."
                else -> "무엇을 도와줄까?"
            },
            color = textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = surfaceAlt),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("내가 들은 말", color = textSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(heardText, color = textPrimary, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Text("JARVIS", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(responseText, color = textPrimary, fontSize = 17.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                when (state) {
                    AssistantState.LISTENING -> {
                        voiceController.cancelListening()
                        state = AssistantState.IDLE
                        responseText = "듣기를 취소했어."
                    }
                    AssistantState.SPEAKING -> {
                        voiceController.stopSpeaking()
                        state = AssistantState.IDLE
                        responseText = "말하기를 멈췄어."
                    }
                    else -> beginListening()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state == AssistantState.LISTENING) primaryColor else surfaceColor,
                contentColor = if (state == AssistantState.LISTENING) Color(0xFF091020) else textPrimary
            ),
            shape = CircleShape,
            modifier = Modifier.size(82.dp)
        ) {
            Text(text = if (state == AssistantState.LISTENING) "■" else "🎙", fontSize = 29.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "QUICK TOOLS",
            color = textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        QuickTools(
            surfaceColor = surfaceColor,
            textColor = textPrimary,
            onCommand = ::executeQuickCommand
        )

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "RECENT",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            history.take(3).forEach { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("나 · ${entry.user}", color = textSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.assistant, color = textPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "JARVIS Mobile · V0.5",
            color = textSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun QuickTools(
    surfaceColor: Color,
    textColor: Color,
    onCommand: (String) -> Unit
) {
    val tools = listOf(
        "시간" to "지금 몇 시야",
        "배터리" to "배터리 몇 퍼센트야",
        "YouTube" to "유튜브 켜",
        "10분 타이머" to "10분 타이머"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTools.forEach { (label, command) ->
                    Button(
                        onClick = { onCommand(command) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = surfaceColor,
                            contentColor = textColor
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, textAlign = TextAlign.Center)
                    }
                }
                if (rowTools.size == 1) Spacer(modifier = Modifier.weight(1f))
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
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    val orbColor = stateColor(state, primaryColor)

    Box(
        modifier = Modifier
            .size(168.dp)
            .scale(scale)
            .background(orbColor.copy(alpha = 0.13f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .background(orbColor.copy(alpha = 0.27f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(orbColor, CircleShape)
            )
        }
    }
}

private fun stateLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "Idle"
    AssistantState.LISTENING -> "Listening"
    AssistantState.PROCESSING -> "Processing"
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

private fun formatTime(): String {
    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
