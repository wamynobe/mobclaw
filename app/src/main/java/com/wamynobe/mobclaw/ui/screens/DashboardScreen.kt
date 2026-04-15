package com.wamynobe.mobclaw.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wamynobe.mobclaw.ui.state.AgentUiState
import com.wamynobe.mobclaw.ui.state.MobClawAppState
import com.wamynobe.mobclaw.ui.theme.MobClawColors
import com.wamynobe.mobclaw.ui.theme.SpaceGroteskFamily

/**
 * Dashboard screen — matches "MobClaw Dashboard" Stitch design.
 * Shows agent state, progress gauge, stats, and task input.
 */
@Composable
fun DashboardScreen(
    onExecuteTask: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
) {
    var taskInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val state = MobClawAppState

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            TopBar()

            // Permission warnings
            if (!state.accessibilityEnabled || !state.overlayPermissionGranted) {
                PermissionWarnings(
                    accessibilityOk = state.accessibilityEnabled,
                    overlayOk = state.overlayPermissionGranted,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlay = onOpenOverlayPermission,
                )
            }

            // Agent state card
            AgentStateCard(state.agentState)

            // Progress gauge
            ProgressGauge(
                progress = when (state.agentState) {
                    AgentUiState.RUNNING -> 0.65f
                    AgentUiState.SUCCESS -> 1.0f
                    else -> 0f
                },
                isRunning = state.agentState == AgentUiState.RUNNING,
            )

            // Stats row
            StatsRow(
                tasksExecuted = state.totalTasksExecuted,
                totalIterations = state.totalIterations,
            )

            // Metrics card
            MetricsCard(provider = state.activeProvider.label)

            // Task input
            TaskInputField(
                value = taskInput,
                onValueChange = { taskInput = it },
                onExecute = {
                    if (taskInput.isNotBlank()) {
                        focusManager.clearFocus()
                        onExecuteTask(taskInput.trim())
                        taskInput = ""
                    }
                },
                isRunning = state.agentState == AgentUiState.RUNNING,
            )

            Spacer(modifier = Modifier.height(80.dp))
        }

        // FAB
        if (state.agentState != AgentUiState.RUNNING && taskInput.isNotBlank()) {
            FloatingActionButton(
                onClick = {
                    focusManager.clearFocus()
                    onExecuteTask(taskInput.trim())
                    taskInput = ""
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MobClawColors.PrimaryContainer,
                contentColor = MobClawColors.OnPrimaryContainer,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Execute")
            }
        }
    }
}

@Composable
private fun TopBar() {
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
        // Lightning bolt indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MobClawColors.PrimaryContainer.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚡", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PermissionWarnings(
    accessibilityOk: Boolean,
    overlayOk: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MobClawColors.ErrorContainer.copy(alpha = 0.15f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MobClawColors.Error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Permissions Required",
                style = MaterialTheme.typography.titleSmall,
                color = MobClawColors.Error,
            )
        }
        if (!accessibilityOk) {
            TextButton(onClick = onOpenAccessibility) {
                Text("❌ Enable Accessibility Service", color = MobClawColors.OnSurface)
            }
        }
        if (!overlayOk) {
            TextButton(onClick = onOpenOverlay) {
                Text("❌ Grant Overlay Permission", color = MobClawColors.OnSurface)
            }
        }
    }
}

@Composable
private fun AgentStateCard(agentState: AgentUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MobClawColors.SurfaceContainer)
            .padding(24.dp),
    ) {
        Text(
            text = "CURRENT STATE",
            style = MaterialTheme.typography.labelSmall,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (agentState) {
                            AgentUiState.RUNNING -> MobClawColors.PrimaryContainer.copy(alpha = pulseAlpha)
                            AgentUiState.SUCCESS -> MobClawColors.StatusSuccess
                            AgentUiState.FAILED -> MobClawColors.Error
                            AgentUiState.IDLE -> MobClawColors.Tertiary.copy(alpha = 0.5f)
                        }
                    ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when (agentState) {
                    AgentUiState.RUNNING -> "RUNNING"
                    AgentUiState.SUCCESS -> "COMPLETED"
                    AgentUiState.FAILED -> "FAILED"
                    AgentUiState.IDLE -> "IDLE"
                },
                style = MaterialTheme.typography.displaySmall,
                color = MobClawColors.OnSurface,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (agentState) {
                AgentUiState.RUNNING -> "Autonomous agent is currently parsing node structures and executing sequential logic optimizations."
                AgentUiState.SUCCESS -> "Task completed successfully."
                AgentUiState.FAILED -> "Agent encountered an error during execution."
                AgentUiState.IDLE -> "Agent is ready. Enter a task below to begin."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ProgressGauge(progress: Float, isRunning: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "gaugeProgress",
    )

    val sweepAngle = animatedProgress * 180f
    val primaryBrush = Brush.linearGradient(
        colors = listOf(MobClawColors.Primary, MobClawColors.PrimaryContainer),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp, 120.dp)) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = Size(size.width, size.width)
            val topLeft = Offset(0f, 0f)

            // Track
            drawArc(
                color = MobClawColors.SurfaceContainerHighest,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // Progress
            if (sweepAngle > 0f) {
                drawArc(
                    brush = primaryBrush,
                    startAngle = 180f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        // Percentage text
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineLarge,
            color = MobClawColors.Primary,
            fontFamily = SpaceGroteskFamily,
            modifier = Modifier.padding(top = 40.dp),
        )
    }
}

@Composable
private fun StatsRow(tasksExecuted: Int, totalIterations: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = "✦",
            value = tasksExecuted.toString(),
            label = "Tasks Executed",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = "⚙",
            value = totalIterations.toString(),
            label = "Total Iterations",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MobClawColors.SurfaceContainer)
            .padding(20.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MobClawColors.OnSurface,
            fontFamily = SpaceGroteskFamily,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MetricsCard(provider: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MobClawColors.SurfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Metrics",
                style = MaterialTheme.typography.titleMedium,
                color = MobClawColors.OnSurface,
            )
        }
        MetricRow("Active Provider", provider)
        MetricRow("Process Core", "Claw_v0.1.0")
        MetricRow("Status", "Optimized")
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MobClawColors.SurfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MobClawColors.Tertiary,
        )
    }
}

@Composable
private fun TaskInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onExecute: () -> Unit,
    isRunning: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isRunning,
        placeholder = {
            Text(
                "Instruct MobClaw...",
                color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MobClawColors.OnSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onExecute() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MobClawColors.SurfaceContainerLowest,
            unfocusedContainerColor = MobClawColors.SurfaceContainerLowest,
            focusedBorderColor = MobClawColors.Primary.copy(alpha = 0.3f),
            unfocusedBorderColor = MobClawColors.OutlineVariant.copy(alpha = 0.15f),
            cursorColor = MobClawColors.Primary,
        ),
        minLines = 2,
        maxLines = 4,
    )
    if (isRunning) {
        Text(
            text = "Agent is running...",
            style = MaterialTheme.typography.labelSmall,
            color = MobClawColors.PrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
