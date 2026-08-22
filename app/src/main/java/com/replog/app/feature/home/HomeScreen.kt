package com.replog.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.WeekStats
import com.replog.app.ui.components.DayDetailSheet
import com.replog.app.ui.components.HeatmapCalendar
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.RingProgress
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import com.replog.app.ui.components.StatBar
import com.replog.app.util.Format
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenCheckIn: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmapState.collectAsStateWithLifecycle()
    val extras by viewModel.extrasState.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val selectedDayEvents by viewModel.selectedDayEvents.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(greeting(), style = MaterialTheme.typography.displaySmall)
                Text(
                    "Here's your progress today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { TodayProgressCard(state) }
        item { StreaksCard(extras.streaks) }
        item {
            HeatmapCard(
                heatmap = heatmap,
                daysAscending = viewModel.days365,
                onSelectFilter = viewModel::setFilter,
                onDayClick = viewModel::selectDay
            )
        }
        item { WeeklySummaryCard(extras.thisWeek, extras.lastWeek, state.goals.workoutDaysPerWeek) }
        item { TimelineCard(timeline, viewModel.today) }
        item { Spacer(Modifier.height(64.dp)) }
    }

    selectedDayEvents?.let { (day, events) ->
        DayDetailSheet(epochDay = day, events = events, onDismiss = { viewModel.selectDay(null) })
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning 👋"
        hour < 18 -> "Good afternoon 👋"
        else -> "Good evening 👋"
    }
}

@Composable
private fun TodayProgressCard(state: HomeUiState) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("TODAY")
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingProgress(
                        progress = if (state.goals.calorieTarget > 0)
                            (state.calories / state.goals.calorieTarget).toFloat() else 0f,
                        content = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Format.kcal(state.calories), style = MaterialTheme.typography.titleMedium)
                                Text("kcal", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Calories", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingProgress(
                        progress = if (state.goals.proteinTargetG > 0)
                            (state.protein / state.goals.proteinTargetG).toFloat() else 0f,
                        content = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Format.int(state.protein), style = MaterialTheme.typography.titleMedium)
                                Text("g", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Protein", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingProgress(
                        progress = if (state.goals.waterTargetMl > 0)
                            (state.waterMl / state.goals.waterTargetMl).toFloat() else 0f,
                        content = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Format.oneDecimal(state.waterMl / 1000.0), style = MaterialTheme.typography.titleMedium)
                                Text("L", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Water", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            StatBar(
                label = "Steps",
                valueText = Format.thousands(state.steps),
                targetText = Format.thousands(state.goals.stepTarget),
                progress = if (state.goals.stepTarget > 0)
                    state.steps.toFloat() / state.goals.stepTarget else 0f
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Workout", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.workoutDoneToday) "Completed ✓" else "Not yet",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.workoutDoneToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StreaksCard(streaks: com.replog.app.domain.model.Streaks?) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("STREAKS", trailing = streaks?.longestOverall?.let { "best ${it}d" })
            Spacer(Modifier.height(10.dp))
            if (streaks == null) {
                Text("…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreakChip("Gym", streaks.gym)
                    StreakChip("Nutrition", streaks.nutrition)
                    StreakChip("Protein", streaks.protein)
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Overall activity", "${streaks.overall} day streak")
            }
        }
    }
}

