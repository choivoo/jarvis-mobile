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
        val batteryPercent = runCatching {
            context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?: 0
        }.getOrDefault(0)

        val charging = runCatching {
            context.getSystemService(BatteryManager::class.java)?.isCharging ?: false
        }.getOrDefault(false)

        val memory = runCatching {
            val activity = context.getSystemService(ActivityManager::class.java)
            if (activity == null) Triple(0L, 0L, 0)
            else {
                val info = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
                val totalMb = info.totalMem / (1024L * 1024L)
                val availMb = info.availMem / (1024L * 1024L)
                val usedMb = (totalMb - availMb).coerceAtLeast(0)
                val percent = if (totalMb > 0) {
                    ((usedMb * 100.0) / totalMb).roundToInt().coerceIn(0, 100)
                } else 0
                Triple(usedMb, totalMb, percent)
            }
        }.getOrElse { Triple(0L, 0L, 0) }

        val appPssMb = runCatching {
            (Debug.getPss() / 1024L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }.getOrDefault(0)

        val nowCpu = android.os.Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        val cpuDelta = (nowCpu - lastAppCpuMs).coerceAtLeast(0)
        val wallDelta = (nowWall - lastWallMs).coerceAtLeast(1)
        lastAppCpuMs = nowCpu
        lastWallMs = nowWall
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val appCpuPercent = runCatching {
            ((cpuDelta.toDouble() / wallDelta.toDouble()) * 100.0 / coreCount)
                .roundToInt().coerceIn(0, 100)
        }.getOrDefault(0)

        val network = runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
                ?: return@runCatching "UNKNOWN"
            connectivity.activeNetwork?.let { net ->
                connectivity.getNetworkCapabilities(net)?.let { caps ->
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                        else -> "ONLINE"
                    }
                }
            } ?: "OFFLINE"
        }.getOrElse { "UNKNOWN" }

        return Snapshot(
            batteryPercent = batteryPercent,
            charging = charging,
            ramUsedMb = memory.first,
            ramTotalMb = memory.second,
            ramPercent = memory.third,
            appPssMb = appPssMb,
            appCpuPercent = appCpuPercent,
            network = network,
            uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L
        )
    }
}
