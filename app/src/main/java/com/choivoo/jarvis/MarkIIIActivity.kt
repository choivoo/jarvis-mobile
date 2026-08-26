package com.choivoo.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.choivoo.jarvis.config.JarvisConfig
import com.choivoo.jarvis.telemetry.SystemTelemetry
import com.choivoo.jarvis.vision.VisionActivity
import com.choivoo.jarvis.wake.WakeWordService
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MarkIIIActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarkIIIHud() }
    }
}

private val MkBg = Color(0xFF01070C)
private val MkPanel = Color(0xFF03131C)
private val MkCyan = Color(0xFF19E6F2)
private val MkBlue = Color(0xFF3A8DFF)
private val MkText = Color(0xFFE8FCFF)
private val MkMuted = Color(0xFF6E9BA6)
private val MkAmber = Color(0xFFFFC84A)

@Composable
private fun MarkIIIHud() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MkBg) {
            val context = LocalContext.current
            val telemetry = remember { SystemTelemetry(context) }
            var snapshot by remember { mutableStateOf(telemetry.snapshot()) }
            var time by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
            var wakeEnabled by remember { mutableStateOf(context.getSharedPreferences(WakeWordService.PREFS, 0).getBoolean(WakeWordService.KEY_ENABLED, false)) }
            var message by remember { mutableStateOf("MARK III systems nominal. Vision and context cores are standing by.") }

            val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    startWake(context)
                    wakeEnabled = true
                    message = "Wake Core online."
                } else message = "마이크 권한이 필요합니다."
            }

            LaunchedEffect(Unit) {
                while (true) {
                    snapshot = telemetry.snapshot()
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    wakeEnabled = context.getSharedPreferences(WakeWordService.PREFS, 0).getBoolean(WakeWordService.KEY_ENABLED, false)
                    delay(1000)
                }
            }

            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MkPanel("J.A.R.V.I.S. / MARK III", "V2.3.0") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time, color = MkCyan, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                        Text(if (wakeEnabled) "WAKE · ONLINE" else "WAKE · STANDBY", color = if (wakeEnabled) MkCyan else MkAmber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Text("VISION · SCREEN CONTEXT · LIVE TELEMETRY", color = MkMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }

                MkPanel("LIVE SYSTEM TELEMETRY", "SYS.23") {
                    MkGauge("BATTERY", snapshot.batteryPercent / 100f, "${snapshot.batteryPercent}%${if (snapshot.charging) " · CHARGING" else ""}")
                    MkGauge("MEMORY", snapshot.ramPercent / 100f, "${snapshot.ramPercent}% · ${snapshot.ramUsedMb}/${snapshot.ramTotalMb} MB")
                    MkGauge("JARVIS APP CPU", snapshot.appCpuPercent / 100f, "${snapshot.appCpuPercent}%")
                    MkLine("APP MEMORY", "${snapshot.appPssMb} MB PSS")
                    MkLine("NETWORK", snapshot.network)
                    MkLine("UPTIME", "${snapshot.uptimeMinutes} MIN")
                }

                MkPanel("PRIMARY INTELLIGENCE CORE", "VISION.23") {
                    MarkIIICore(wakeEnabled)
                    Text(message, color = MkText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MkButton("VISION SCAN", Modifier.weight(1f)) {
                            context.startActivity(Intent(context, VisionActivity::class.java))
                        }
                        MkButton("OPS CONSOLE", Modifier.weight(1f)) {
                            context.startActivity(Intent(context, MainActivity::class.java))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MkButton(if (wakeEnabled) "WAKE OFF" else "WAKE ON", Modifier.weight(1f), if (wakeEnabled) MkAmber else MkCyan) {
                            if (wakeEnabled) {
                                context.stopService(Intent(context, WakeWordService::class.java))
                                context.getSharedPreferences(WakeWordService.PREFS, 0).edit().putBoolean(WakeWordService.KEY_ENABLED, false).apply()
                                wakeEnabled = false
                            } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startWake(context); wakeEnabled = true
                            } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        MkButton("OVERLAY", Modifier.weight(1f), MkBlue) {
                            if (!Settings.canDrawOverlays(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
                            } else message = "한국어 HUD 자막 오버레이 권한이 이미 활성화되어 있습니다."
                        }
                    }
                }

                MkPanel("CONTEXT MATRIX", "CTX.23") {
                    MkLine("CLOUD BRAIN", if (JarvisConfig.cloudEnabled) "SECURE LINK" else "LOCAL / UNCONFIGURED")
                    MkLine("CAMERA VISION", "READY")
                    MkLine("SCREEN CONTEXT", "SHARE SCREENSHOT → JARVIS")
                    MkLine("KOREAN OVERLAY", if (Settings.canDrawOverlays(context)) "ONLINE" else "PERMISSION REQUIRED")
                    Text("화면 이해: 어느 앱에서든 스크린샷을 찍은 뒤 공유 → JARVIS Screen Context를 선택하세요.", color = MkMuted, fontSize = 11.sp)
                }

                Text("JARVIS MOBILE · MARK III · PERMANENT UPDATE IDENTITY", color = MkMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MkPanel(title: String, code: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MkPanel), border = BorderStroke(1.dp, MkCyan.copy(alpha = .45f)), shape = RoundedCornerShape(3.dp)) {
        Column(Modifier.padding(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = MkCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(code, color = MkMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp)); content()
        }
    }
}

@Composable
private fun MkGauge(label: String, progress: Float, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MkMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = MkText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MkCyan, trackColor = MkCyan.copy(alpha = .12f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MkLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MkMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = MkText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun MarkIIICore(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "mk3")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 1800 else 4200), RepeatMode.Restart), label = "r")
    val counter by transition.animateFloat(360f, 0f, infiniteRepeatable(tween(6600), RepeatMode.Restart), label = "c")
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val r = size.minDimension * .34f
        drawCircle(MkCyan.copy(alpha = .04f), r * 1.25f, center)
        drawCircle(MkCyan.copy(alpha = .18f), r * .3f, center)
        drawCircle(MkCyan, r * .07f, center)
        drawCircle(MkCyan.copy(alpha = .55f), r * .55f, center, style = Stroke(2f))
        drawCircle(MkCyan.copy(alpha = .35f), r, center, style = Stroke(2f))
        repeat(16) { i ->
            drawArc(MkCyan.copy(alpha = .78f), rotation + i * 22.5f, 10f, false,
                Offset(center.x - r * .88f, center.y - r * .88f), Size(r * 1.76f, r * 1.76f), style = Stroke(5f))
        }
        repeat(8) { i ->
            drawArc(MkBlue.copy(alpha = .65f), counter + i * 45f, 20f, false,
                Offset(center.x - r * .68f, center.y - r * .68f), Size(r * 1.36f, r * 1.36f), style = Stroke(3f))
        }
    }
}

@Composable
private fun MkButton(label: String, modifier: Modifier, accent: Color = MkCyan, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = MkPanel, contentColor = accent), border = BorderStroke(1.dp, accent.copy(alpha = .65f)), shape = RoundedCornerShape(2.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

private fun startWake(context: android.content.Context) {
    val intent = Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}
