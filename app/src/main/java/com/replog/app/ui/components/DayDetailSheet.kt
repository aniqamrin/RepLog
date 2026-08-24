package com.replog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.replog.app.domain.model.ActivityEvent
import com.replog.app.domain.model.ActivityType
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    epochDay: Long,
    events: List<ActivityEvent>,
    onDismiss: () -> Unit
) {
    val date = LocalDate.ofEpochDay(epochDay)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) +
                    ", " + date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) +
                    " " + date.dayOfMonth,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (events.isEmpty()) "No activity recorded" else "${events.size} activities",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            if (events.isEmpty()) {
                EmptyState("Rest day", "Nothing was logged on this day.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events.size) { i ->
                        val e = events[i]
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emojiFor(e.type), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(0.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                if (!e.detail.isNullOrBlank()) {
                                    Text(
                                        e.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            e.value?.let { v ->
                                Text(
                                    valueLabel(e.type, v),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun emojiFor(type: ActivityType): String = when (type) {
    ActivityType.GYM -> "🏋️"
    ActivityType.FOOD -> "🍗"
    ActivityType.PROTEIN -> "🥩"
    ActivityType.CARDIO -> "🏃"
    ActivityType.STEPS -> "🚶"
    ActivityType.WATER -> "💧"
    ActivityType.WEIGHT -> "⚖️"
    ActivityType.HABIT -> "✅"
}

private fun valueLabel(type: ActivityType, v: Double): String = when (type) {
    ActivityType.FOOD -> "${v.toInt()} kcal"
    ActivityType.PROTEIN -> "${v.toInt()} g"
    ActivityType.CARDIO -> "${com.replog.app.util.Format.oneDecimal(v)} km"
    ActivityType.STEPS -> "${v.toInt()}"
    ActivityType.WATER -> "${v.toInt()} ml"
    ActivityType.WEIGHT -> "${com.replog.app.util.Format.oneDecimal(v)} kg"
    else -> ""
}
