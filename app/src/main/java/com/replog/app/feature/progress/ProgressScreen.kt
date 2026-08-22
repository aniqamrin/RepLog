package com.replog.app.feature.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.domain.model.DailyFacts
import com.replog.app.ui.components.BarChart
import com.replog.app.ui.components.BarEntry
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.LineChart
import com.replog.app.ui.components.RingProgress
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import com.replog.app.util.Format

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Progress", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Trends over time",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ProgressRange.entries.forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { viewModel.setRange(r) },
                        label = { Text(r.label) }
                    )
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader(
                        "BODY WEIGHT",
                        trailing = state.currentWeight?.let { "${Format.oneDecimal(it)} kg" }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (state.weightSeries.size < 2) {
                        EmptyState("Not enough data", "Log your weight a few times to see the trend.")
                    } else {
                        LineChart(values = state.weightSeries)
                        Spacer(Modifier.height(8.dp))
                        state.weightChangeKg?.let { change ->
                            val dir = when {
                                change < -0.05 -> "▼"
                                change > 0.05 -> "▲"
                                else -> "≈"
                            }
                            Text(
                                "$dir ${Format.oneDecimal(kotlin.math.abs(change))} kg over ${state.range.label.lowercase()}" +
                                    (state.weightPerWeekKg?.let { " · ${Format.oneDecimal(it)} kg/week" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RingProgress(
                        progress = state.consistency.overall / 100f,
                        size = 92.dp,
                        stroke = 9.dp,
                        content = {
                            Text(
                                "${state.consistency.overall}%",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        SectionHeader("CONSISTENCY")
                        state.consistency.byMetric.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                            KeyValueRow(k, "$v%")
                        }
                    }
                }
            }
        }
        item { MonthlyCard(state.monthly) }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("GYM SESSIONS · LAST 10 WEEKS")
                    BarChart(
                        entries = state.gymPerWeekBars.mapIndexed { i, v ->
                            BarEntry(label = if (i % 2 == 0) "${i - 9}" else null, value = v, highlight = v >= 4f)
                        }
                    )
                }
            }
        }
        item { StrengthCard(state.strengthDeltas) }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun MonthlyCard(monthly: com.replog.app.data.repository.MonthlySummaryData?) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("LAST 30 DAYS")
            if (monthly == null) {
                EmptyState("Gathering data…", "Your monthly summary will appear here.")
            } else {
                KeyValueRow("Gym sessions", monthly.sessions.toString())
                KeyValueRow("Average calories", Format.kcal(monthly.avgCalories) + " kcal")
                KeyValueRow("Average protein", Format.int(monthly.avgProtein) + " g")
                KeyValueRow("Total cardio", Format.oneDecimal(monthly.totalCardioKm) + " km")
                KeyValueRow("Average steps", Format.thousands(monthly.avgSteps.toInt()))
                monthly.weightChangeKg?.let {
                    val sign = if (it > 0) "+" else ""
                    KeyValueRow("Weight change", sign + Format.oneDecimal(it) + " kg")
                }
                KeyValueRow("Consistency", "${monthly.consistencyPct}%")
            }
        }
    }
}

@Composable
private fun StrengthCard(deltas: Map<String, Pair<Double?, Double>>) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            SectionHeader("STRENGTH")
            if (deltas.isEmpty()) {
                EmptyState("No lifts tracked yet", "Log weighted exercises to see progression.")
            } else {
                deltas.entries.sortedBy { it.key }.take(6).forEach { (name, pair) ->
                    val prev = pair.first
                    val best = pair.second
                    val deltaText = prev?.let { p ->
                        val d = best - p
                        if (kotlin.math.abs(d) >= 0.5) " (${if (d > 0) "+" else ""}${Format.oneDecimal(d)} kg)" else ""
                    } ?: ""
                    KeyValueRow(name, "${Format.oneDecimal(best)} kg$deltaText")
                }
            }
        }
    }
}