@Composable
private fun StreakChip(label: String, days: Int) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$days d",
            style = MaterialTheme.typography.titleMedium,
            color = if (days > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatmapCard(
    heatmap: HeatmapState,
    daysAscending: List<Long>,
    onSelectFilter: (ActivityType?) -> Unit,
    onDayClick: (Long) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("LAST 365 DAYS")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(
                    selected = heatmap.filter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("All") },
                    colors = chipColors()
                ) }
                items(filterOptions.size) { i ->
                    val opt = filterOptions[i]
                    FilterChip(
                        selected = heatmap.filter == opt,
                        onClick = { onSelectFilter(opt) },
                        label = { Text(opt.label) },
                        colors = chipColors()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HeatmapCalendar(
                daysAscending = daysAscending,
                levels = heatmap.levels,
                darkTheme = dark,
                selectedDay = null,
                onDayClick = onDayClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private val filterOptions = listOf(
    ActivityType.GYM, ActivityType.FOOD, ActivityType.PROTEIN, ActivityType.CARDIO,
    ActivityType.STEPS, ActivityType.WATER, ActivityType.HABIT, ActivityType.WEIGHT
)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
private fun WeeklySummaryCard(thisWeek: WeekStats?, lastWeek: WeekStats?, workoutTarget: Int) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("THIS WEEK")
            if (thisWeek == null) {
                EmptyInline("No data for this week yet.")
            } else {
                Spacer(Modifier.height(6.dp))
                DeltaRow("Gym", "${thisWeek.gymSessions} / $workoutTarget days",
                    delta = percentDelta(thisWeek.gymSessions.toDouble(), lastWeek?.gymSessions?.toDouble()))
                DeltaRow("Calories", Format.kcal(thisWeek.calories) + " kcal",
                    sub = "avg ${Format.kcal(thisWeek.avgCalories)}/day",
                    delta = percentDelta(thisWeek.calories, lastWeek?.calories))
                DeltaRow("Protein", Format.kcal(thisWeek.protein) + " g",
                    sub = "avg ${Format.int(thisWeek.avgProtein)} g/day",
                    delta = percentDelta(thisWeek.protein, lastWeek?.protein))
                DeltaRow("Steps", Format.thousands(thisWeek.steps.toInt()),
                    delta = percentDelta(thisWeek.steps.toDouble(), lastWeek?.steps?.toDouble()))
                DeltaRow("Consistency", "${thisWeek.consistencyPct}%",
                    delta = thisWeek.consistencyPct - (lastWeek?.consistencyPct ?: thisWeek.consistencyPct),
                    deltaIsPoints = true)
            }
        }
    }
}

@Composable
private fun DeltaRow(
    label: String,
    value: String,
    sub: String? = null,
    delta: Int?,
    deltaIsPoints: Boolean = false
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
        delta?.let { d ->
            Spacer(Modifier.width(10.dp))
            val text = if (deltaIsPoints) signed(d) + " pts" else signed(d) + "%"
            val color = when {
                d > 0 -> MaterialTheme.colorScheme.primary
                d < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

private fun signed(v: Int): String = if (v > 0) "+$v" else "$v"

private fun percentDelta(current: Double?, previous: Double?): Int? {
    if (current == null || previous == null || previous <= 0.0) return null
    return (((current - previous) / previous) * 100).toInt()
}

@Composable
private fun TimelineCard(events: List<com.replog.app.domain.model.ActivityEvent>, today: Long) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("ACTIVITY")
            if (events.isEmpty()) {
                EmptyInline("Your logged activity will appear here.")
            } else {
                var currentGroup: Long? = null
                events.take(24).forEach { e ->
                    if (e.epochDay != currentGroup) {
                        currentGroup = e.epochDay
                        Spacer(Modifier.height(8.dp))
                        Text(
                            groupLabel(e.epochDay, today),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TimelineEventRow(e)
                }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(e: com.replog.app.domain.model.ActivityEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emojiOf(e.type), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge)
            e.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        e.value?.let { v ->
            Text(valueText(e.type, v), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun groupLabel(day: Long, today: Long): String = when (day) {
    today -> "Today"
    today - 1 -> "Yesterday"
    else -> Format.dateLabel(day)
}

private fun emojiOf(type: ActivityType): String = when (type) {
    ActivityType.GYM -> "🏋️"
    ActivityType.FOOD -> "🍗"
    ActivityType.PROTEIN -> "🥩"
    ActivityType.CARDIO -> "🏃"
    ActivityType.STEPS -> "🚶"
    ActivityType.WATER -> "💧"
    ActivityType.WEIGHT -> "⚖️"
    ActivityType.HABIT -> "✅"
}

private fun valueText(type: ActivityType, v: Double): String = when (type) {
    ActivityType.FOOD -> "${v.toInt()} kcal"
    ActivityType.PROTEIN -> "${v.toInt()} g"
    ActivityType.WATER -> "${v.toInt()} ml"
    ActivityType.STEPS -> Format.thousands(v.toInt())
    ActivityType.CARDIO -> "${Format.oneDecimal(v)} km"
    ActivityType.WEIGHT -> "${Format.oneDecimal(v)} kg"
    else -> ""
}

@Composable
private fun EmptyInline(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
