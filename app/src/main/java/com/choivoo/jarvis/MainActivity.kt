package com.choivoo.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisApp()
        }
    }
}

@Composable
fun JarvisApp() {
    val background = Color(0xFF0B0D12)
    val surface = Color(0xFF141821)
    val primary = Color(0xFF8DA9FF)
    val textPrimary = Color(0xFFF4F6FA)
    val textSecondary = Color(0xFF9DA7B5)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            JarvisHome(
                surfaceColor = surface,
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
    primaryColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    var currentTime by remember { mutableStateOf(formatTime()) }
    var status by remember { mutableStateOf("Idle") }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatTime()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "JARVIS",
            color = textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = currentTime,
            color = textSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        JarvisOrb(primaryColor)

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = status,
            color = primaryColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "무엇을 도와줄까?",
            color = textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(34.dp))

        Button(
            onClick = {
                status = if (status == "Idle") "Listening" else "Idle"
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = surfaceColor,
                contentColor = textPrimary
            ),
            shape = CircleShape,
            modifier = Modifier.size(76.dp)
        ) {
            Text(
                text = "🎙",
                fontSize = 28.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "JARVIS Mobile · V0.1",
            color = textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun JarvisOrb(primaryColor: Color) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    Box(
        modifier = Modifier
            .size(174.dp)
            .scale(scale)
            .background(
                color = primaryColor.copy(alpha = 0.12f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = primaryColor.copy(alpha = 0.24f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        color = primaryColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun formatTime(): String {
    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
