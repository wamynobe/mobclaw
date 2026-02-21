package com.mobclaw.android.memory

/**
 * Simple in-memory implementation of MobMemory.
 * Suitable for single-session use; data is lost when process ends.
 */
class InMemoryStorage : MobMemory {

    private val entries = mutableListOf<MemoryEntry>()

    override suspend fun store(key: String, value: String, category: String?) {
        entries.removeAll { it.key == key }
        entries.add(MemoryEntry(key, value, category))
    }

    override suspend fun recall(query: String, limit: Int): List<MemoryEntry> {
        val queryLower = query.lowercase()
        return entries
            .filter {
                it.key.lowercase().contains(queryLower) ||
                    it.value.lowercase().contains(queryLower)
            }
            .takeLast(limit)
    }

    override suspend fun clear() {
        entries.clear()
    }
}
