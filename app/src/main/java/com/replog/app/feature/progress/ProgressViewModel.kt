package com.replog.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.repository.MonthlySummaryData
import com.replog.app.data.repository.ProfileRepository
import com.replog.app.data.repository.StatsRepository
import com.replog.app.data.repository.WeightRepository
import com.replog.app.data.repository.WorkoutRepository
import com.replog.app.domain.logic.ConsistencyCalculator
import com.replog.app.domain.logic.WeightTrendCalculator
import com.replog.app.domain.model.DailyFacts
import com.replog.app.domain.model.Goals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ProgressRange(val days: Int, val label: String) {
    D7(7, "7D"), D30(30, "30D"), M3(90, "3M"), M6(182, "6M"), Y1(365, "1Y")
}

data class ProgressUiState(
    val loading: Boolean = true,
    val range: ProgressRange = ProgressRange.D30,
    val goals: Goals = Goals(),
    val weightSeries: List<Double> = emptyList(),
    val weightChangeKg: Double? = null,
    val weightPerWeekKg: Double? = null,
    val currentWeight: Double? = null,
    val consistency: ConsistencyCalculator.Result = ConsistencyCalculator.Result(0, emptyMap()),
    val monthly: MonthlySummaryData? = null,
    val gymPerWeekBars: List<Float> = emptyList(),
    val strengthDeltas: Map<String, Pair<Double?, Double>> = emptyMap()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val weightRepository: WeightRepository,
    private val workoutRepository: WorkoutRepository,
    private val cardioDao: com.replog.app.data.local.CardioDao,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state

    init {
        viewModelScope.launch {
            runCatching { load() }
        }
        viewModelScope.launch {
            profileRepository.observeGoals().collect { goals ->
                _state.value = _state.value.copy(goals = goals)
            }
        }
    }

    fun setRange(range: ProgressRange) {
        _state.value = _state.value.copy(range = range)
        viewModelScope.launch { runCatching { load() } }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { load() } }
    }

    private suspend fun load() {
        val range = _state.value.range
        val facts = statsRepository.factsSnapshot(today - range.days + 1, today)
            .let { attachCardio(it) }
        val weights = weightRepository.between(today - range.days + 1, today)
            .map { it.epochDay to it.weightKg }
        val trend = WeightTrendCalculator.compute(weights)
        val monthly = statsRepository.monthlySummary(today, trend?.changeKg)
        val deltas = workoutRepository.strengthDeltas()

        val weeksBack = 10
        val bars = (weeksBack - 1 downTo 0).map { w ->
            val start = today - today.mod(7) - w * 7L
            facts.count { it.epochDay in start..(start + 6) && it.gym }.toFloat()
        }

        _state.value = _state.value.copy(
            loading = false,
            weightSeries = weights.map { it.second },
            weightChangeKg = trend?.changeKg,
            weightPerWeekKg = trend?.perWeekKg,
            currentWeight = weights.lastOrNull()?.second,
            consistency = ConsistencyCalculator.compute(facts, _state.value.goals),
            monthly = monthly,
            gymPerWeekBars = bars,
            strengthDeltas = deltas
        )
    }

    private suspend fun attachCardio(facts: List<DailyFacts>): List<DailyFacts> {
        if (facts.isEmpty()) return facts
        val rows = cardioDao.rawDistanceByDay(facts.minOf { it.epochDay }, facts.maxOf { it.epochDay })
            .associate { it.epochDay to it.total }
        return facts.map { it.copy(cardioKm = rows[it.epochDay] ?: 0.0) }
    }
}
