package com.replog.app.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import com.replog.app.util.Format

@Composable
fun WorkoutScreen(
    onOpenWorkout: (Long) -> Unit,
    onCreateWorkout: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "History", "PRs")

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Workout", style = MaterialTheme.typography.displaySmall)
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i; if (i == 1) viewModel.refresh() },
                    text = { Text(label) }
                )
            }
        }
        when (tab) {
            0 -> OverviewTab(onOpenWorkout, onCreateWorkout, viewModel)
            1 -> HistoryTab(viewModel)
            else -> PRsTab(viewModel)
        }
    }
}

@Composable
private fun OverviewTab(
    onOpenWorkout: (Long) -> Unit,
    onCreateWorkout: () -> Unit,
    viewModel: WorkoutViewModel
) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("This week", "${state.weekSessions}/${state.weekTarget}", Modifier.weight(1f))
                StatPill("Streak", "${state.gymStreak} d", Modifier.weight(1f))
                StatPill("Volume 30d", Format.kcal(state.volume30d) + " kg", Modifier.weight(1.4f))
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("RECENT WORKOUTS")
                    if (state.recent.isEmpty()) {
                        EmptyState("No workouts yet", "Tap + to log your first session.")
                    } else {
                        state.recent.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onOpenWorkout(w.id) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${Format.dateLabel(w.epochDay)} · ${w.durationMin} min",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    typeLabel(w.type),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("CARDIO")
                    if (state.cardio.isEmpty()) {
                        EmptyState("No cardio yet", "Log a run, ride or swim via the + button.")
                    } else {
                        state.cardio.forEach { c ->
                            KeyValueRow(
                                "${typeLabel(c.type)} · ${Format.dateLabel(c.epochDay)}",
                                "${Format.oneDecimal(c.distanceKm)} km in ${c.durationMin.toInt()} min"
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    RpCard(modifier) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryTab(viewModel: WorkoutViewModel) {
    val h by viewModel.history.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("TRAINING HISTORY")
                    KeyValueRow("Total workouts", h.total.toString())
                    KeyValueRow("This week", h.thisWeek.toString())
                    KeyValueRow("This month", h.thisMonth.toString())
                    KeyValueRow("Current streak", "${h.currentStreak} days")
                    KeyValueRow("Longest streak", "${h.longestStreak} days")
                    KeyValueRow("Avg duration (30d)", Format.oneDecimal(h.avgDuration) + " min")
                    KeyValueRow("Total volume (30d)", Format.kcal(h.totalVolume) + " kg")
                }
            }
        }
    }
}

@Composable
private fun PRsTab(viewModel: WorkoutViewModel) {
    val prs by viewModel.prs.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Personal records are detected automatically from your logged sets.", 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (prs.isEmpty()) {
            item { EmptyState("No records yet", "Log a few sets and your bests will show up here.") }
        } else {
            items(prs.size) { i ->
                val pr = prs[i]
                RpCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.exerciseName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Best ${Format.oneDecimal(pr.bestWeightKg)} kg × ${pr.bestRepsAtBest}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "e1RM ${Format.oneDecimal(pr.estimatedOneRmKg)} kg · volume ${Format.kcal(pr.totalVolumeKg)} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        pr.previousBestKg?.let { prev ->
                            val delta = pr.bestWeightKg - prev
                            Text(
                                signed(delta) + " kg",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (delta > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

internal fun signed(v: Double): String =
    if (v > 0) "+" + Format.oneDecimal(v) else Format.oneDecimal(v)

internal fun typeLabel(type: String): String =
    when (type.uppercase()) {
        "PUSH" -> "Push"; "PULL" -> "Pull"; "LEGS" -> "Legs"; "UPPER" -> "Upper";
        "LOWER" -> "Lower"; "FULL_BODY" -> "Full Body"; "CARDIO" -> "Cardio";
        "RUNNING" -> "Running"; "WALKING" -> "Walking"; "CYCLING" -> "Cycling";
        "SWIMMING" -> "Swimming"; "OTHER" -> "Other";
        else -> type.lowercase().replaceFirstChar { it.uppercase() }
    }
