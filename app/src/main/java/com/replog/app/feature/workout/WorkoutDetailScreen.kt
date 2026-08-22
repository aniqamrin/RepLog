package com.replog.app.feature.workout

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replog.app.data.local.WorkoutDao
import com.replog.app.data.local.WorkoutWithExercises
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.RpCard
import com.replog.app.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutDao: WorkoutDao
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["id"])

    val workout: StateFlow<WorkoutWithExercises?> =
        workoutDao.observeWithExercises(workoutId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun WorkoutDetailScreen(
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val data by viewModel.workout.collectAsStateWithLifecycle()
    val w = data?.workout

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (w == null) {
                EmptyState("Workout", "Loading…")
            } else {
                Column {
                    Text(w.name, style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${Format.fullDateLabel(w.epochDay)} · ${w.durationMin} min · ${typeLabel(w.type)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    w.notes?.let { note ->
                        Spacer(Modifier.height(6.dp))
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        data?.exercises?.forEach { exWithSets ->
            val sets = exWithSets.sets.sortedBy { it.setIndex }
            item(key = exWithSets.exercise.id) {
                RpCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(exWithSets.exercise.exerciseName, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        sets.forEach { set ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${set.setIndex}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 14.dp)
                                )
                                Text(
                                    "${Format.oneDecimal(set.weightKg)} kg × ${set.reps}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "rest ${exWithSets.exercise.restSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val best = sets.maxOfOrNull { it.weightKg } ?: 0.0
                        val volume = sets.sumOf { it.weightKg * it.reps }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Top set ${Format.oneDecimal(best)} kg · volume ${Format.kcal(volume)} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
