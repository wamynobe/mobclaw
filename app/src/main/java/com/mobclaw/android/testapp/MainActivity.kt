package com.mobclaw.android.testapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobclaw.android.accessibility.MobClawAccessibilityService
import com.mobclaw.android.core.MobAgent
import com.mobclaw.android.core.MobClawConfig
import com.mobclaw.android.overlay.AgentOverlay
import com.mobclaw.android.overlay.OverlayObserver
import com.mobclaw.android.provider.GeminiProvider
import kotlinx.coroutines.launch

/**
 * Simple test activity to exercise MobClaw agent.
 *
 * Usage:
 * 1. Enter your Gemini API key
 * 2. Grant overlay permission
 * 3. Enable MobClaw accessibility service
 * 4. Type a task and hit "Execute"
 */
class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var taskInput: EditText
    private lateinit var executeButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var overlayButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    private var overlay: AgentOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyInput = findViewById(R.id.api_key_input)
        taskInput = findViewById(R.id.task_input)
        executeButton = findViewById(R.id.execute_button)
        accessibilityButton = findViewById(R.id.accessibility_button)
        overlayButton = findViewById(R.id.overlay_button)
        statusText = findViewById(R.id.status_text)
        resultText = findViewById(R.id.result_text)

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        overlayButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        executeButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            val task = taskInput.text.toString().trim()

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter your Gemini API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (task.isEmpty()) {
                Toast.makeText(this, "Enter a task", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (MobClawAccessibilityService.instance == null) {
                Toast.makeText(this, "Enable MobClaw accessibility service first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            executeTask(apiKey, task)
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityOk = MobClawAccessibilityService.instance != null
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        // Hide buttons and status when granted
        accessibilityButton.visibility = if (accessibilityOk) View.GONE else View.VISIBLE
        overlayButton.visibility = if (overlayOk) View.GONE else View.VISIBLE

        if (accessibilityOk && overlayOk) {
            statusText.visibility = View.GONE
        } else {
            statusText.visibility = View.VISIBLE
            statusText.text = buildString {
                if (!accessibilityOk) appendLine("❌ Accessibility Service: tap button to enable")
                if (!overlayOk) append("❌ Overlay Permission: tap button to grant")
            }.trim()
        }
    }

    private fun executeTask(apiKey: String, task: String) {
        executeButton.isEnabled = false
        resultText.text = "🦀 Executing..."

        // Create overlay for this session
        val agentOverlay = AgentOverlay(applicationContext)
        overlay = agentOverlay

        val provider = GeminiProvider(apiKey = apiKey)
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

        lifecycleScope.launch {
            try {
                val result = agent.execute(task)
                val status = if (result.success) "✅ Success" else "❌ Failed"
                resultText.text = buildString {
                    appendLine("$status (${result.iterations} iterations, ${result.duration.inWholeSeconds}s)")
                    appendLine()
                    append(result.message)
                }
            } catch (e: Exception) {
                resultText.text = "❌ Error: ${e.message}"
            } finally {
                executeButton.isEnabled = true
                // Overlay stays visible — user can close it manually with ✕
            }
        }
    }
}
