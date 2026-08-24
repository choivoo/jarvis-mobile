package com.choivoo.jarvis.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisCalendar(private val context: Context) {
    data class Event(val title: String, val startMillis: Long)

    fun upcoming(limit: Int = 5, hoursAhead: Int = 24): List<Event> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()

        val begin = System.currentTimeMillis()
        val end = begin + hoursAhead * 60L * 60L * 1000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )

        return runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val title = cursor.getString(titleIndex)?.ifBlank { "일정" } ?: "일정"
                        val start = cursor.getLong(beginIndex)
                        add(Event(title, start))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun summary(): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "일정을 확인하려면 캘린더 읽기 권한이 필요합니다."
        }
        val events = upcoming()
        if (events.isEmpty()) return "앞으로 24시간 안에 등록된 일정이 없습니다."
        val formatter = SimpleDateFormat("a h시 mm분", Locale.KOREAN)
        return "앞으로 24시간 일정은 " + events.joinToString(". ") {
            "${formatter.format(Date(it.startMillis))} ${it.title}"
        } + "입니다."
    }

    fun createEventIntent(title: String, startMillis: Long): Intent {
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60L * 60L * 1000L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
