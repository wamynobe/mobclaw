package com.mobclaw.android.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopContextTest {

    @Test
    fun start_setsInitialState() {
        val ctx = LoopContext()
        ctx.start("send messages", 5)

        assertTrue(ctx.isActive)
        assertEquals(1, ctx.currentIteration)
        assertEquals(5, ctx.totalCount)
        assertEquals("send messages", ctx.description)
    }

    @Test
    fun next_advancesIteration() {
        val ctx = LoopContext()
        ctx.start("test", 3)

        assertTrue(ctx.next()) // 1 -> 2
        assertEquals(2, ctx.currentIteration)

        assertTrue(ctx.next()) // 2 -> 3
        assertEquals(3, ctx.currentIteration)

        assertFalse(ctx.next()) // 3 -> done
        assertFalse(ctx.isActive)
    }

    @Test
    fun next_returnsFalseWhenInactive() {
        val ctx = LoopContext()
        assertFalse(ctx.next())
    }

    @Test
    fun done_deactivatesLoop() {
        val ctx = LoopContext()
        ctx.start("test", 5)
        ctx.done()

        assertFalse(ctx.isActive)
    }

    @Test
    fun statusText_showsProgressWhenActive() {
        val ctx = LoopContext()
        ctx.start("like posts", 3)

        val status = ctx.statusText()
        assertTrue(status.contains("1/3"))
        assertTrue(status.contains("like posts"))
    }

    @Test
    fun statusText_emptyWhenInactive() {
        val ctx = LoopContext()
        assertEquals("", ctx.statusText())
    }

    @Test
    fun reset_clearsEverything() {
        val ctx = LoopContext()
        ctx.start("test", 5)
        ctx.next()
        ctx.reset()

        assertFalse(ctx.isActive)
        assertEquals(0, ctx.currentIteration)
        assertEquals(0, ctx.totalCount)
        assertEquals("", ctx.description)
    }

    @Test
    fun singleIterationLoop_completesCorrectly() {
        val ctx = LoopContext()
        ctx.start("one shot", 1)

        assertEquals(1, ctx.currentIteration)
        assertTrue(ctx.isActive)

        // Advance past single iteration
        assertFalse(ctx.next())
        assertFalse(ctx.isActive)
    }
}
