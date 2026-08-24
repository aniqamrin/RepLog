package com.replog.app.domain.logic

import com.replog.app.domain.model.DailyFacts
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.PersonalRecord
import kotlin.math.max
import kotlin.math.roundToInt

object WorkoutMath {

    fun volume(sets: List<Pair<Double, Int>>): Double =
        sets.sumOf { (weight, reps) -> weight * reps }

    fun epleyOneRm(weightKg: Double, reps: Int): Double =
        if (weightKg <= 0.0 || reps <= 0) 0.0 else weightKg * (1 + reps / 30.0)
}

object PRDetector {

    fun buildRecord(
        exerciseName: String,
        sets: List<Triple<Long, Double, Int>>
    ): PersonalRecord? {
        if (sets.isEmpty()) return null
        var bestWeight = 0.0
        var bestRepsAtBest = 0
        var bestOneRm = 0.0
        var achievedDay = sets.first().first
        var volume = 0.0
        for ((day, w, r) in sets) {
            volume += w * r
            bestOneRm = max(bestOneRm, WorkoutMath.epleyOneRm(w, r))
            if (w > bestWeight || (w == bestWeight && r > bestRepsAtBest)) {
                bestWeight = w
                bestRepsAtBest = r
                achievedDay = day
            }
        }
        return PersonalRecord(exerciseName, bestWeight, bestRepsAtBest, bestOneRm, volume, achievedDay, null)
    }

    fun isNewPr(existing: PersonalRecord?, candidateWeight: Double): Boolean {
        return existing == null && candidateWeight > 0 || existing != null && candidateWeight > existing.bestWeightKg + 1e-9
    }
}

object NutritionMath {
    fun remaining(target: Int, consumed: Double): Int = max(0, target - consumed.roundToInt())
    fun macroCalories(protein: Double, carbs: Double, fat: Double): Double = protein * 4 + carbs * 4 + fat * 9
    fun percent(consumed: Double, target: Int): Int =
        if (target <= 0) 0 else ((consumed / target) * 100).roundToInt()
}

object WeightTrendCalculator {

    data class Trend(val changeKg: Double, val perWeekKg: Double, val direction: String)

    fun compute(pointsAscending: List<Pair<Long, Double>>): Trend? {
        if (pointsAscending.size < 2) return null
        val firstDay = pointsAscending.first().first.toDouble()
        val xs = pointsAscending.map { it.first - firstDay }
        val ys = pointsAscending.map { it.second }
        val n = xs.size
        val meanX = xs.average(); val meanY = ys.average()
        var num = 0.0; var den = 0.0
        for (i in 0 until n) {
            num += (xs[i] - meanX) * (ys[i] - meanY)
            den += (xs[i] - meanX) * (xs[i] - meanX)
        }
        if (den == 0.0) return null
        val slopePerDay = num / den
        val change = ys.last() - ys.first()
        val direction = when {
            change < -0.3 -> "down"
            change > 0.3 -> "up"
            else -> "stable"
        }
        return Trend(change, slopePerDay * 7.0, direction)
    }
}

object SummaryCalculator {

    private const val DAY = 86_400_000L

    fun weekStats(days: List<DailyFacts>, goals: Goals): com.replog.app.domain.model.WeekStats? {
        if (days.isEmpty()) return null
        val start = days.minOf { it.epochDay }
        val consistency = ConsistencyCalculator.compute(days, goals).overall
        val calories = days.sumOf { it.calories }
        val loggedDays = days.count { it.calories > 0 }.coerceAtLeast(1)
        return com.replog.app.domain.model.WeekStats(
            epochDayStart = start,
            gymSessions = days.count { it.gym },
            calories = calories,
            avgCalories = calories / loggedDays,
            protein = days.sumOf { it.protein },
            avgProtein = days.sumOf { it.protein } / loggedDays,
            steps = days.sumOf { it.steps.toLong() },
            cardioKm = days.sumOf { it.cardioKm },
            consistencyPct = consistency,
            daysLogged = days.count { it.calories > 0 }
        )
    }

    fun deltaPercent(current: Double, previous: Double): Int? {
        if (previous <= 0.0) return null
        return (((current - previous) / previous) * 100).roundToInt()
    }

    fun isSameWeek(a: Long, b: Long): Boolean = a / 7 == b / 7

    val DAY_MS = DAY
}
