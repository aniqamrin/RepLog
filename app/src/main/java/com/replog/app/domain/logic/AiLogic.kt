package com.replog.app.domain.logic

import com.replog.app.domain.model.FitnessSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

object AiContextBuilder {

    fun build(s: FitnessSnapshot): String = buildString {
        append("{")
        append("\"user\":\"${s.name}\",")
        append("\"goal\":\"${s.goalType}\",")
        append("\"targets\":{\"calories\":${s.calorieTarget},\"protein_g\":${s.proteinTargetG},\"water_ml\":${s.waterTargetMl},\"steps\":${s.stepTarget}},")
        append("\"weight_kg\":${s.weightKg ?: "null"},")
        append("\"weight_trend_kg_per_week\":${s.weightTrendKgPerWeek?.let { (it * 100).roundToInt() / 100.0 } ?: "null"},")
        append("\"today\":{\"calories\":${fmt(s.caloriesToday)},\"protein_g\":${fmt(s.proteinToday)},\"water_ml\":${s.waterTodayMl},\"steps\":${s.stepsToday}},")
        append("\"averages_7d\":{\"calories\":${fmt(s.avgCalories7)},\"protein_g\":${fmt(s.avgProtein7)},\"steps\":${s.avgSteps7.roundToInt()}},")
        append("\"training\":{\"workouts_this_week\":${s.workoutsThisWeek},\"last_week\":${s.workoutsLastWeek},\"avg_per_week_4w\":${(s.avgWorkoutsPerWeek4w * 10).roundToInt() / 10.0},\"cardio_km_7d\":${fmt(s.cardioKm7)},\"gym_streak_days\":${s.gymStreak}},")
        if (s.recentExercises.isNotEmpty()) {
            append("\"recent_exercises\":[${s.recentExercises.take(6).joinToString(",") { "\"$it\"" }}],")
        }
        if (s.strengthProgression.isNotEmpty()) {
            val entries = s.strengthProgression.entries.take(5).joinToString(",") {
                "\"${it.key}\":{\"prev\":${it.value.previousBestKg},\"best\":${it.value.currentBestKg}}"
            }
            append("\"strength_progression\":{$entries},")
        }
        if (s.habitRates.isNotEmpty()) {
            val entries = s.habitRates.entries.joinToString(",") {
                "\"${it.key}\":${(it.value * 100).roundToInt()}"
            }
            append("\"habit_completion_pct\":{$entries},")
        }
        append("\"consistency_pct\":${s.consistencyPct}")
        append("}")
    }

    private fun fmt(v: Double): String = (v * 10).roundToInt() / 10.0
}

object InsightsGenerator {

    data class Insight(val text: String, val kind: String)

    fun generate(
        workoutsThisWeek: Int,
        workoutsLastWeek: Int,
        avgProtein3w: Double?,
        proteinTargetG: Int,
        waterMissesThisWeek: Int,
        strengthDeltas: List<com.replog.app.domain.model.StrengthDelta>,
        consistencyNow: Int,
        consistencyPrev: Int?
    ): List<Insight> {
        val out = mutableListOf<Insight>()

        if (workoutsLastWeek > 0 && workoutsThisWeek != workoutsLastWeek) {
            val dir = if (workoutsThisWeek > workoutsLastWeek) "increased" else "dropped"
            out += Insight(
                "Your gym frequency $dir from $workoutsLastWeek to $workoutsThisWeek sessions per week.",
                "training"
            )
        }
        if (avgProtein3w != null && avgProtein3w >= proteinTargetG * 0.9) {
            out += Insight(
                "Average protein intake has been above ${avgProtein3w.roundToInt()}g, close to your ${proteinTargetG}g goal.",
                "nutrition"
            )
        }
        strengthDeltas.firstOrNull { abs(it.currentBestKg - it.previousBestKg) >= 1.0 }?.let { d ->
            val sign = if (d.currentBestKg > d.previousBestKg) "+" else ""
            out += Insight(
                "Your ${d.exercise} best moved to ${fmt(d.currentBestKg)}kg ($sign${fmt(d.currentBestKg - d.previousBestKg)}kg vs previous best).",
                "strength"
            )
        }
        if (waterMissesThisWeek >= 3) {
            out += Insight(
                "You missed your water goal on $waterMissesThisWeek days this week.",
                "hydration"
            )
        }
        if (consistencyPrev != null && abs(consistencyNow - consistencyPrev) >= 8) {
            val dir = if (consistencyNow > consistencyPrev) "up" else "down"
            out += Insight(
                "Consistency is $dir ${abs(consistencyNow - consistencyPrev)} points versus the previous period (${consistencyNow}% now).",
                "consistency"
            )
        }
        return out
    }

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
}
