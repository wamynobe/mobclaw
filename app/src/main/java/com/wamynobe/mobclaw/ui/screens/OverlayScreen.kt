package com.wamynobe.mobclaw.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wamynobe.mobclaw.ui.state.AgentUiState
import com.wamynobe.mobclaw.ui.state.DebugLogEntry
import com.wamynobe.mobclaw.ui.state.LogLevel
import com.wamynobe.mobclaw.ui.state.MobClawAppState
import com.wamynobe.mobclaw.ui.theme.MobClawColors
import com.wamynobe.mobclaw.ui.theme.SpaceGroteskFamily

/**
 * Debug Overlay screen — matches "Live Debug Overlay" Stitch design.
 * Shows real-time debug console, current action, and stop button.
 */
@Composable
fun OverlayScreen(
    onStopAgent: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
) {
    val state = MobClawAppState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SYSTEM STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (state.agentState) {
                        AgentUiState.RUNNING -> "ACTIVE"
                        AgentUiState.SUCCESS -> "COMPLETED"
                        AgentUiState.FAILED -> "ERROR"
                        AgentUiState.IDLE -> "STANDBY"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (state.agentState) {
                        AgentUiState.RUNNING -> MobClawColors.PrimaryContainer
                        AgentUiState.SUCCESS -> MobClawColors.StatusSuccess
                        AgentUiState.FAILED -> MobClawColors.Error
                        AgentUiState.IDLE -> MobClawColors.Tertiary
                    },
                    fontFamily = SpaceGroteskFamily,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug console header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💻", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DEBUG_CONSOLE",
                style = MaterialTheme.typography.titleSmall,
                color = MobClawColors.OnSurface,
                fontFamily = SpaceGroteskFamily,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Console log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MobClawColors.SurfaceContainerLowest),
        ) {
            if (state.debugLog.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔍", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Waiting for agent activity...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(state.debugLog.size) {
                    if (state.debugLog.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.debugLog) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permissions status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenAccessibility,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Filled.Accessibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state.accessibilityEnabled) MobClawColors.StatusSuccess
                    else MobClawColors.Error,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.accessibilityEnabled) "A11y ✓" else "A11y ✗",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = onOpenOverlayPermission,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state.overlayPermissionGranted) MobClawColors.StatusSuccess
                    else MobClawColors.Error,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.overlayPermissionGranted) "Overlay ✓" else "Overlay ✗",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stop button
        Button(
            onClick = onStopAgent,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.agentState == AgentUiState.RUNNING,
            colors = ButtonDefaults.buttonColors(
                containerColor = MobClawColors.PrimaryContainer,
                contentColor = MobClawColors.OnPrimaryContainer,
                disabledContainerColor = MobClawColors.SurfaceContainerHigh,
                disabledContentColor = MobClawColors.OnSurfaceVariant.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Agent", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LogLine(entry: DebugLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Timestamp
        Text(
            text = "[${entry.timestamp}]",
            style = MaterialTheme.typography.labelSmall,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
            fontFamily = SpaceGroteskFamily,
        )
        // Level indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when (entry.level) {
                        LogLevel.INFO -> MobClawColors.Tertiary
                        LogLevel.ACTION -> MobClawColors.PrimaryContainer
                        LogLevel.WARNING -> MobClawColors.Primary
                        LogLevel.ERROR -> MobClawColors.Error
                    }
                )
                .align(Alignment.CenterVertically),
        )
        // Message
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall,
            color = when (entry.level) {
                LogLevel.ERROR -> MobClawColors.Error
                LogLevel.WARNING -> MobClawColors.Primary
                LogLevel.ACTION -> MobClawColors.PrimaryContainer
                LogLevel.INFO -> MobClawColors.OnSurface.copy(alpha = 0.8f)
            },
            fontFamily = SpaceGroteskFamily,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
