package com.wamynobe.mobclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wamynobe.mobclaw.ui.state.MobClawAppState
import com.wamynobe.mobclaw.ui.state.ProviderConfig
import com.wamynobe.mobclaw.ui.state.ProviderStore
import com.wamynobe.mobclaw.ui.state.ProviderType
import com.wamynobe.mobclaw.ui.theme.MobClawColors
import com.wamynobe.mobclaw.ui.theme.SpaceGroteskFamily

/**
 * LLM Providers configuration screen — matches "LLM Providers" Stitch design.
 */
@Composable
fun ProvidersScreen(
    providerStore: ProviderStore,
    onMobMockLogin: () -> Unit,
) {
    val configs = remember { mutableStateListOf<ProviderConfig>() }
    var activeProvider by remember { mutableStateOf(MobClawAppState.activeProvider) }

    LaunchedEffect(Unit) {
        configs.clear()
        configs.addAll(providerStore.loadAllProviders())
        activeProvider = providerStore.getActiveProvider()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MOBCLAW",
                style = MaterialTheme.typography.titleMedium,
                color = MobClawColors.Primary,
                fontFamily = SpaceGroteskFamily,
            )
            StatusChip(text = "Optimized", color = MobClawColors.Tertiary)
        }

        // Title
        Text(
            text = "LLM Configuration",
            style = MaterialTheme.typography.headlineMedium,
            color = MobClawColors.OnSurface,
        )
        Text(
            text = "Manage your neural processing units. Configure API keys and endpoint parameters for autonomous system reasoning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Provider cards
        ProviderType.entries.forEach { type ->
            val idx = configs.indexOfFirst { it.type == type }
            val config = if (idx >= 0) configs[idx] else ProviderConfig(type)
            val isActive = activeProvider == type

            ProviderCard(
                config = config,
                isActive = isActive,
                onConfigUpdate = { updated ->
                    if (idx >= 0) configs[idx] = updated
                    else configs.add(updated)
                },
                onSetActive = {
                    activeProvider = type
                    MobClawAppState.activeProvider = type
                    providerStore.setActiveProvider(type)
                },
                onMobMockLogin = onMobMockLogin,
            )
        }

        // Security note
        Text(
            text = "API keys are encrypted locally with AES-256-GCM.",
            style = MaterialTheme.typography.labelSmall,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 4.dp),
        )

        // Save button
        Button(
            onClick = {
                configs.forEach { providerStore.saveProviderConfig(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MobClawColors.PrimaryContainer,
                contentColor = MobClawColors.OnPrimaryContainer,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Nodes", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderCard(
    config: ProviderConfig,
    isActive: Boolean,
    onConfigUpdate: (ProviderConfig) -> Unit,
    onSetActive: () -> Unit,
    onMobMockLogin: () -> Unit,
) {
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) MobClawColors.SurfaceContainerHigh
                else MobClawColors.SurfaceContainer
            )
            .clickable { onSetActive() }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MobClawColors.PrimaryContainer.copy(alpha = 0.3f)
                            else MobClawColors.SurfaceContainerHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        config.type.icon,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MobClawColors.Primary else MobClawColors.OnSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = config.type.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MobClawColors.OnSurface,
                    )
                    if (config.model.isNotBlank()) {
                        Text(
                            text = config.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MobClawColors.PrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = MobClawColors.OnPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // API key field or login button
        if (config.type == ProviderType.MOBMOCK) {
            OutlinedButton(
                onClick = onMobMockLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Login", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            // API key
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { onConfigUpdate(config.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.3f),
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MobClawColors.OnSurface,
                    fontFamily = SpaceGroteskFamily,
                ),
                visualTransformation = if (showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MobClawColors.SurfaceContainerLowest,
                    unfocusedContainerColor = MobClawColors.SurfaceContainerLowest,
                    focusedBorderColor = MobClawColors.Primary.copy(alpha = 0.3f),
                    unfocusedBorderColor = MobClawColors.OutlineVariant.copy(alpha = 0.1f),
                    cursorColor = MobClawColors.Primary,
                ),
            )

            // Base URL for Ollama/OpenRouter
            if (config.type == ProviderType.OLLAMA || config.type == ProviderType.OPENROUTER) {
                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = { onConfigUpdate(config.copy(baseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (config.type == ProviderType.OLLAMA) "http://localhost:11434"
                            else "https://openrouter.ai/api/v1",
                            style = MaterialTheme.typography.bodySmall,
                            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.3f),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MobClawColors.OnSurface,
                        fontFamily = SpaceGroteskFamily,
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MobClawColors.SurfaceContainerLowest,
                        unfocusedContainerColor = MobClawColors.SurfaceContainerLowest,
                        focusedBorderColor = MobClawColors.Primary.copy(alpha = 0.3f),
                        unfocusedBorderColor = MobClawColors.OutlineVariant.copy(alpha = 0.1f),
                        cursorColor = MobClawColors.Primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
