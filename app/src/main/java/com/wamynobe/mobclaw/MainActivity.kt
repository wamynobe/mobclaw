package com.wamynobe.mobclaw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wamynobe.mobclaw.accessibility.MobClawAccessibilityService
import com.wamynobe.mobclaw.core.MobAgent
import com.wamynobe.mobclaw.core.MobClawConfig
import com.wamynobe.mobclaw.overlay.AgentOverlay
import com.wamynobe.mobclaw.overlay.OverlayObserver
import com.wamynobe.mobclaw.provider.AnthropicProvider
import com.wamynobe.mobclaw.provider.GeminiProvider
import com.wamynobe.mobclaw.provider.LlmProvider
import com.wamynobe.mobclaw.provider.MobMockProvider
import com.wamynobe.mobclaw.provider.OllamaProvider
import com.wamynobe.mobclaw.provider.OpenAiProvider
import com.wamynobe.mobclaw.provider.OpenRouterProvider
import com.wamynobe.mobclaw.ui.navigation.MobClawNavigation
import com.wamynobe.mobclaw.ui.state.AgentUiState
import com.wamynobe.mobclaw.ui.state.LogLevel
import com.wamynobe.mobclaw.ui.state.MobClawAppState
import com.wamynobe.mobclaw.ui.state.ProviderStore
import com.wamynobe.mobclaw.ui.state.ProviderType
import com.wamynobe.mobclaw.ui.theme.MobClawTheme
import kotlinx.coroutines.launch

/**
 * MobClaw main activity — full navigation with 4 Stitch-designed screens.
 */
class MainActivity : ComponentActivity() {

    private lateinit var providerStore: ProviderStore
    private var currentAgent: MobAgent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        providerStore = ProviderStore(this)
        MobClawAppState.activeProvider = providerStore.getActiveProvider()
        MobClawAppState.initHistoryDb(this)

