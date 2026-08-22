package com.replog.app.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.local.CardioSessionEntity
import com.replog.app.data.local.PersonalRecordEntity
import com.replog.app.data.local.WorkoutEntity
import com.replog.app.data.repository.CardioRepository
import com.replog.app.data.repository.ExerciseDraft
import com.replog.app.data.repository.SetDraft
import com.replog.app.data.repository.StatsRepository
import com.replog.app.data.repository.WorkoutDraft
import com.replog.app.data.repository.WorkoutRepository
import com.replog.app.domain.logic.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutDashboardState(
    val loading: Boolean = true,
    val trainedToday: Boolean = false,
    val weekSessions: Int = 0,
    val weekTarget: Int = 5,
    val gymStreak: Int = 0,
    val volume30d: Double = 0.0,
    val avgDuration30d: Double = 0.0,
    val recent: List<WorkoutEntity> = emptyList(),
    val cardio: List<CardioSessionEntity> = emptyList()
)

data class HistoryState(
    val total: Int = 0,
    val thisWeek: Int = 0,
    val thisMonth: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val avgDuration: Double = 0.0,
    val totalVolume: Double = 0.0
)

data class CardioFormState(
    val type: String = "RUNNING",
    val distanceKm: String = "",
    val durationMin: String = ""
) {
    fun isValid(): Boolean =
        distanceKm.toDoubleOrNull() != null && durationMin.toDoubleOrNull() != null
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val cardioRepository: CardioRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()
    private val cardioForm = MutableStateFlow(CardioFormState())

    val cardioFormState: StateFlow<CardioFormState> = cardioForm

    val dashboard: StateFlow<WorkoutDashboardState> = combine(
        workoutRepository.observeRecent(10),
        cardioRepository.observeRecent(8)
    ) { workouts, cardio -> workouts to cardio }
        .map { (workouts, cardio) ->
            val weekStart = today - today.mod(7)
            val monthStart = today - 29
            WorkoutDashboardState(
                loading = false,
                trainedToday = workouts.any { it.epochDay == today },
                weekSessions = workoutRepository.countBetween(weekStart, today),
                weekTarget = 5,
                gymStreak = StreakCalculator.currentStreak(workouts.map { it.epochDay }.toSet(), today),
                volume30d = workoutRepository.volumeBetween(monthStart, today),
                avgDuration30d = workoutRepository.avgDurationBetween(monthStart, today),
                recent = workouts,
                cardio = cardio
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDashboardState())

    val prs: StateFlow<List<PersonalRecordEntity>> = workoutRepository.observePRs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { recomputeHistory() }
    }

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history

    fun refresh() {
        viewModelScope.launch { recomputeHistory() }
    }

    private suspend fun recomputeHistory() {
        runCatching {
            val yearAgo = today - 365
            val total = workoutRepository.countBetween(yearAgo, today)
            val weekStart = today - today.mod(7)
            val monthStart = today - 29
            val days = workoutRepository.gymDays(yearAgo, today).toSet()
            _history.value = HistoryState(
                total = total,
                thisWeek = workoutRepository.countBetween(weekStart, today),
                thisMonth = workoutRepository.countBetween(monthStart, today),
                currentStreak = StreakCalculator.currentStreak(days, today),
                longestStreak = StreakCalculator.longestStreak(days),
                avgDuration = workoutRepository.avgDurationBetween(monthStart, today),
                totalVolume = workoutRepository.volumeBetween(monthStart, today)
            )
        }
    }

    fun updateCardioForm(transform: (CardioFormState) -> CardioFormState) {
        cardioForm.value = transform(cardioForm.value)
    }

    fun saveCardio(onDone: () -> Unit) {
        val form = cardioForm.value
        if (!form.isValid()) return
        viewModelScope.launch {
            cardioRepository.add(
                epochDay = today,
                type = form.type,
                distanceKm = form.distanceKm.toDouble(),
                durationMin = form.durationMin.toDouble(),
                calories = (form.distanceKm.toDouble() * 62).toInt()
            )
            cardioForm.value = CardioFormState(type = form.type)
            onDone()
        }
    }

    suspend fun paceFor(distanceKm: Double, durationMin: Double): String =
        cardioRepository.pace(distanceKm, durationMin)
}
