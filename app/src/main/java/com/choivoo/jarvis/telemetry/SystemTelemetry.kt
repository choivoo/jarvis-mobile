package com.choivoo.jarvis.telemetry

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import kotlin.math.roundToInt

class SystemTelemetry(private val context: Context) {
    data class Snapshot(
        val batteryPercent: Int,
        val charging: Boolean,
        val ramUsedMb: Long,
        val ramTotalMb: Long,
        val ramPercent: Int,
        val appPssMb: Int,
        val appCpuPercent: Int,
        val network: String,
        val uptimeMinutes: Long
    ) {
        fun compact(): String =
            "BAT ${batteryPercent}% · RAM ${ramPercent}% · APP CPU ${appCpuPercent}% · NET $network · UP ${uptimeMinutes}m"

        fun spokenKorean(): String =
            "현재 배터리는 ${batteryPercent}%이고, 메모리는 ${ramPercent}% 사용 중입니다. " +
                "JARVIS 앱 CPU 사용률은 약 ${appCpuPercent}%이며 네트워크는 $network 상태입니다."
    }

    private var lastAppCpuMs = android.os.Process.getElapsedCpuTime()
    private var lastWallMs = SystemClock.elapsedRealtime()

    fun snapshot(): Snapshot {
        val battery = context.getSystemService(BatteryManager::class.java)
        val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = battery.isCharging

        val activity = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val totalMb = info.totalMem / (1024L * 1024L)
        val availMb = info.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val ramPercent = if (totalMb > 0) ((usedMb * 100.0) / totalMb).roundToInt().coerceIn(0, 100) else 0

        val pssKb = Debug.getPss()
        val appPssMb = (pssKb / 1024L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        val nowCpu = android.os.Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        val cpuDelta = (nowCpu - lastAppCpuMs).coerceAtLeast(0)
        val wallDelta = (nowWall - lastWallMs).coerceAtLeast(1)
        lastAppCpuMs = nowCpu
        lastWallMs = nowWall
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val appCpuPercent = ((cpuDelta.toDouble() / wallDelta.toDouble()) * 100.0 / coreCount)
            .roundToInt().coerceIn(0, 100)

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork?.let { net ->
            connectivity.getNetworkCapabilities(net)?.let { caps ->
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    else -> "ONLINE"
                }
            }
        } ?: "OFFLINE"

        return Snapshot(
            batteryPercent = batteryPercent,
            charging = charging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            ramPercent = ramPercent,
            appPssMb = appPssMb,
            appCpuPercent = appCpuPercent,
            network = network,
            uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L
        )
    }
}
