package com.wamynobe.mobclaw.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryStorageTest {

    @Test
    fun store_replacesExistingKeyAndReturnsLatestForMatchingQuery() = runBlocking {
        val storage = InMemoryStorage()

        storage.store("task", "first")
        storage.store("task", "second")
        storage.store("other", "context")

        val results = storage.recall("task", limit = 10)
        assertEquals(1, results.size)
        assertEquals("task", results[0].key)
        assertEquals("second", results[0].value)
    }

    @Test
    fun recall_isCaseInsensitiveAndRespectsLimit() = runBlocking {
        val storage = InMemoryStorage()

        storage.store("First", "one")
        storage.store("second", "Two")
        storage.store("third", "three")

        val results = storage.recall("t", limit = 2)

        assertEquals(2, results.size)
        assertEquals("second", results[0].key)
        assertEquals("third", results[1].key)
    }

    @Test
    fun clear_removesAllEntries() = runBlocking {
        val storage = InMemoryStorage()

        storage.store("a", "1")
        storage.store("b", "2")
        storage.clear()

        val results = storage.recall("", limit = 10)
        assertEquals(0, results.size)
    }
}
