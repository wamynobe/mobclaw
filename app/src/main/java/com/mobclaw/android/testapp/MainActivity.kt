package com.mobclaw.android.testapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobclaw.android.accessibility.MobClawAccessibilityService
import com.mobclaw.android.core.AgentResult
import com.mobclaw.android.core.MobAgent
import com.mobclaw.android.core.MobClawConfig
import com.mobclaw.android.overlay.AgentOverlay
import com.mobclaw.android.overlay.OverlayObserver
import com.mobclaw.android.provider.AnthropicProvider
import com.mobclaw.android.provider.GeminiProvider
import com.mobclaw.android.provider.LlmProvider
import com.mobclaw.android.provider.OllamaProvider
import com.mobclaw.android.provider.OpenAiProvider
import com.mobclaw.android.provider.OpenRouterProvider
import kotlinx.coroutines.launch

/**
 * Simple test activity to exercise MobClaw agent.
 *
 * Usage:
 * 1. Select provider type
 * 2. Enter API key (not required for local Ollama)
 * 3. Grant overlay permission
 * 4. Enable MobClaw accessibility service
 * 5. Type a task and hit "Execute"
 */
class MainActivity : ComponentActivity() {

    private enum class ProviderType(
        val label: String,
        val requiresApiKey: Boolean,
        val apiKeyHint: String,
    ) {
        GEMINI("Gemini", true, "Gemini API Key"),
        OPENAI("OpenAI", true, "OpenAI API Key"),
        ANTHROPIC("Anthropic", true, "Anthropic API Key"),
        OPENROUTER("OpenRouter", true, "OpenRouter API Key"),
        OLLAMA("Ollama", false, "Ollama API Key (optional)"),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen()
                }
            }
        }
    }

    @Composable
    private fun DemoScreen() {
        var selectedProvider by remember { mutableStateOf(ProviderType.GEMINI) }
        var apiKey by remember { mutableStateOf("") }
        var task by remember { mutableStateOf("") }
        var isRunning by remember { mutableStateOf(false) }
        var resultText by remember { mutableStateOf("Results will appear here...") }
        var providerMenuExpanded by remember { mutableStateOf(false) }

        var accessibilityOk by remember { mutableStateOf(isAccessibilityEnabled()) }
        var overlayOk by remember { mutableStateOf(isOverlayPermissionGranted()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityOk = isAccessibilityEnabled()
                    overlayOk = isOverlayPermissionGranted()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🦀 MobClaw Test", style = MaterialTheme.typography.headlineMedium)

            if (!accessibilityOk || !overlayOk) {
                Text(
                    text = buildString {
                        if (!accessibilityOk) appendLine("❌ Accessibility Service: tap button to enable")
                        if (!overlayOk) append("❌ Overlay Permission: tap button to grant")
                    }.trim(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (!accessibilityOk) {
                Button(
                    onClick = { openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Accessibility Settings")
                }
            }

            if (!overlayOk) {
                Button(
                    onClick = { openOverlaySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Overlay Permission")
                }
            }

            Text("Provider", style = MaterialTheme.typography.labelLarge)
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { providerMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedProvider.label)
                }
                DropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    ProviderType.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.label) },
                            onClick = {
                                selectedProvider = provider
                                providerMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(selectedProvider.apiKeyHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text("Task (e.g. Open Settings and turn on Wi-Fi)") }
            )

            Button(
                onClick = {
                    val trimmedKey = apiKey.trim()
                    val trimmedTask = task.trim()

                    if (selectedProvider.requiresApiKey && trimmedKey.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Enter your ${selectedProvider.label} API key",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    if (trimmedTask.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Enter a task", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isAccessibilityEnabled()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Enable MobClaw accessibility service first",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (!isOverlayPermissionGranted()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Grant overlay permission first",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    isRunning = true
                    resultText = "🦀 Executing..."
                    scope.launch {
                        try {
                            val result = executeTask(selectedProvider, trimmedKey, trimmedTask)
                            val status = if (result.success) "✅ Success" else "❌ Failed"
                            resultText = buildString {
                                appendLine("$status (${result.iterations} iterations, ${result.duration.inWholeSeconds}s)")
                                appendLine()
                                append(result.message)
                            }
                        } catch (e: Exception) {
                            resultText = "❌ Error: ${e.message}"
                        } finally {
                            isRunning = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(if (isRunning) "Executing..." else "🚀 Execute Task")
            }

            Text(
                text = resultText,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp),
                fontFamily = FontFamily.Monospace
            )
        }
    }

    private suspend fun executeTask(
        providerType: ProviderType,
        apiKey: String,
        task: String
    ): AgentResult {
        val agentOverlay = AgentOverlay(applicationContext)

        val provider = buildProvider(providerType, apiKey)
        val agent = MobAgent.builder()
            .provider(provider)
            .observer(OverlayObserver(agentOverlay))
            .config(MobClawConfig())
            .build()

        // Wire stop button to cancel the agent
        agentOverlay.onStopRequested = {
            agent.cancel()
            agentOverlay.updateStatus("⏹ Stopping...")
        }

        return agent.execute(task)
    }

    private fun buildProvider(providerType: ProviderType, apiKey: String): LlmProvider {
        return when (providerType) {
            ProviderType.GEMINI -> GeminiProvider(apiKey = apiKey)
            ProviderType.OPENAI -> OpenAiProvider(apiKey = apiKey)
            ProviderType.ANTHROPIC -> AnthropicProvider(apiKey = apiKey)
            ProviderType.OPENROUTER -> OpenRouterProvider(apiKey = apiKey)
            ProviderType.OLLAMA -> {
                if (apiKey.isBlank()) OllamaProvider()
                else OllamaProvider(apiKey = apiKey)
            }
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
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
}
