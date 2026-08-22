package com.replog.app.feature.scanner

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import java.io.File
import java.time.LocalTime

@Composable
fun ReviewScreen(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    onDone: () -> Unit
) {
    var meal by remember { mutableStateOf("LUNCH") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Scan result", style = MaterialTheme.typography.displaySmall)
            Text(
                "AI estimate — verify and edit before saving",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            state.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Scanned meal",
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
        }
        item { SectionHeader("DETECTED FOODS") }
        items(state.foods.size) { i ->
            FoodEditorCard(
                food = state.foods[i],
                onUpdate = { viewModel.updateFood(i, it) },
                onRemove = { viewModel.removeFood(i) }
            )
        }
        item {
            RpCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "~${viewModel.totalCalories()} kcal · ~${com.replog.app.util.Format.int(viewModel.totalProtein())}g protein",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("AI estimate", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column {
                SectionHeader("MEAL")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    val now = LocalTime.now()
                    viewModel.saveAll(meal, now.hour * 60 + now.minute, onDone)
                },
                enabled = !state.saving && state.foods.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (state.saving) "Adding…" else "Add to today") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                Text("Retake photo")
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun FoodEditorCard(
    food: DetectedFood,
    onUpdate: (DetectedFood) -> Unit,
    onRemove: () -> Unit
) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = food.name,
                    onValueChange = { v -> onUpdate(food.copy(name = v)) },
                    label = { Text("Food") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp).clickable(onClick = onRemove)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Portion", food.portion, Modifier.weight(1f)) { v ->
                    onUpdate(food.copy(portion = v))
                }
                SmallNumberField("kcal", if (food.calories == 0) "" else food.calories.toString(), Modifier.weight(1f)) { v ->
                    onUpdate(food.copy(calories = v.filter(Char::isDigit).toIntOrNull() ?: 0))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Protein g", num(food.protein), Modifier.weight(1f)) { v ->
                    onUpdate(food.copy(protein = v.toDoubleOrNull() ?: 0.0))
                }
                SmallNumberField("Carbs g", num(food.carbs), Modifier.weight(1f)) { v ->
                    onUpdate(food.copy(carbs = v.toDoubleOrNull() ?: 0.0))
                }
                SmallNumberField("Fat g", num(food.fat), Modifier.weight(1f)) { v ->
                    onUpdate(food.copy(fat = v.toDoubleOrNull() ?: 0.0))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Confidence: ${food.confidence}",
                style = MaterialTheme.typography.labelMedium,
                color = when (food.confidence) {
                    "high" -> MaterialTheme.colorScheme.primary
                    "low" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun num(v: Double): String =
    if (v == 0.0) "" else com.replog.app.util.Format.oneDecimal(v)

@Composable
private fun SmallNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true
    )
}

@Composable
fun ErrorScreen(state: ScannerUiState, viewModel: ScannerViewModel, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyState("Scan failed", state.errorMessage)
        Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
