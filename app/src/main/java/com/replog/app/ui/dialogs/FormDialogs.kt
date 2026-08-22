package com.replog.app.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.replog.app.domain.model.CardioType

@Composable
fun AddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, meal: String, serving: String?, kcal: Int, p: Double, c: Double, f: Double, fib: Double) -> Unit,
    viewModel: QuickLogViewModel
) {
    var name by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf("LUNCH") }
    var serving by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }

    val valid = name.isNotBlank() && kcal.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Food name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m },
                            label = { Text(m.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedTextField(value = serving, onValueChange = { serving = it },
                    label = { Text("Serving size (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = kcal, onValueChange = { kcal = it.filter(Char::isDigit).take(5) },
                        label = { Text("Calories") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = protein, onValueChange = { protein = num(it) },
                        label = { Text("Protein g") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = carbs, onValueChange = { carbs = num(it) },
                        label = { Text("Carbs g") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = fat, onValueChange = { fat = num(it) },
                        label = { Text("Fat g") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = fiber, onValueChange = { fiber = num(it) },
                    label = { Text("Fiber g (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(
                    name.trim(), meal, serving.takeIf { it.isNotBlank() },
                    kcal.toIntOrNull() ?: 0,
                    protein.toDoubleOrNull() ?: 0.0,
                    carbs.toDoubleOrNull() ?: 0.0,
                    fat.toDoubleOrNull() ?: 0.0,
                    fiber.toDoubleOrNull() ?: 0.0
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LogWeightDialog(onDismiss: () -> Unit, onSave: (Double, Double?) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    val valid = weight.toDoubleOrNull()?.let { it in 20.0..400.0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = num(it) },
                    label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = bodyFat, onValueChange = { bodyFat = num(it) },
                    label = { Text("Body fat % (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(
                    "Trends matter more than any single measurement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(weight.toDouble(), bodyFat.toDoubleOrNull())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LogCardioDialog(onDismiss: () -> Unit, onSave: (type: String, km: Double, min: Double, kcal: Int) -> Unit) {
    var type by remember { mutableStateOf(CardioType.RUNNING.name) }
    var distance by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    val km = distance.toDoubleOrNull()
    val mins = duration.toDoubleOrNull()
    val valid = km != null && km > 0 && mins != null && mins > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log cardio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    CardioType.entries.forEach { t ->
                        FilterChip(selected = type == t.name, onClick = { type = t.name },
                            label = { Text(t.label) })
                    }
                }
                OutlinedTextField(value = distance, onValueChange = { distance = num(it) },
                    label = { Text("Distance (km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = duration, onValueChange = { duration = num(it) },
                    label = { Text("Duration (min)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (valid) {
                    val paceMinPerKm = mins / km
                    Text(
                        String.format("Pace %d:%02d /km · ~%d kcal",
                            paceMinPerKm.toInt(), ((paceMinPerKm % 1) * 60).toInt(), (km * 62).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(type, km!!, mins!!, (km * 62).toInt())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CheckInDialog(onDismiss: () -> Unit, onSave: (trained: Boolean, energy: Int, mood: Int, sleep: Int, hitKcal: Boolean, hitProtein: Boolean, steps: Int, notes: String?) -> Unit) {
    var trained by remember { mutableStateOf(false) }
    var energy by remember { mutableStateOf(3f) }
    var mood by remember { mutableStateOf(3f) }
    var sleep by remember { mutableStateOf(3f) }
    var hitKcal by remember { mutableStateOf(true) }
    var hitProtein by remember { mutableStateOf(true) }
    var steps by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleLine("Did you train?", trained) { trained = it }
                SliderRow("Energy", energy) { energy = it }
                SliderRow("Mood", mood) { mood = it }
                SliderRow("Sleep quality", sleep) { sleep = it }
                ToggleLine("Hit calorie target?", hitKcal) { hitKcal = it }
                ToggleLine("Hit protein target?", hitProtein) { hitProtein = it }
                OutlinedTextField(value = steps, onValueChange = { steps = it.filter(Char::isDigit).take(6) },
                    label = { Text("Steps today") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(trained, energy.toInt(), mood.toInt(), sleep.toInt(),
                    hitKcal, hitProtein, steps.toIntOrNull() ?: 0, notes.takeIf { it.isNotBlank() })
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text(if (checked) "Yes" else "No") })
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}/5", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 1f..5f, steps = 3)
    }
}

private fun num(v: String): String = v.filter { it.isDigit() || it == '.' }.take(6)
