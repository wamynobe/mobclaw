package com.wamynobe.mobclaw.tool

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatToolTest {

    @Test
    fun repeat_startsLoopWithCorrectState() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatTool(ctx)

        val result = tool.execute(buildJsonObject {
            put("count", 3)
            put("description", "send message to contacts")
        })

        assertTrue(result.success)
        assertTrue(ctx.isActive)
        assertEquals(1, ctx.currentIteration)
        assertEquals(3, ctx.totalCount)
        assertTrue(result.output.contains("3 iterations"))
    }

    @Test
    fun repeat_rejectsZeroCount() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatTool(ctx)

        val result = tool.execute(buildJsonObject {
            put("count", 0)
            put("description", "test")
        })

        assertFalse(result.success)
        assertFalse(ctx.isActive)
    }

    @Test
    fun repeat_rejectsExcessiveCount() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatTool(ctx)

        val result = tool.execute(buildJsonObject {
            put("count", 200)
            put("description", "test")
        })

        assertFalse(result.success)
    }

    @Test
    fun repeat_requiresParameters() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatTool(ctx)

        val result = tool.execute(buildJsonObject {})

        assertFalse(result.success)
    }

    @Test
    fun repeatNext_advancesAndReportsStatus() = runBlocking {
        val ctx = LoopContext()
        ctx.start("test", 3)
        val tool = RepeatNextTool(ctx)

        val result = tool.execute(buildJsonObject {})

        assertTrue(result.success)
        assertEquals(2, ctx.currentIteration)
        assertTrue(result.output.contains("2/3"))
    }

    @Test
    fun repeatNext_completesOnLastIteration() = runBlocking {
        val ctx = LoopContext()
        ctx.start("test", 2)
        val tool = RepeatNextTool(ctx)

        // Advance from 1 to 2
        tool.execute(buildJsonObject {})
        // Advance from 2 past end
        val result = tool.execute(buildJsonObject {})

        assertTrue(result.success)
        assertFalse(ctx.isActive)
        assertTrue(result.output.contains("complete"))
    }

    @Test
    fun repeatNext_failsWithNoActiveLoop() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatNextTool(ctx)

        val result = tool.execute(buildJsonObject {})

        assertFalse(result.success)
    }

    @Test
    fun repeatDone_endsActiveLoop() = runBlocking {
        val ctx = LoopContext()
        ctx.start("test", 5)
        val tool = RepeatDoneTool(ctx)

        val result = tool.execute(buildJsonObject {
            put("reason", "found what I needed")
        })

        assertTrue(result.success)
        assertFalse(ctx.isActive)
        assertTrue(result.output.contains("1/5"))
        assertTrue(result.output.contains("found what I needed"))
    }

    @Test
    fun repeatDone_handlesNoActiveLoop() = runBlocking {
        val ctx = LoopContext()
        val tool = RepeatDoneTool(ctx)

        val result = tool.execute(buildJsonObject {})

        assertTrue(result.success)
        assertTrue(result.output.contains("No active loop"))
    }
}
