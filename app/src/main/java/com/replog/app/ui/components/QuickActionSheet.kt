package com.replog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class QuickAction(val label: String, val emoji: String) {
    LOG_FOOD("Log Food", "🍗"),
    SCAN_FOOD("Scan Food", "📷"),
    LOG_WORKOUT("Log Workout", "🏋️"),
    LOG_CARDIO("Log Cardio", "🏃"),
    LOG_WEIGHT("Log Weight", "⚖️"),
    LOG_WATER("Log Water", "💧"),
    CHECK_IN("Check In", "✅")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionSheet(
    onAction: (QuickAction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp)) {
            Text("Quick actions", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            val rows = QuickAction.entries.chunked(3)
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { action ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onAction(action) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(action.emoji, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                action.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                    }
                    if (row.size < 3) {
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
