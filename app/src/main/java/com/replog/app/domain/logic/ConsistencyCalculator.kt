package com.replog.app.domain.logic

import com.replog.app.domain.model.DailyFacts
import com.replog.app.domain.model.Goals
import kotlin.math.roundToInt

object ConsistencyCalculator {

    data class Result(val overall: Int, val byMetric: Map<String, Int>)

    fun compute(days: List<DailyFacts>, goals: Goals): Result {
        if (days.isEmpty()) return Result(0, emptyMap())
        var gym = 0.0; var nutrition = 0.0; var protein = 0.0
        var cardio = 0.0; var water = 0.0; var steps = 0.0
        for (d in days) {
            gym += if (d.gym) 1.0 else 0.0
            nutrition += when {
                d.calories <= 0.0 -> 0.0
                d.calories >= goals.calorieTarget * 0.7 && d.calories <= goals.calorieTarget * 1.15 -> 1.0
                else -> 0.5
            }
            protein += minOf(d.protein / goals.proteinTargetG.toDouble(), 1.0)
            cardio += if (d.cardioKm > 0.5 || (d.gym && d.steps > 4000)) 1.0 else 0.0
            water += minOf(d.waterMl / goals.waterTargetMl.toDouble(), 1.0)
            steps += minOf(d.steps / goals.stepTarget.toDouble(), 1.0)
        }
        val n = days.size
        fun pct(v: Double) = ((v / n) * 100).roundToInt()
        val overall = (pct(gym) * 0.25 + pct(nutrition) * 0.20 + pct(protein) * 0.20 +
                pct(cardio) * 0.10 + pct(water) * 0.125 + pct(steps) * 0.125).roundToInt()
        return Result(
            overall = overall,
            byMetric = mapOf(
                "Gym" to pct(gym),
                "Nutrition" to pct(nutrition),
                "Protein" to pct(protein),
                "Cardio" to pct(cardio),
                "Water" to pct(water),
                "Steps" to pct(steps)
            )
        )
    }
}
