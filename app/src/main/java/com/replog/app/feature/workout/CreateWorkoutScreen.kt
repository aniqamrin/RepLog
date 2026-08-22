package com.replog.app.feature.workout

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.domain.model.WorkoutType
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader

@Composable
fun CreateWorkoutScreen(
    onDone: () -> Unit,
    viewModel: CreateWorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val typeScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Log workout", style = MaterialTheme.typography.displaySmall)
        }
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Workout name") },
                placeholder = { Text("Push workout") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Column {
                SectionHeader("TYPE")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(typeScroll)
                ) {
                    WorkoutType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type.name,
                            onClick = { viewModel.setType(type.name) },
                            label = { Text(type.label) }
                        )
                    }
                }
            }
        }
        item {
            SectionHeader("EXERCISES")
        }
        items(state.exercises.size) { index ->
            ExerciseEditorCard(
                exercise = state.exercises[index],
                canRemove = state.exercises.size > 1,
                viewModel = viewModel
            )
        }
        item {
            OutlinedButton(onClick = viewModel::addExercise, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add exercise")
            }
        }
        item {
            OutlinedTextField(
                value = state.durationMin,
                onValueChange = { v ->
                    viewModel.update { it.copy(durationMin = v.filter(Char::isDigit).take(4)) }
                },
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
        item {
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.isValid && !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (state.saving) "Saving…" else "Save workout")
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun ExerciseEditorCard(
    exercise: com.replog.app.feature.workout.ExerciseEditor,
    canRemove: Boolean,
    viewModel: CreateWorkoutViewModel
) {
    RpCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = exercise.exerciseName,
                    onValueChange = { v ->
                        viewModel.updateExercise(exercise.id) { it.copy(exerciseName = v) }
                    },
                    label = { Text("Exercise") },
                    placeholder = { Text("Bench Press") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (canRemove) {
                    IconButton(onClick = { viewModel.removeExercise(exercise.id) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Remove exercise",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = exercise.restSeconds,
                onValueChange = { v ->
                    val digits = v.filter(Char::isDigit).take(3)
                    viewModel.updateExercise(exercise.id) { it.copy(restSeconds = digits) }
                },
                label = { Text("Rest (sec)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            SectionHeader("SETS")
            exercise.sets.forEachIndexed { setIndex, set ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        "${setIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp)
                    )
                    OutlinedTextField(
                        value = set.reps,
                        onValueChange = { v ->
                            val digits = v.filter(Char::isDigit).take(3)
                            viewModel.updateExercise(exercise.id) { ex ->
                                ex.copy(sets = ex.sets.map {
                                    if (it.id == set.id) it.copy(reps = digits) else it
                                })
                            }
                        },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = set.weightKg,
                        onValueChange = { v ->
                            val cleaned = v.filter { it.isDigit() || it == '.' }.take(6)
                            viewModel.updateExercise(exercise.id) { ex ->
                                ex.copy(sets = ex.sets.map {
                                    if (it.id == set.id) it.copy(weightKg = cleaned) else it
                                })
                            }
                        },
                        label = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true
                    )
                    if (exercise.sets.size > 1) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Remove set",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                viewModel.removeSet(exercise.id, set.id)
                            }.padding(6.dp)
                        )
                    } else {
                        Spacer(Modifier.width(18.dp))
                    }
                }
            }
            TextButton(onClick = { viewModel.addSet(exercise.id) }) {
                Text("+ Add set")
            }
        }
    }
}
