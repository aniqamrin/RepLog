package com.replog.app.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.domain.logic.NutritionMath
import com.replog.app.ui.components.KeyValueRow
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import com.replog.app.ui.components.StatBar
import com.replog.app.util.Format

@Composable
fun NutritionScreen(
    onScanFood: () -> Unit,
    onAddFood: (onSaved: () -> Unit) -> Unit,
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val goals = state.goals

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Nutrition", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Today's intake",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("MACROS", trailing = "${state.totals.calories.toInt()} kcal")
                    Spacer(Modifier.height(10.dp))
                    StatBar(
                        label = "Calories",
                        valueText = Format.kcal(state.totals.calories),
                        targetText = "${goals.calorieTarget} kcal",
                        progress = if (goals.calorieTarget > 0) (state.totals.calories / goals.calorieTarget).toFloat() else 0f,
                        accent = true
                    )
                    Spacer(Modifier.height(12.dp))
                    StatBar(
                        label = "Protein",
                        valueText = Format.int(state.totals.protein) + " g",
                        targetText = "${goals.proteinTargetG} g",
                        progress = if (goals.proteinTargetG > 0) (state.totals.protein / goals.proteinTargetG).toFloat() else 0f
                    )
                    Spacer(Modifier.height(12.dp))
                    StatBar(
                        label = "Carbs",
                        valueText = Format.int(state.totals.carbs) + " g",
                        targetText = "${goals.carbsTargetG} g",
                        progress = if (goals.carbsTargetG > 0) (state.totals.carbs / goals.carbsTargetG).toFloat() else 0f
                    )
                    Spacer(Modifier.height(12.dp))
                    StatBar(
                        label = "Fat",
                        valueText = Format.int(state.totals.fat) + " g",
                        targetText = "${goals.fatTargetG} g",
                        progress = if (goals.fatTargetG > 0) (state.totals.fat / goals.fatTargetG).toFloat() else 0f
                    )
                    Spacer(Modifier.height(12.dp))
                    StatBar(
                        label = "Fiber",
                        valueText = Format.int(state.totals.fiber) + " g",
                        targetText = "${goals.fiberTargetG} g",
                        progress = if (goals.fiberTargetG > 0) (state.totals.fiber / goals.fiberTargetG).toFloat() else 0f
                    )
                    Spacer(Modifier.height(14.dp))
                    KeyValueRow(
                        "Remaining calories",
                        NutritionMath.remaining(goals.calorieTarget, state.totals.calories).toString() + " kcal",
                        valueAccent = true
                    )
                    KeyValueRow(
                        "Remaining protein",
                        NutritionMath.remaining(goals.proteinTargetG, state.totals.protein).toString() + " g",
                        valueAccent = true
                    )
                }
            }
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader("WATER", trailing = "${Format.oneDecimal(state.waterMl / 1000.0)} / ${Format.oneDecimal(goals.waterTargetMl / 1000.0)} L")
                    Spacer(Modifier.height(8.dp))
                    StatBar(
                        label = "",
                        valueText = "",
                        targetText = null,
                        progress = if (goals.waterTargetMl > 0) (state.waterMl / goals.waterTargetMl.toFloat()) else 0f,
                        accent = false
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(250, 500, 750).forEach { ml ->
                            androidx.compose.material3.AssistChip(
                                onClick = { viewModel.addWater(ml) },
                                label = { Text("+$ml ml") }
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Meals", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onAddFood {} }) { Text("+ Add food") }
                androidx.compose.material3.TextButton(onClick = onScanFood) { Text("📷 Scan") }
            }
        }
        state.meals.forEach { meal ->
            item(key = meal.label) {
                RpCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader(meal.label.uppercase(), trailing = if (meal.entries.isEmpty()) null
                        else "${meal.calories} kcal · ${Format.int(meal.protein)}g protein")
                        if (meal.entries.isEmpty()) {
                            Text(
                                "Nothing logged",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            meal.entries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            buildString {
                                                append("${entry.calories} kcal · P ${Format.int(entry.proteinG)} C ${Format.int(entry.carbsG)} F ${Format.int(entry.fatG)}")
                                                entry.servingSize?.let { append(" · $it") }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteFood(entry.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}
