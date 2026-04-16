package com.wamynobe.mobclaw.core

import com.wamynobe.mobclaw.accessibility.GestureEngine
import com.wamynobe.mobclaw.accessibility.ScreenReader
import com.wamynobe.mobclaw.dispatcher.ActionDispatcher
import com.wamynobe.mobclaw.dispatcher.JsonActionDispatcher
import com.wamynobe.mobclaw.memory.InMemoryStorage
import com.wamynobe.mobclaw.memory.MobMemory
import com.wamynobe.mobclaw.model.*
import com.wamynobe.mobclaw.observer.LogObserver
import com.wamynobe.mobclaw.observer.MobObserver
import com.wamynobe.mobclaw.overlay.OverlayObserver
import com.wamynobe.mobclaw.provider.LlmProvider
import com.wamynobe.mobclaw.skill.SkillRegistry
import com.wamynobe.mobclaw.tool.*
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * MobClaw Agent — the core orchestrator.
 *
 * Loop: ScreenRead → Build messages → LLM chat → Parse actions → Execute tools → Repeat
 */
class MobAgent private constructor(
    private val provider: LlmProvider,
    private val tools: List<MobTool>,
    private val toolSpecs: List<ToolSpec>,
    private val dispatcher: ActionDispatcher,
    private val memory: MobMemory,
    private val observer: MobObserver,
    private val config: MobClawConfig,
    private val skillRegistry: SkillRegistry,
    private val loopContext: LoopContext,
) {
    private val history = mutableListOf<ConversationMessage>()

    @Volatile
    private var cancelled = false

    /** Cancel the currently running task. Safe to call from any thread. */
    fun cancel() {
        cancelled = true
    }

    /**
     * Execute a task described in natural language.
     * This is the main entry point
     *
     * @param task The user's natural language task description.
     * @return Result text from the agent (success message or failure reason).
     */
    suspend fun execute(task: String): AgentResult {
        cancelled = false
        loopContext.reset()
        val mark = TimeSource.Monotonic.markNow()
        observer.onAgentStart(task)
        history.clear()

        // Match skills based on the task and merge any additional tools
        val matchedSkills = skillRegistry.findMatchingSkills(task)
        val allTools = if (matchedSkills.isEmpty()) tools
        else {
            val skillTools = matchedSkills.flatMap { it.additionalTools() }
            if (skillTools.isEmpty()) tools
            else (tools + skillTools).distinctBy { it.name }
        }
        val allToolSpecs = allTools.map { it.spec() }

        // Build system prompt with tool instructions + skill knowledge
        val systemPrompt = buildSystemPrompt(task, matchedSkills)
        history.add(ConversationMessage.Chat(ChatMessage.system(systemPrompt)))

        // Initial screen read
        val screenTool = allTools.filterIsInstance<ScreenReadTool>().firstOrNull()
        val initialScreen = screenTool?.execute(kotlinx.serialization.json.buildJsonObject {})
        val screenContext = if (initialScreen?.success == true) {
            // Feed screen state to overlay + GestureEngine for node resolution & fallback
            val screenState = ScreenReader.read()
            (observer as? OverlayObserver)?.currentScreenState = screenState
            GestureEngine.lastScreenState = screenState
            "\n\nCurrent screen:\n${initialScreen.output}"
        } else {
            "\n\n(Screen read unavailable — accessibility service may not be enabled)"
        }

        // Add user message with initial screen context
        val userMessage = "Task: $task$screenContext"
        history.add(ConversationMessage.Chat(ChatMessage.user(userMessage)))

        // Agent loop
        for (iteration in 0 until config.maxIterations) {
            // Check cancellation
            if (cancelled) {
                observer.onAgentEnd(task, mark.elapsedNow(), false)
                return AgentResult(
                    success = false,
                    message = "Agent stopped by user",
                    iterations = iteration,
                    duration = mark.elapsedNow(),
                )
            }
            val messages = dispatcher.toProviderMessages(history)
            val response = try {
                provider.chat(
                    messages = messages,
                    tools = if (dispatcher.shouldSendToolSpecs()) allToolSpecs else null,
                    model = config.model,
                    temperature = config.temperature,
                )
            } catch (e: Exception) {
                observer.onError("LLM call failed at iteration $iteration", e)
                return AgentResult(
                    success = false,
                    message = "LLM error: ${e.message}",
                    iterations = iteration + 1,
                    duration = mark.elapsedNow(),
                )
            }

            val (text, actions) = dispatcher.parseResponse(response)

            // No tool calls — LLM gave text without acting.
            // Don't treat as success! Prompt it to continue using tools.
            if (actions.isEmpty()) {
                val assistantText = text.ifEmpty { response.text.orEmpty() }
                history.add(ConversationMessage.Chat(ChatMessage.assistant(assistantText)))
                // Feed reasoning to overlay
                (observer as? OverlayObserver)?.onReasoning(assistantText)

                // Remind the LLM to keep going with tools
                history.add(ConversationMessage.Chat(ChatMessage.user(
                    "You must use tools to complete the task. The task is NOT done yet. " +
                    "Call `finish` when the task is truly complete, or call another action tool to continue. " +
                    "Read the screen if you need to see what's on screen."
                )))
                continue
            }

            // Record assistant text + tool calls
            if (text.isNotEmpty()) {
                history.add(ConversationMessage.Chat(ChatMessage.assistant(text)))
                // Feed reasoning to overlay
                (observer as? OverlayObserver)?.onReasoning(text)
            }
            history.add(ConversationMessage.AssistantToolCalls(
                text = response.text,
                toolCalls = response.toolCalls,
            ))

            // Execute each tool call
            val results = mutableListOf<ToolExecutionResult>()
            for (action in actions) {
                // Feed current screen state and show pending action on overlay
                val overlayObserver = observer as? OverlayObserver
                if (overlayObserver != null) {
                    val argsMap = action.arguments.entries.associate { (k, v) ->
                        k to (v as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    }
                    overlayObserver.onActionPending(action.name, argsMap)
                }

                val toolMark = TimeSource.Monotonic.markNow()
                val tool = allTools.find { it.name == action.name }

                val result = if (tool != null) {
                    val toolResult = try {
                        tool.execute(action.arguments)
                    } catch (e: Exception) {
                        ToolResult(false, "", "Error executing ${action.name}: ${e.message}")
                    }

                    observer.onToolCall(action.name, toolMark.elapsedNow(), toolResult.success)

                    ToolExecutionResult(
                        name = action.name,
                        output = if (toolResult.success) toolResult.output
                        else "Error: ${toolResult.error ?: toolResult.output}",
                        success = toolResult.success,
                        toolCallId = action.toolCallId,
                    )
                } else {
                    ToolExecutionResult(
                        name = action.name,
                        output = "Unknown tool: ${action.name}",
                        success = false,
                        toolCallId = action.toolCallId,
                    )
                }

                results.add(result)

                if (action.name == "finish") {
                    val formatted = dispatcher.formatResults(results)
                    history.add(formatted)
                    observer.onAgentEnd(task, mark.elapsedNow(), true)

                    // Automatically return to host app
                    returnToHostApp()

                    return AgentResult(
                        success = true,
                        message = result.output,
                        iterations = iteration + 1,
                        duration = mark.elapsedNow(),
                    )
                }
                if (action.name == "fail") {
                    val formatted = dispatcher.formatResults(results)
                    history.add(formatted)
                    observer.onAgentEnd(task, mark.elapsedNow(), false)
                    return AgentResult(
                        success = false,
                        message = result.output,
                        iterations = iteration + 1,
                        duration = mark.elapsedNow(),
                    )
                }
            }

            // Append results to history
            val formatted = dispatcher.formatResults(results)
            history.add(formatted)

            // Auto-read screen after actions if configured
            if (config.autoScreenRead && screenTool != null) {
                delay(config.actionDelayMs)
                val newScreen = screenTool.execute(kotlinx.serialization.json.buildJsonObject {})
                if (newScreen.success) {
                    // Update screen state for overlay + GestureEngine
                    val screenState = ScreenReader.read()
                    (observer as? OverlayObserver)?.currentScreenState = screenState
                    GestureEngine.lastScreenState = screenState

                    // Build screen update message, include loop status if active
                    val loopStatus = if (loopContext.isActive) "\n${loopContext.statusText()}" else ""
                    history.add(ConversationMessage.Chat(
                        ChatMessage.user(
                            "[Updated screen]\n${newScreen.output}\n\n" +
                            "[REMINDER] Original task: \"$task\" — " +
                            "Make sure you complete ALL parts before calling finish." +
                            loopStatus
                        )
                    ))
                    observer.onScreenRead(
                        newScreen.output.substringAfter("Screen: ").substringBefore(" ==="),
                        newScreen.output.count { it == '\n' },
                    )
                }
            }

            // Trim history if too long
            trimHistory()
        }

        observer.onAgentEnd(task, mark.elapsedNow(), false)
        return AgentResult(
            success = false,
            message = "Agent exceeded maximum iterations (${config.maxIterations})",
            iterations = config.maxIterations,
            duration = mark.elapsedNow(),
        )
    }

    /**
     * Automatically navigate back to the host app after task completion.
     * Uses the accessibility service context to launch the host app.
     */
    private fun returnToHostApp() {
        try {
            val service = com.wamynobe.mobclaw.accessibility.MobClawAccessibilityService.instance
                ?: return
            val context = service.applicationContext
            val pm = context.packageManager

            // Find the host app package (the app that includes mobclaw)
            val hostPackage = context.packageName
            val launchIntent = pm.getLaunchIntentForPackage(hostPackage) ?: return
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            // Best effort — don't crash if we can't return
        }
    }

    private fun buildSystemPrompt(
        task: String,
        matchedSkills: List<com.wamynobe.mobclaw.skill.MobSkill> = emptyList(),
    ): String = buildString {
        appendLine("""
You are MobClaw, an expert AI agent that controls an Android phone autonomously.
You observe the screen via the Android Accessibility Service and interact with UI elements to complete user tasks.

## Your Capabilities (via Accessibility Service)
You have full access to:
- **List & launch apps**: Scan all installed apps by package name and launch them directly — NO need to navigate the home screen
- **Read entire UI tree**: Every visible element with its text, description, resource ID, state, and bounds
- **Click any element**: Buttons, links, list items, switches, checkboxes, tabs
- **Long-click elements**: For context menus, drag operations, edit modes
- **Type text**: Into any editable field (search bars, text inputs, forms)
- **Scroll**: Up/down/left/right to reveal hidden content
- **System actions**: Back, Home, Recents, Notifications, Quick Settings
- **Tap coordinates**: For elements that are rendered but not in the accessibility tree (Canvas, WebView, maps)
- **Wait**: For animations, loading screens, or network operations to complete
- **Repeat/Loop**: Perform the same set of actions multiple times

## How to Read the Screen State
Each screen read gives you a list of UI elements with:
- **[nX]**: Unique node ID — use this with the `click` or `input_text` tools
- **className**: The widget type (Button, TextView, EditText, Switch, ImageView, RecyclerView, etc.)
- **resourceId**: The Android view ID (e.g. "com.android.settings:id/title") — this tells you WHAT the element is
- **text/desc/hint**: What the user sees on this element
- **state**: checked/unchecked for toggles, selected for tabs, focused for inputs
- **bounds**: Screen coordinates (left,top)-(right,bottom) — use for tap when node ID doesn't work

## Reasoning Strategy
For EVERY turn, think step-by-step:
1. **Observe**: What app am I in? What screen is this? What elements are visible?
2. **Plan**: What is the next logical step toward completing the task?
3. **Act**: Which specific element should I interact with, and how?
4. **Verify**: After acting, check the new screen state to confirm the action worked

## How to Open Apps
When you need to open an app:
1. If you know the package name (e.g. 'com.android.settings'), use `open_app(package_name)` directly
2. If you don't know the package name, use `list_apps(filter)` to search (e.g. `list_apps(filter="chrome")` to find Chrome)
3. Then use `open_app(package_name)` with the result
4. Wait 500-1000ms after opening for the app to load, then read the screen

Do NOT try to find app icons on the home screen — always use `open_app` instead.

Common package names:
- Settings: com.android.settings
- Chrome: com.android.chrome
- Phone: com.android.dialer
- Messages: com.google.android.apps.messaging
- Camera: com.android.camera / com.google.android.GoogleCamera
- Gmail: com.google.android.gm
- Maps: com.google.android.apps.maps
- YouTube: com.google.android.youtube
- Play Store: com.android.vending
- Files: com.google.android.documentsui
- Clock: com.google.android.deskclock
- Calculator: com.google.android.calculator
- Calendar: com.google.android.calendar
- Contacts: com.android.contacts

## Repeating / Loop Actions
When you need to repeat the same set of actions multiple times (e.g. "send this message to 5 contacts", "like the next 3 posts"):
1. Call `repeat(count, description)` to declare the loop
2. Perform the actions for iteration 1
3. Call `repeat_next` to advance to the next iteration
4. Repeat steps 2-3 until all iterations are done
5. The system will tell you when the loop is complete, or call `repeat_done` to end early

## Common Android Navigation Patterns
- **Back navigation**: Use `system_action(back)` to go to previous screen
- **Toggles/Switches**: Look for Switch or ToggleButton elements with checked/unchecked state
- **Search**: Many apps have a search icon (🔍) or search bar at the top
- **Tabs**: Look for TabLayout or elements with "selected" state
- **Lists**: RecyclerView or ListView — scroll down if the target item isn't visible
- **Dialogs/Popups**: Often have "OK", "Cancel", "Allow", "Deny" buttons
- **Permissions**: Android may show permission dialogs — click "Allow" or "While using the app"
- **Loading states**: If screen seems empty, use `wait` then `screen_read` again
- **Keyboards**: After typing in a field, you may need to click a "Search" or "Go" button on the keyboard, or press back to dismiss

## Important Tips
- **Always read the screen first** before deciding what to do
- **Use resource IDs** to identify elements reliably (e.g. "id/search_button" is more reliable than matching text)
- **Scroll if needed**: If you don't see the target element, it might be below the fold — scroll down
- **Be patient with loading**: After clicking something that triggers navigation, wait 500-1000ms then re-read
- **Handle errors gracefully**: If an action fails, try an alternative approach (e.g. tap coordinates instead of click)
- **Check for state changes**: After toggling a switch, verify it changed state by reading the screen again
- **Don't repeat failed actions**: If something doesn't work after 2 attempts, try a different approach

## CRITICAL RULES — Read Carefully
- **COMPLETE THE ENTIRE TASK**: You MUST complete every single step the user asked for. If the user says "open Settings and change language to English", you must (1) open Settings, (2) navigate to language settings, (3) actually change the language to English, and (4) confirm it changed. Do NOT stop halfway. Do NOT call `finish` until every part of the request is fully done.
- **ALWAYS respond with tool calls** — NEVER give a text-only response without calling a tool
- **ONLY use `finish` to signal task completion** — do NOT assume the task is done until you have fully verified it
- **ONLY use `fail` to signal failure** — do NOT stop without calling `finish` or `fail`
- **Verify before finishing**: After performing all steps, read the screen one more time to confirm the task was actually completed successfully
- **Keep going until truly done**: If the task has multiple steps, complete ALL steps before calling `finish`
- **Never give up too early**: If you encounter an obstacle, try alternative approaches (scroll, search, use different menus). Only call `fail` after exhausting all options
- **Stay focused — do NOT navigate back between sub-tasks**: If the task is "do X and then do Y", after completing X go DIRECTLY to Y. Do NOT press back or return to any home screen between sub-tasks. The system will automatically return to the host app after you call `finish`.

## Tool Usage Rules
- Use `open_app(package_name)` to launch apps — never navigate the home screen manually
- Use `list_apps(filter)` if you don't know the package name
- Use `click(node_id)` as your primary interaction method — it's the most reliable
- Use `tap(x, y)` only when click doesn't work or for custom-drawn elements
- Use `input_text(node_id, text)` to type — it replaces existing text in the field
- Use `scroll(direction)` when content extends beyond the visible area
- Use `wait(milliseconds)` after actions that trigger screen transitions (300-1000ms is typical)
- Use `screen_read()` explicitly if you need to re-observe after a wait
- Use `system_action(action)` for global navigation (back, home, notifications)
- Use `repeat(count, description)` when you need to do the same actions multiple times
""".trimIndent())

        // Inject skill-specific domain knowledge
        val skillPrompt = skillRegistry.buildSkillPrompt(matchedSkills)
        if (skillPrompt.isNotEmpty()) {
            appendLine(skillPrompt)
        }

        appendLine()
        append(dispatcher.promptInstructions(tools))
    }

    private fun trimHistory() {
        val maxMessages = 40
        if (history.size <= maxMessages) return

        val systemMessages = history.filter {
            it is ConversationMessage.Chat && it.message.role == "system"
        }
        val others = history.filter {
            !(it is ConversationMessage.Chat && it.message.role == "system")
        }

        if (others.size > maxMessages) {
            val dropped = others.size - maxMessages
            history.clear()
            history.addAll(systemMessages)
            history.addAll(others.drop(dropped))
        }
    }

    /**
     * Builder pattern.
     */
    class Builder {
        private var provider: LlmProvider? = null
        private var tools: List<MobTool>? = null
        private var dispatcher: ActionDispatcher? = null
        private var memory: MobMemory? = null
        private var observer: MobObserver? = null
        private var config: MobClawConfig = MobClawConfig()
        private var skillRegistry: SkillRegistry? = null

        fun provider(provider: LlmProvider) = apply { this.provider = provider }
        fun tools(tools: List<MobTool>) = apply { this.tools = tools }
        fun dispatcher(dispatcher: ActionDispatcher) = apply { this.dispatcher = dispatcher }
        fun memory(memory: MobMemory) = apply { this.memory = memory }
        fun observer(observer: MobObserver) = apply { this.observer = observer }
        fun config(config: MobClawConfig) = apply { this.config = config }
        fun skillRegistry(registry: SkillRegistry) = apply { this.skillRegistry = registry }

        fun build(): MobAgent {
            val resolvedProvider = provider
                ?: throw IllegalStateException("LlmProvider is required")

            val loopContext = LoopContext()
            val resolvedTools = tools ?: defaultTools(loopContext)
            val toolSpecs = resolvedTools.map { it.spec() }

            return MobAgent(
                provider = resolvedProvider,
                tools = resolvedTools,
                toolSpecs = toolSpecs,
                dispatcher = dispatcher ?: JsonActionDispatcher(
                    sendToolSpecs = resolvedProvider.supportsNativeTools()
                ),
                memory = memory ?: InMemoryStorage(),
                observer = observer ?: LogObserver(),
                config = config,
                skillRegistry = skillRegistry ?: SkillRegistry.withDefaults(),
                loopContext = loopContext,
            )
        }

        /**
         * Default tool registry.
         */
        private fun defaultTools(loopContext: LoopContext): List<MobTool> = listOf(
            ScreenReadTool(),
            ListAppsTool(),
            OpenAppTool(),
            ClickTool(),
            LongClickTool(),
            TapTool(),
            InputTextTool(),
            ScrollTool(),
            SystemActionTool(),
            WaitTool(),
            RepeatTool(loopContext),
            RepeatNextTool(loopContext),
            RepeatDoneTool(loopContext),
            FinishTool(),
            FailTool(),
        )
    }

    companion object {
        fun builder() = Builder()
    }
}

/**
 * Result of an agent task execution.
 */
data class AgentResult(
    val success: Boolean,
    val message: String,
    val iterations: Int,
    val duration: Duration,
)
