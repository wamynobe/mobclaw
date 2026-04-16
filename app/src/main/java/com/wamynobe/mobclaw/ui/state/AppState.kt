package com.wamynobe.mobclaw.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wamynobe.mobclaw.db.TaskHistoryDbHelper
import com.wamynobe.mobclaw.core.AgentResult
import kotlin.time.Duration

/**
 * Singleton app state shared across all screens.
 */
object MobClawAppState {

    /** Current agent execution state. */
    var agentState by mutableStateOf(AgentUiState.IDLE)

    /** Currently running task description. */
    var currentTask by mutableStateOf("")

    /** Currently selected provider type. */
    var activeProvider by mutableStateOf(ProviderType.GEMINI)

    /** Accessibility service enabled. */
    var accessibilityEnabled by mutableStateOf(false)

    /** Overlay permission granted. */
    var overlayPermissionGranted by mutableStateOf(false)

    /** Total tasks executed this session. */
    var totalTasksExecuted by mutableStateOf(0)

    /** Total iterations across all tasks this session. */
    var totalIterations by mutableStateOf(0)

    /** Task history log. */
    val taskHistory = mutableStateListOf<TaskHistoryEntry>()

    /** Debug console log lines. */
    val debugLog = mutableStateListOf<DebugLogEntry>()

    private var dbHelper: TaskHistoryDbHelper? = null

    fun initHistoryDb(context: android.content.Context) {
        if (dbHelper == null) {
            dbHelper = TaskHistoryDbHelper(context.applicationContext)
            taskHistory.clear()
            taskHistory.addAll(dbHelper!!.getAllTasks())
            
            totalTasksExecuted = taskHistory.size
            totalIterations = taskHistory.sumOf { it.iterations }
        }
    }

    fun addDebugLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        debugLog.add(0, DebugLogEntry(timestamp, tag, message, level))
        // Keep last 200 entries
        while (debugLog.size > 200) {
            debugLog.removeAt(debugLog.lastIndex)
        }
    }

    fun recordTaskResult(task: String, result: AgentResult) {
        totalTasksExecuted++
        totalIterations += result.iterations
        val entry = TaskHistoryEntry(
            task = task,
            success = result.success,
            message = result.message,
            iterations = result.iterations,
            duration = result.duration,
            timestamp = System.currentTimeMillis(),
            provider = activeProvider,
        )
        taskHistory.add(0, entry)
        
        Thread {
            dbHelper?.insertTask(entry)
        }.start()
    }
}

enum class AgentUiState {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
}

enum class ProviderType(
    val label: String,
    val requiresApiKey: Boolean,
    val apiKeyHint: String,
    val icon: String,
    val defaultModel: String,
) {
    GEMINI("Gemini", true, "Gemini API Key", "G", "gemini-2.5-flash"),
    OPENAI("OpenAI", true, "OpenAI API Key", "O", "gpt-4o"),
    ANTHROPIC("Anthropic", true, "Anthropic API Key", "A", "claude-sonnet-4-20250514"),
    OPENROUTER("OpenRouter", true, "OpenRouter API Key", "R", ""),
    OLLAMA("Ollama", false, "Ollama API Key (optional)", "L", "llama3"),
    MOBMOCK("MobMock", false, "Uses Web Login - No key needed", "M", "gpt-5.4"),
}

data class ProviderConfig(
    val type: ProviderType,
    val apiKey: String = "",
    val model: String = type.defaultModel,
    val baseUrl: String = "",
    val isActive: Boolean = false,
)

data class TaskHistoryEntry(
    val task: String,
    val success: Boolean,
    val message: String,
    val iterations: Int,
    val duration: Duration,
    val timestamp: Long,
    val provider: ProviderType,
)

data class DebugLogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel,
)

enum class LogLevel {
    INFO,
    ACTION,
    WARNING,
    ERROR,
}
