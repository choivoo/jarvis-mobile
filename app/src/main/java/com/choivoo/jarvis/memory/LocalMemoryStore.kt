package com.choivoo.jarvis.memory

import android.content.Context

class LocalMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    fun save(content: String) {
        val existing = getAll().toMutableList()
        existing.add(0, content.trim())
        prefs.edit().putStringSet(KEY_MEMORIES, existing.take(MAX_MEMORIES).toSet()).apply()
    }

    fun getAll(): List<String> {
        return prefs.getStringSet(KEY_MEMORIES, emptySet())
            ?.toList()
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    fun search(query: String): List<String> {
        val tokens = query.lowercase().split(" ").filter { it.length >= 2 }
        if (tokens.isEmpty()) return getAll().take(5)
        return getAll()
            .map { memory -> memory to tokens.count { token -> memory.lowercase().contains(token) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(5)
    }

    fun clear() {
        prefs.edit().remove(KEY_MEMORIES).apply()
    }

    companion object {
        private const val KEY_MEMORIES = "memories"
        private const val MAX_MEMORIES = 100
    }
}
