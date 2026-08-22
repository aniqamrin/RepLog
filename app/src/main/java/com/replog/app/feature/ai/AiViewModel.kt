package com.replog.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.ai.OfflineCoach
import com.replog.app.data.local.AiMessageEntity
import com.replog.app.data.prefs.SettingsRepository
import com.replog.app.data.repository.ActivityRepository
import com.replog.app.data.repository.AiRepository
import com.replog.app.data.repository.CardioRepository
import com.replog.app.data.repository.HabitRepository
import com.replog.app.data.repository.ProfileRepository
import com.replog.app.data.repository.StatsRepository
import com.replog.app.data.repository.WeightRepository
import com.replog.app.domain.logic.ConsistencyCalculator
import com.replog.app.domain.logic.InsightsGenerator
import com.replog.app.domain.logic.WeightTrendCalculator
import com.replog.app.domain.model.FitnessSnapshot
import com.replog.app.domain.model.StrengthDelta
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class AiUiState(
    val loading: Boolean = true,
    val messages: List<AiMessageEntity> = emptyList(),
    val sending: Boolean = false,
    val backendConfigured: Boolean = false,
    val dailySummary: String? = null,
    val insights: List<InsightsGenerator.Insight> = emptyList(),
    val snapshot: FitnessSnapshot? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val statsRepository: StatsRepository,
    private val weightRepository: WeightRepository,
    private val workoutRepository: com.replog.app.data.repository.WorkoutRepository,
    private val cardioRepository: CardioRepository,
    private val habitRepository: HabitRepository,
    private val cardioDao: com.replog.app.data.local.CardioDao,
    private val profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    private val offlineCoach: OfflineCoach
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()

    private val snapshot = MutableStateFlow<FitnessSnapshot?>(null)
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state

    init {
        viewModelScope.launch {
            aiRepository.observeLatestConversation()
                .flatMapLatest { conversation ->
                    if (conversation == null) flowOf(emptyList())
                    else aiRepository.observeMessages(conversation.id)
                }
                .collect { messages ->
                    _state.value = _state.value.copy(messages = messages)
                }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _state.value = _state.value.copy(backendConfigured = s.backendConfigured)
            }
        }
        viewModelScope.launch {
            runCatching { loadSnapshot() }
        }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { loadSnapshot() } }
    }

    suspend fun buildSnapshot(): FitnessSnapshot? {
        if (snapshot.value == null) loadSnapshot()
        return snapshot.value
    }

    private suspend fun loadSnapshot() {
        val goals = profileRepository.observeGoals().firstOrNull() ?: com.replog.app.domain.model.Goals()
        val weekStart = today - today.mod(7)
        val facts30 = statsRepository.factsSnapshot(today - 29, today).let { facts ->
            if (facts.isEmpty()) facts
            else {
                val cardio = cardioDao.rawDistanceByDay(facts.minOf { it.epochDay }, facts.maxOf { it.epochDay })
                    .associate { it.epochDay to it.total }
                facts.map { it.copy(cardioKm = cardio[it.epochDay] ?: 0.0) }
            }
        }
        val facts7 = facts30.filter { it.epochDay > today - 7 }
        val logged7 = facts7.count { it.calories > 0 }.coerceAtLeast(1)
        val weights90 = weightRepository.between(today - 89, today).map { it.epochDay to it.weightKg }
        val trend = WeightTrendCalculator.compute(weights90)
        val consistency = ConsistencyCalculator.compute(facts30, goals)
        val weekly = statsRepository.weeklyComparison(today)

        val snap = FitnessSnapshot(
            name = profileRepository.profile()?.name ?: "Athlete",
            goalType = goals.goalType.label,
            calorieTarget = goals.calorieTarget,
            proteinTargetG = goals.proteinTargetG,
            waterTargetMl = goals.waterTargetMl,
            stepTarget = goals.stepTarget,
            weightKg = weights90.lastOrNull()?.second,
            weightTrendKgPerWeek = trend?.perWeekKg,
            avgCalories7 = facts7.sumOf { it.calories } / logged7,
            avgProtein7 = facts7.sumOf { it.protein } / logged7,
            caloriesToday = facts7.lastOrNull()?.calories ?: 0.0,
            proteinToday = facts7.lastOrNull()?.protein ?: 0.0,
            waterTodayMl = facts7.lastOrNull()?.waterMl ?: 0,
            stepsToday = facts7.lastOrNull()?.steps ?: 0,
            workoutsThisWeek = workoutRepository.countBetween(weekStart, today),
            workoutsLastWeek = workoutRepository.countBetween(weekStart - 7, weekStart - 1),
            avgWorkoutsPerWeek4w = workoutRepository.countBetween(today - 27, today) / 4.0,
            cardioKm7 = cardioRepository.totalDistanceBetween(today - 6, today),
            avgSteps7 = facts7.map { it.steps }.average(),
            recentExercises = workoutRepository.recentExerciseNames(8),
            strengthProgression = workoutRepository.strengthDeltas().mapValues {
                StrengthDelta(it.key, it.value.first ?: 0.0, it.value.second)
            },
            habitRates = habitRepository.habitCompletionRates(14, today),
            gymStreak = statsRepository.streaks(today).gym,
            consistencyPct = consistency.overall
        )
        snapshot.value = snap

        val avgProtein21 = facts30.filter { it.epochDay > today - 21 && it.calories > 0 }
            .takeIf { it.isNotEmpty() }?.map { it.protein }?.average()
        val thisWeekDays = facts30.filter { it.epochDay >= weekStart }
        val waterMisses = thisWeekDays.count { it.waterMl < goals.waterTargetMl * 0.9 }

        _state.value = _state.value.copy(
            loading = false,
            dailySummary = offlineCoach.dailySummary(snap),
            insights = InsightsGenerator.generate(
                workoutsThisWeek = snap.workoutsThisWeek,
                workoutsLastWeek = snap.workoutsLastWeek,
                avgProtein3w = avgProtein21,
                proteinTargetG = goals.proteinTargetG,
                waterMissesThisWeek = waterMisses,
                strengthDeltas = snap.strengthProgression.values.toList(),
                consistencyNow = weekly.first?.consistencyPct ?: consistency.overall,
                consistencyPrev = weekly.second?.consistencyPct
            ),
            snapshot = snap
        )
    }

    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            runCatching {
                val snap = buildSnapshot() ?: throw IllegalStateException("No data")
                aiRepository.chat(q, snap)
            }
            _state.value = _state.value.copy(sending = false)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            aiRepository.clearAll()
            _state.value = _state.value.copy(messages = emptyList())
        }
    }
}