        setContent {
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current
            val mobMock = remember { com.mobmock.MobMock(this@MainActivity) }
            var loginSession by remember { mutableStateOf<com.mobmock.MobMock.LoginSession?>(null) }

            // Refresh permission state on resume
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        MobClawAppState.accessibilityEnabled = isAccessibilityEnabled()
                        MobClawAppState.overlayPermissionGranted = isOverlayPermissionGranted()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MobClawTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MobClawNavigation(
                        providerStore = providerStore,
                        onExecuteTask = { task ->
                            scope.launch {
                                executeTaskWithUi(mobMock, task) {
                                    loginSession = it
                                }
                            }
                        },
                        onStopAgent = {
                            currentAgent?.cancel()
                            MobClawAppState.agentState = AgentUiState.IDLE
                            MobClawAppState.addDebugLog("AGENT", "⏹ Agent stopped by user", LogLevel.WARNING)
                        },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onOpenOverlayPermission = { openOverlaySettings() },
                        onMobMockLogin = {
                            scope.launch {
                                loginSession = mobMock.startLogin()
                            }
                        },
                    )

                    // MobMock login WebView overlay
                    if (loginSession != null) {
                        ChatGPTLoginWebView(
                            authUrl = loginSession!!.authUrl,
                            onAuthSuccess = { code ->
                                scope.launch {
                                    try {
                                        mobMock.completeLogin(code, loginSession!!)
                                        loginSession = null
                                        Toast.makeText(
                                            this@MainActivity,
                                            "ChatGPT Login Successful!",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Login Failed: ${e.message}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        loginSession = null
                                    }
                                }
                            },
                            onAuthCancel = { loginSession = null },
                        )
                    }
                }
            }
        }
    }

    /**
     * Execute a task with full UI state management and debug logging.
     */
    private suspend fun executeTaskWithUi(
        mobMock: com.mobmock.MobMock,
        task: String,
        onLoginNeeded: (com.mobmock.MobMock.LoginSession) -> Unit,
    ) {
        val providerType = MobClawAppState.activeProvider

        // Validate
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Enable MobClaw accessibility service first", Toast.LENGTH_LONG).show()
            return
        }
        if (!isOverlayPermissionGranted()) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            return
        }

        // MobMock requires login
        if (providerType == ProviderType.MOBMOCK && !mobMock.isLoggedIn()) {
            onLoginNeeded(mobMock.startLogin())
            return
        }

        // Check API key for providers that need it
        val config = providerStore.loadProviderConfig(providerType)
        if (providerType.requiresApiKey && config.apiKey.isBlank()) {
            Toast.makeText(this, "Configure API key for ${providerType.label} first", Toast.LENGTH_LONG).show()
            return
        }

        // Update UI state
        MobClawAppState.agentState = AgentUiState.RUNNING
        MobClawAppState.currentTask = task
        MobClawAppState.addDebugLog("AGENT", "▶ Starting task: $task", LogLevel.INFO)

        try {
            val agentOverlay = AgentOverlay(applicationContext)
            val provider = buildProvider(mobMock, providerType, config)

            val overlayObserver = object : OverlayObserver(agentOverlay) {
                override fun onAgentStart(taskDesc: String) {
                    super.onAgentStart(taskDesc)
                    MobClawAppState.addDebugLog("AGENT", "Initializing agent...", LogLevel.INFO)
                }

                override fun onToolCall(toolName: String, duration: kotlin.time.Duration, success: Boolean) {
                    super.onToolCall(toolName, duration, success)
                    val status = if (success) "✓" else "✗"
                    MobClawAppState.addDebugLog(
                        "TOOL",
                        "$status $toolName (${duration.inWholeMilliseconds}ms)",
                        if (success) LogLevel.ACTION else LogLevel.ERROR,
                    )
                }

                override fun onScreenRead(packageName: String, nodeCount: Int) {
                    super.onScreenRead(packageName, nodeCount)
                    MobClawAppState.addDebugLog(
                        "SCREEN",
                        "DOM mapped: $nodeCount reachable nodes",
                        LogLevel.INFO,
                    )
                }

                override fun onError(message: String, throwable: Throwable?) {
                    super.onError(message, throwable)
                    MobClawAppState.addDebugLog("ERROR", message, LogLevel.ERROR)
                }

                override fun onReasoning(text: String) {
                    super.onReasoning(text)
                    MobClawAppState.addDebugLog(
                        "LLM",
                        "Intent: \"${text.take(80)}\"",
                        LogLevel.INFO,
                    )
                }

                override fun onActionPending(toolName: String, args: Map<String, String>) {
                    super.onActionPending(toolName, args)
                    val argsStr = args.entries.joinToString(", ") { "${it.key}=${it.value}" }.take(60)
                    MobClawAppState.addDebugLog(
                        "ACTION",
                        "$toolName($argsStr)",
                        LogLevel.ACTION,
                    )
                }
            }

            val agent = MobAgent.builder()
                .provider(provider)
                .observer(overlayObserver)
                .config(MobClawConfig(model = config.model.ifBlank { null }))
                .build()

            currentAgent = agent

            // Wire overlay stop → agent cancel
            agentOverlay.onStopRequested = {
                agent.cancel()
                agentOverlay.updateStatus("⏹ Stopping...")
                MobClawAppState.addDebugLog("AGENT", "Stop requested via overlay", LogLevel.WARNING)
            }

            val result = agent.execute(task)

            // Record result
            MobClawAppState.recordTaskResult(task, result)
            MobClawAppState.agentState = if (result.success) AgentUiState.SUCCESS else AgentUiState.FAILED
            MobClawAppState.addDebugLog(
                "AGENT",
                "${result.message.take(100)}",
                if (result.success) LogLevel.INFO else LogLevel.ERROR,
            )
        } catch (e: Exception) {
            MobClawAppState.agentState = AgentUiState.FAILED
            MobClawAppState.addDebugLog("ERROR", "Fatal: ${e.message}", LogLevel.ERROR)
        } finally {
            currentAgent = null
        }
    }

    private fun buildProvider(
        mobMock: com.mobmock.MobMock,
        providerType: ProviderType,
        config: com.wamynobe.mobclaw.ui.state.ProviderConfig,
    ): LlmProvider {
        return when (providerType) {
            ProviderType.GEMINI -> GeminiProvider(apiKey = config.apiKey)
            ProviderType.OPENAI -> OpenAiProvider(apiKey = config.apiKey)
            ProviderType.ANTHROPIC -> AnthropicProvider(apiKey = config.apiKey)
            ProviderType.OPENROUTER -> OpenRouterProvider(apiKey = config.apiKey)
            ProviderType.OLLAMA -> {
                if (config.apiKey.isBlank()) OllamaProvider()
                else OllamaProvider(apiKey = config.apiKey)
            }
            ProviderType.MOBMOCK -> MobMockProvider(mobMock = mobMock)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return MobClawAccessibilityService.instance != null
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
}
