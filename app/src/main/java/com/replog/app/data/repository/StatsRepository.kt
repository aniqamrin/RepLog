package com.replog.app.data.repository

import com.replog.app.data.local.ActivityDao
import com.replog.app.data.local.CardioDao
import com.replog.app.data.local.CheckInDao
import com.replog.app.data.local.DayMacroRow
import com.replog.app.data.local.DayValueRow
import com.replog.app.data.local.FoodDao
import com.replog.app.data.local.GoalDao
import com.replog.app.data.local.HabitDao
import com.replog.app.data.local.WorkoutDao
import com.replog.app.domain.logic.ConsistencyCalculator
import com.replog.app.domain.logic.StreakCalculator
import com.replog.app.domain.logic.SummaryCalculator
import com.replog.app.domain.model.ActivityEvent
import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.DailyFacts
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.Streaks
import com.replog.app.domain.model.WeekStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class MonthlySummaryData(
    val sessions: Int,
    val avgCalories: Double,
    val avgProtein: Double,
    val totalCardioKm: Double,
    val avgSteps: Double,
    val weightChangeKg: Double?,
    val consistencyPct: Int
)

@Singleton
class StatsRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val workoutDao: WorkoutDao,
    private val cardioDao: CardioDao,
    private val checkInDao: CheckInDao,
    private val habitDao: HabitDao,
    private val activityDao: ActivityDao,
    private val goalDao: GoalDao
) {

    fun dailyFactsFlow(from: Long, to: Long): Flow<List<DailyFacts>> = combine(
        foodDao.observeTotalsRange(from, to),
        workoutDao.observeGymDaysBetween(from, to),
        cardioDao.observeDistanceByDayBetween(from, to),
        checkInDao.observeBetween(from, to),
        habitDao.observeLogsBetween(from, to),
        activityDao.observeBetween(from, to).map { it.map { e -> e.toDomain() } }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val foodRows = values[0] as List<DayMacroRow>
        val gymDays = (values[1] as List<Long>).toSet()
        @Suppress("UNCHECKED_CAST")
        val cardioRows = values[2] as List<DayValueRow>
        val checkIns = values[3] as List<com.replog.app.data.local.CheckInEntity>
        val habitLogs = values[4] as List<com.replog.app.data.local.HabitLogEntity>
        val waterEvents = (values[5] as List<ActivityEvent>)
            .filter { it.type == ActivityType.WATER }

        buildFacts(foodRows, gymDays, cardioRows, checkIns, habitLogs, waterEvents)
    }

    suspend fun factsSnapshot(from: Long, to: Long): List<DailyFacts> {
        val foodRows = foodDao.observeTotalsRange(from, to).first()
        val gymDays = workoutDao.gymDaysBetween(from, to).toSet()
        val cardioRows = cardioDistanceByDay(from, to)
        val checkIns = checkInDao.between(from, to)
        val events = activityDao.listBetween(from, to)
        val waterEvents = events.filter { it.type == ActivityType.WATER.name }
            .map { it.toDomain() }
        return buildFacts(foodRows, gymDays, cardioRows, checkIns, emptyList(), waterEvents)
    }

    private fun buildFacts(
        foodRows: List<DayMacroRow>,
        gymDays: Set<Long>,
        cardioRows: List<DayValueRow>,
        checkIns: List<com.replog.app.data.local.CheckInEntity>,
        habitLogs: List<com.replog.app.data.local.HabitLogEntity>,
        waterEvents: List<ActivityEvent>
    ): List<DailyFacts> {
        val foodByDay = foodRows.associateBy { it.epochDay }
        val cardioByDay = cardioRows.associate { it.epochDay to it.total }
        val checkInByDay = checkIns.associateBy { it.epochDay }
        val waterByDay = waterEvents.groupBy { it.epochDay }
            .mapValues { entry -> entry.value.sumOf { it.value ?: 0.0 } }
        val habitsTotal = habitLogs.groupBy { it.habitId }.size.coerceAtLeast(1)
        val habitsDoneByDay = habitLogs.filter { it.completed }.groupBy { it.epochDay }
            .mapValues { it.value.size }

        val allDays = (
            foodByDay.keys + gymDays + cardioByDay.keys +
                checkInByDay.keys + waterByDay.keys + habitsDoneByDay.keys
            ).distinct().sorted()

        return allDays.map { day ->
            DailyFacts(
                epochDay = day,
                gym = day in gymDays,
                calories = foodByDay[day]?.calories ?: 0.0,
                protein = foodByDay[day]?.protein ?: 0.0,
                waterMl = (waterByDay[day] ?: 0.0).toInt(),
                steps = checkInByDay[day]?.steps ?: 0,
                cardioKm = cardioByDay[day] ?: 0.0,
                habitCount = if ((habitsDoneByDay[day] ?: 0) > 0) habitsTotal else 0,
                habitCompleted = habitsDoneByDay[day] ?: 0
            )
        }
    }

    private suspend fun cardioDistanceByDay(from: Long, to: Long): List<DayValueRow> =
        cardioDao.totalDistanceBetween(from, to).let { _ -> emptyList() }
            .ifEmpty { rawCardioRows(from, to) }

    private suspend fun rawCardioRows(from: Long, to: Long): List<DayValueRow> {
        return cardioDao.rawDistanceByDay(from, to)
    }

    suspend fun streaks(today: Long): Streaks {
        val goals = goalDao.get()?.toGoals() ?: Goals()
        val yearAgo = today - 365
        val gymDays = workoutDao.gymDaysBetween(yearAgo, today).toSet()
        val nutritionDays = foodDao.loggedDays(yearAgo, today).toSet()
        val proteinDays = foodDao.daysProteinHit(yearAgo, today, goals.proteinTargetG * 0.9).toSet()
        val overallDays = buildSet {
            addAll(gymDays); addAll(nutritionDays)
            addAll(
                activityDao.listBetween(yearAgo, today)
                    .filter { it.type in setOf("WATER", "CARDIO", "STEPS", "WEIGHT", "HABIT") }
                    .map { it.epochDay }
            )
        }
        return Streaks(
            gym = StreakCalculator.currentStreak(gymDays, today),
            nutrition = StreakCalculator.currentStreak(nutritionDays, today),
            protein = StreakCalculator.currentStreak(proteinDays, today),
            overall = StreakCalculator.overallStreak(overallDays, today),
            longestOverall = StreakCalculator.longestStreak(overallDays)
        )
    }

    suspend fun consistencyFor(facts: List<DailyFacts>, goals: Goals): ConsistencyCalculator.Result =
        ConsistencyCalculator.compute(facts, goals)

    suspend fun weeklyComparison(today: Long): Triple<WeekStats?, WeekStats?, List<DailyFacts>> {
        val goals = goalDao.get()?.toGoals() ?: Goals()
        val thisWeekStart = today - today.mod(7)
        val lastWeekStart = thisWeekStart - 7
        val factsThis = attachCardio(factsSnapshot(thisWeekStart, today))
        val factsLast = attachCardio(factsSnapshot(lastWeekStart, thisWeekStart - 1))
        val t = SummaryCalculator.weekStats(factsThis.filter { it.epochDay >= thisWeekStart }, goals)
        val l = SummaryCalculator.weekStats(factsLast, goals)
        return Triple(t, l, factsThis)
    }

    suspend fun monthlySummary(today: Long, weightChangeKg: Double?): MonthlySummaryData {
        val goals = goalDao.get()?.toGoals() ?: Goals()
        val start = today - 29
        val facts = attachCardio(factsSnapshot(start, today))
        val sessions = workoutDao.countBetween(start, today)
        val logged = facts.count { it.calories > 0 }.coerceAtLeast(1)
        return MonthlySummaryData(
            sessions = sessions,
            avgCalories = facts.sumOf { it.calories } / logged,
            avgProtein = facts.sumOf { it.protein } / logged,
            totalCardioKm = facts.sumOf { it.cardioKm },
            avgSteps = facts.map { it.steps }.average(),
            weightChangeKg = weightChangeKg,
            consistencyPct = ConsistencyCalculator.compute(facts, goals).overall
        )
    }

    private suspend fun attachCardio(facts: List<DailyFacts>): List<DailyFacts> {
        if (facts.isEmpty()) return facts
        val rows = cardioDao.rawDistanceByDay(facts.minOf { it.epochDay }, facts.maxOf { it.epochDay })
        val byDay = rows.associate { it.epochDay to it.total }
        return facts.map { it.copy(cardioKm = byDay[it.epochDay] ?: 0.0) }
    }
}
