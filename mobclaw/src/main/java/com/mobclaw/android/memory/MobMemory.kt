package com.mobclaw.android.memory

/**
 * Memory interface for persisting agent context.
 */
interface MobMemory {

    /** Store a key-value entry. */
    suspend fun store(key: String, value: String, category: String? = null)

    /** Recall entries matching a query. */
    suspend fun recall(query: String, limit: Int = 5): List<MemoryEntry>

    /** Clear all stored memory. */
    suspend fun clear()
}

data class MemoryEntry(
    val key: String,
    val value: String,
    val category: String? = null,
)
