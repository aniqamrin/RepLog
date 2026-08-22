package com.replog.app.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.repository.ExerciseDraft
import com.replog.app.data.repository.SetDraft
import com.replog.app.data.repository.WorkoutDraft
import com.replog.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetEditor(
    val id: Long,
    val reps: String = "",
    val weightKg: String = ""
) {
    fun isValid(): Boolean =
        reps.toIntOrNull() != null && weightKg.toDoubleOrNull() != null
}

data class ExerciseEditor(
    val id: Long,
    val exerciseName: String = "",
    val restSeconds: String = "90",
    val sets: List<SetEditor> = listOf(SetEditor(newId()))
) {
    val isComplete: Boolean get() = exerciseName.isNotBlank() && sets.isNotEmpty() && sets.all { it.isValid() }
}

data class WorkoutEditorState(
    val name: String = "",
    val type: String = "PUSH",
    val durationMin: String = "",
    val notes: String = "",
    val exercises: List<ExerciseEditor> = listOf(ExerciseEditor(id = newId())),
    val saving: Boolean = false,
    val savedPrs: List<String> = emptyList()
) {
    val isValid: Boolean
        get() = durationMin.toIntOrNull() != null &&
            exercises.isNotEmpty() &&
            exercises.all { it.isComplete }
}

private var idCounter = System.currentTimeMillis()

fun newId(): Long = ++idCounter

@HiltViewModel
class CreateWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutEditorState())
    val state: StateFlow<WorkoutEditorState> = _state

    fun update(transform: (WorkoutEditorState) -> WorkoutEditorState) {
        _state.update(transform)
    }

    fun setType(typeName: String) {
        _state.update { it.copy(type = typeName) }
    }

    fun addExercise() {
        _state.update {
            it.copy(exercises = it.exercises + ExerciseEditor(id = newId()))
        }
    }

    fun removeExercise(exerciseId: Long) {
        _state.update {
            it.copy(exercises = it.exercises.filterNot { ex -> ex.id == exerciseId })
        }
    }

    fun updateExercise(exerciseId: Long, transform: (ExerciseEditor) -> ExerciseEditor) {
        _state.update { s ->
            s.copy(exercises = s.exercises.map { if (it.id == exerciseId) transform(it) else it })
        }
    }

    fun addSet(exerciseId: Long) {
        updateExercise(exerciseId) { ex ->
            val last = ex.sets.lastOrNull()
            ex.copy(
                sets = ex.sets + SetEditor(
                    id = newId(),
                    weightKg = last?.weightKg.orEmpty(),
                    reps = last?.reps.orEmpty()
                )
            )
        }
    }

    fun removeSet(exerciseId: Long, setId: Long) {
        updateExercise(exerciseId) { ex -> ex.copy(sets = ex.sets.filterNot { it.id == setId }) }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.isValid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val drafts = s.exercises.map { ex ->
                    ExerciseDraft(
                        exerciseName = ex.exerciseName.trim(),
                        restSeconds = ex.restSeconds.toIntOrNull() ?: 90,
                        notes = null,
                        sets = ex.sets.mapNotNull { set ->
                            val reps = set.reps.toIntOrNull()
                            val w = set.weightKg.toDoubleOrNull()
                            if (reps == null || w == null) null else SetDraft(reps = reps, weightKg = w)
                        }
                    )
                }
                val prs = workoutRepository.save(
                    WorkoutDraft(
                        epochDay = LocalDate.now().toEpochDay(),
                        name = s.name.trim().ifBlank { typeLabel(s.type) },
                        type = s.type,
                        durationMin = s.durationMin.toIntOrNull() ?: 60,
                        notes = s.notes.trim().takeIf { it.isNotBlank() },
                        exercises = drafts
                    )
                )
                _state.update { it.copy(saving = false, savedPrs = emptyList()) }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun typeLabel(type: String): String =
        com.replog.app.domain.model.WorkoutType.entries.firstOrNull { it.name == type }?.label ?: "Workout"
}
