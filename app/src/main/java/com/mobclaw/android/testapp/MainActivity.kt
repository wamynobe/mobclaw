package com.mobclaw.android.testapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.mobclaw.android.testapp.voice.VoiceCommandService
import com.mobclaw.android.testapp.voice.VoiceMode
import kotlinx.coroutines.launch

/**
 * Simple test activity to exercise MobClaw agent.
 *
 * Usage:
 * 1. Select provider type
 * 2. Enter API key (not required for local Ollama)
 * 3. Grant overlay permission
 * 4. Enable MobClaw accessibility service
 * 5. Type a task and hit "Execute", or say "Hey Claw" followed by a command
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
        MOBMOCK("MobMock (ChatGPT)", false, "Uses Web Login - No key needed"),
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

        val mobMock = remember { com.mobmock.MobMock(this@MainActivity) }
        var loginSession by remember { mutableStateOf<com.mobmock.MobMock.LoginSession?>(null) }

        var accessibilityOk by remember { mutableStateOf(isAccessibilityEnabled()) }
        var overlayOk by remember { mutableStateOf(isOverlayPermissionGranted()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        // Voice state
        val voiceEvent by VoiceCommandService.stateFlow.collectAsState()
        var voiceActive by remember { mutableStateOf(false) }
        var micPermissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
            micPermissionGranted = audioGranted
            if (audioGranted) {
                voiceActive = true
                VoiceCommandService.start(this@MainActivity)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Microphone permission is required for voice commands",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // React to voice commands: detect event, then launch on the stable
        // coroutine scope so the task survives voiceEvent state changes.
        var lastHandledCommand by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(voiceEvent) {
            if (voiceEvent.mode == VoiceMode.PROCESSING && voiceEvent.transcription != null) {
                val command = voiceEvent.transcription!!
                if (command == lastHandledCommand) return@LaunchedEffect
                lastHandledCommand = command
                task = command

                val trimmedKey = apiKey.trim()
                if (selectedProvider.requiresApiKey && trimmedKey.isEmpty()) {
                    resultText = "Voice command received but no API key set."
                    return@LaunchedEffect
                }
                if (!isAccessibilityEnabled() || !isOverlayPermissionGranted()) {
                    resultText = "Voice: \"$command\"\nGrant permissions first."
                    return@LaunchedEffect
                }
                if (isRunning) {
                    resultText = "Voice: \"$command\"\nAgent is already running."
                    return@LaunchedEffect
                }

                // Launch on the stable scope — not inside LaunchedEffect which
                // gets cancelled when voiceEvent changes (PROCESSING -> STANDBY).
                scope.launch {
                    isRunning = true
                    resultText = "🎤 Voice: \"$command\"\n🦀 Executing..."
                    try {
                        val result = executeTask(mobMock, selectedProvider, trimmedKey, command)
                        val status = if (result.success) "✅ Success" else "❌ Failed"
                        resultText = buildString {
                            appendLine("🎤 Voice: \"$command\"")
                            appendLine("$status (${result.iterations} iterations, ${result.duration.inWholeSeconds}s)")
                            appendLine()
                            append(result.message)
                        }
                    } catch (e: Exception) {
                        resultText = "🎤 Voice: \"$command\"\n❌ Error: ${e.message}"
                    } finally {
                        isRunning = false
                        lastHandledCommand = null
                    }
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityOk = isAccessibilityEnabled()
                    overlayOk = isOverlayPermissionGranted()
                    micPermissionGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🦀 MobClaw Test", style = MaterialTheme.typography.headlineMedium)

                // Voice control section
                VoiceControlSection(
                    voiceActive = voiceActive,
                    voiceMode = voiceEvent.mode,
                    micPermissionGranted = micPermissionGranted,
                    onToggleVoice = {
                        if (voiceActive) {
                            voiceActive = false
                            VoiceCommandService.stop(this@MainActivity)
                        } else {
                            if (micPermissionGranted) {
                                voiceActive = true
                                VoiceCommandService.start(this@MainActivity)
                            } else {
                                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        }
                    },
                )

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

                        scope.launch {
                            if (selectedProvider == ProviderType.MOBMOCK && !mobMock.isLoggedIn()) {
                                loginSession = mobMock.startLogin()
                                return@launch
                            }

                            isRunning = true
                            resultText = "🦀 Executing..."
                            try {
                                val result = executeTask(mobMock, selectedProvider, trimmedKey, trimmedTask)
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

            if (loginSession != null) {
                ChatGPTLoginWebView(
                    authUrl = loginSession!!.authUrl,
                    onAuthSuccess = { code ->
                        scope.launch {
                            try {
                                mobMock.completeLogin(code, loginSession!!)
                                loginSession = null
                                Toast.makeText(this@MainActivity, "ChatGPT Login Successful!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                loginSession = null
                            }
                        }
                    },
                    onAuthCancel = {
                        loginSession = null
                    }
                )
            }
        }
    }

    @Composable
    private fun VoiceControlSection(
        voiceActive: Boolean,
        voiceMode: VoiceMode,
        micPermissionGranted: Boolean,
        onToggleVoice: () -> Unit,
    ) {
        val isListening = voiceMode == VoiceMode.LISTENING || voiceMode == VoiceMode.ACTIVATED
        val pulseTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.15f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic_pulse",
        )

        val buttonColor by animateColorAsState(
            targetValue = when {
                !voiceActive -> MaterialTheme.colorScheme.surfaceVariant
                isListening -> Color(0xFF4CAF50)
                voiceMode == VoiceMode.PROCESSING -> Color(0xFFFF9800)
                voiceMode == VoiceMode.ERROR -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.primary
            },
            label = "mic_color",
        )

        val statusText = when {
            !voiceActive -> "Voice off"
            voiceMode == VoiceMode.STANDBY -> "Say \"Hey Claw\"..."
            voiceMode == VoiceMode.ACTIVATED -> "Wake word detected!"
            voiceMode == VoiceMode.LISTENING -> "Listening..."
            voiceMode == VoiceMode.PROCESSING -> "Processing command..."
            voiceMode == VoiceMode.ERROR -> "Error — tap to retry"
            else -> "Voice off"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (voiceActive) Color(0x1A4CAF50) else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onToggleVoice,
                modifier = Modifier
                    .size(56.dp)
                    .scale(if (isListening) pulseScale else 1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = if (voiceActive) "🎤" else "🎙️",
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (voiceActive) "Voice Active" else "Voice Commands",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!micPermissionGranted && !voiceActive) {
                    Text(
                        text = "Tap to grant mic permission",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    private suspend fun executeTask(
        mobMock: com.mobmock.MobMock,
        providerType: ProviderType,
        apiKey: String,
        task: String
    ): AgentResult {
        val agentOverlay = AgentOverlay(applicationContext)

        val provider = buildProvider(mobMock, providerType, apiKey)
        val agent = MobAgent.builder()
            .provider(provider)
            .observer(OverlayObserver(agentOverlay))
            .config(MobClawConfig())
            .build()

        agentOverlay.onStopRequested = {
            agent.cancel()
            agentOverlay.updateStatus("⏹ Stopping...")
        }

        return agent.execute(task)
    }

    private fun buildProvider(mobMock: com.mobmock.MobMock, providerType: ProviderType, apiKey: String): LlmProvider {
        return when (providerType) {
            ProviderType.GEMINI -> GeminiProvider(apiKey = apiKey)
            ProviderType.OPENAI -> OpenAiProvider(apiKey = apiKey)
            ProviderType.ANTHROPIC -> AnthropicProvider(apiKey = apiKey)
            ProviderType.OPENROUTER -> OpenRouterProvider(apiKey = apiKey)
            ProviderType.OLLAMA -> {
                if (apiKey.isBlank()) OllamaProvider()
                else OllamaProvider(apiKey = apiKey)
            }
            ProviderType.MOBMOCK -> com.mobclaw.android.testapp.provider.MobMockProvider(mobMock = mobMock)
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
