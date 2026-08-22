package com.replog.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.local.CheckInEntity
import com.replog.app.data.repository.ActivityRepository
import com.replog.app.data.repository.CheckInRepository
import com.replog.app.data.repository.NutritionRepository
import com.replog.app.data.repository.ProfileRepository
import com.replog.app.data.repository.StatsRepository
import com.replog.app.data.repository.WorkoutRepository
import com.replog.app.domain.logic.ContributionAggregator
import com.replog.app.domain.logic.SummaryCalculator
import com.replog.app.domain.model.ActivityEvent
import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.MacroTotals
import com.replog.app.domain.model.Streaks
import com.replog.app.domain.model.WeekStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val goals: Goals = Goals(),
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val waterMl: Int = 0,
    val steps: Int = 0,
    val workoutDoneToday: Boolean = false,
    val checkInToday: CheckInEntity? = null
)

data class HeatmapState(
    val levels: Map<Long, Int> = emptyMap(),
    val filter: ActivityType? = null
)

data class ExtrasState(
    val streaks: Streaks? = null,
    val thisWeek: WeekStats? = null,
    val lastWeek: WeekStats? = null,
    val todayFacts: com.replog.app.domain.model.DailyFacts? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val nutritionRepository: NutritionRepository,
    private val checkInRepository: CheckInRepository,
    private val workoutRepository: WorkoutRepository,
    profileRepository: ProfileRepository,
    activityRepository: ActivityRepository
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()
    val days365: List<Long> = (today - 364..today).toList()

    private val filter = MutableStateFlow<ActivityType?>(null)
    private val selectedDay = MutableStateFlow<Long?>(null)

    private val _extras = MutableStateFlow(ExtrasState())
    val extrasState: StateFlow<ExtrasState> = _extras

    val uiState: StateFlow<HomeUiState> = combine(
        profileRepository.observeGoals(),
        nutritionRepository.observeTotals(today),
        checkInRepository.observeForDay(today),
        statsRepository.dailyFactsFlow(today, today).map { it.firstOrNull() },
        activityRepository.observeDay(today)
    ) { goals, totals, checkIn, facts, events ->
        val waterMl = facts?.waterMl
            ?: events.filter { it.type == ActivityType.WATER }.sumOf { it.value ?: 0.0 }.toInt()
        val steps = maxOf(facts?.steps ?: 0, checkIn?.steps ?: 0)
        val gymFromEvents = events.any { it.type == ActivityType.GYM }
        HomeUiState(
            loading = false,
            goals = goals,
            calories = totals.calories,
            protein = totals.protein,
            carbs = totals.carbs,
            fat = totals.fat,
            fiber = totals.fiber,
            waterMl = waterMl,
            steps = steps,
            workoutDoneToday = gymFromEvents || (facts?.gym ?: false) || (checkIn?.trained ?: false),
            checkInToday = checkIn
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val heatmapState: StateFlow<HeatmapState> = combine(
        statsRepository.observeEvents(today - 364, today),
        profileRepository.observeGoals(),
        filter
    ) { events, goals, f ->
        val byDay = events.groupBy { it.epochDay }
        HeatmapState(
            levels = days365.associateWith { day ->
                ContributionAggregator.levelForDay(byDay[day].orEmpty(), goals, f)
            },
            filter = f
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeatmapState())

    val selectedDayEvents: StateFlow<Pair<Long, List<ActivityEvent>>?> =
        selectedDay.flatMapLatest { day ->
            if (day == null) flowOf(null)
            else activityRepository.observeDay(day).map { day to it }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val timeline: StateFlow<List<ActivityEvent>> = activityRepository
        .observeRange(today - 2, today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            statsRepository.observeEvents(today - 7, today)
                .drop(1)
                .collect { loadExtras() }
        }
        loadExtras()
    }

    fun refresh() {
        loadExtras()
    }

    fun setFilter(type: ActivityType?) {
        filter.value = type
    }

    fun selectDay(day: Long?) {
        selectedDay.value = day
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { nutritionRepository.addWater(today, ml) }
    }

    fun addSteps(delta: Int) {
        viewModelScope.launch { checkInRepository.addSteps(today, delta) }
    }

    fun saveCheckIn(entity: CheckInEntity) {
        viewModelScope.launch {
            checkInRepository.save(entity.copy(epochDay = today))
            loadExtras()
        }
    }

    private fun loadExtras() {
        viewModelScope.launch {
            runCatching {
                val streaks = statsRepository.streaks(today)
                val comparison = statsRepository.weeklyComparison(today)
                _extras.value = ExtrasState(
                    streaks = streaks,
                    thisWeek = comparison.first,
                    lastWeek = comparison.second,
                    todayFacts = comparison.third.lastOrNull()
                )
            }
        }
    }

    fun deltaPercent(current: Double?, previous: Double?): Int? {
        if (current == null || previous == null) return null
        return SummaryCalculator.deltaPercent(current, previous)
    }
}
