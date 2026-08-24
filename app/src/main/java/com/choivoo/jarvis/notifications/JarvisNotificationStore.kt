package com.choivoo.jarvis.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class JarvisNotificationStore(context: Context) {
    data class Item(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    )

    private val prefs = context.getSharedPreferences("jarvis_notifications", Context.MODE_PRIVATE)

    @Synchronized
    fun add(item: Item) {
        val items = getAll().toMutableList()
        items.add(0, item)
        save(items.take(40))
    }

    fun getAll(): List<Item> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        Item(
                            packageName = obj.optString("packageName"),
                            title = obj.optString("title"),
                            text = obj.optString("text"),
                            timestamp = obj.optLong("timestamp")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun summary(limit: Int = 8): String {
        val items = getAll().take(limit)
        if (items.isEmpty()) return "최근 수집된 알림이 없습니다. 알림 접근 권한이 켜져 있는지 확인해 주세요."
        return "최근 주요 알림은 " + items.joinToString(". ") { item ->
            val label = item.title.ifBlank { item.packageName.substringAfterLast('.') }
            val body = item.text.take(90)
            if (body.isBlank()) label else "$label: $body"
        } + "입니다."
    }

    fun clear() = prefs.edit().remove("items").apply()

    private fun save(items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("packageName", item.packageName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("timestamp", item.timestamp)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
