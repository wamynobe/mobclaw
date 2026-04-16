package com.wamynobe.mobclaw.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wamynobe.mobclaw.ui.state.MobClawAppState
import com.wamynobe.mobclaw.ui.state.TaskHistoryEntry
import com.wamynobe.mobclaw.ui.theme.MobClawColors
import com.wamynobe.mobclaw.ui.theme.SpaceGroteskFamily

/**
 * Tasks & History screen.
 */
@Composable
fun TaskHistoryScreen(onExecuteTask: (String) -> Unit) {
    var taskInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
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
            Column {
                Text(
                    text = "MOBCLAW",
                    style = MaterialTheme.typography.titleMedium,
                    color = MobClawColors.Primary,
                    fontFamily = SpaceGroteskFamily,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Task History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MobClawColors.OnSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "ACTIVE THREADS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "%02d".format(state.taskHistory.count { it.success }),
                    style = MaterialTheme.typography.displaySmall,
                    color = MobClawColors.OnSurface,
                    fontFamily = SpaceGroteskFamily,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Task list
        if (state.taskHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No tasks yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        "Execute a task from the Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.taskHistory) { entry ->
                    TaskCard(entry)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Task input at bottom
        OutlinedTextField(
            value = taskInput,
            onValueChange = { taskInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Instruct MobClaw...",
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MobClawColors.OnSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (taskInput.isNotBlank()) {
                        focusManager.clearFocus()
                        onExecuteTask(taskInput.trim())
                        taskInput = ""
                    }
                },
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MobClawColors.SurfaceContainerLowest,
                unfocusedContainerColor = MobClawColors.SurfaceContainerLowest,
                focusedBorderColor = MobClawColors.Primary.copy(alpha = 0.3f),
                unfocusedBorderColor = MobClawColors.OutlineVariant.copy(alpha = 0.15f),
                cursorColor = MobClawColors.Primary,
            ),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TaskCard(entry: TaskHistoryEntry) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = if (entry.success) MobClawColors.StatusSuccess else MobClawColors.Error
    val statusText = if (entry.success) "SUCCESS" else "FAILED"
    val timeAgo = formatTimeAgo(entry.timestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MobClawColors.SurfaceContainer)
            .clickable { expanded = !expanded }
            .padding(20.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
            Text(
                text = timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
            )
        }

        // Task name
        Text(
            text = entry.task,
            style = MaterialTheme.typography.titleMedium,
            color = MobClawColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Result message preview
        if (entry.message.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.08f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RESULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontFamily = SpaceGroteskFamily,
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MobClawColors.OnSurface,
                )
            }
        }

        // Duration & iterations
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Text(
                    text = "LATENCY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = "${entry.duration.inWholeSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MobClawColors.Tertiary,
                    fontFamily = SpaceGroteskFamily,
                )
            }
            Column {
                Text(
                    text = "ITERATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = entry.iterations.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MobClawColors.Tertiary,
                    fontFamily = SpaceGroteskFamily,
                )
            }
            Column {
                Text(
                    text = "PROVIDER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MobClawColors.OnSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = entry.provider.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MobClawColors.Primary,
                    fontFamily = SpaceGroteskFamily,
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
