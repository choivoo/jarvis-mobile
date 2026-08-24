package com.choivoo.jarvis.tasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class JarvisTaskStore(context: Context) {
    data class Task(
        val id: Long,
        val title: String,
        val completed: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val prefs = context.getSharedPreferences("jarvis_tasks", Context.MODE_PRIVATE)

    fun all(): List<Task> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        Task(
                            id = o.optLong("id"),
                            title = o.optString("title"),
                            completed = o.optBoolean("completed", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun pending(): List<Task> = all().filterNot { it.completed }

    fun add(title: String): Task {
        val task = Task(
            id = System.currentTimeMillis(),
            title = title.trim().take(160)
        )
        save(all() + task)
        return task
    }

    fun completeByIndex(index: Int): Task? {
        val items = all().toMutableList()
        val pending = items.withIndex().filter { !it.value.completed }
        val selected = pending.getOrNull(index.coerceAtLeast(0)) ?: return null
        val completed = selected.value.copy(completed = true)
        items[selected.index] = completed
        save(items)
        return completed
    }

    fun deleteCompleted() {
        save(all().filterNot { it.completed })
    }

    fun summary(limit: Int = 6): String {
        val items = pending()
        if (items.isEmpty()) return "남아 있는 할 일이 없습니다."
        return "남은 할 일은 ${items.take(limit).mapIndexed { i, t -> "${i + 1}번 ${t.title}" }.joinToString(", ")}입니다."
    }

    private fun save(items: List<Task>) {
        val array = JSONArray()
        items.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("completed", task.completed)
                    .put("createdAt", task.createdAt)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
