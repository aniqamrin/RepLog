package com.replog.app.domain.logic

import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.ActivityEvent
import com.replog.app.domain.model.Goals
import kotlin.math.min

object ContributionAggregator {

    fun levelForDay(events: List<ActivityEvent>, goals: Goals, filter: ActivityType?): Int {
        if (events.isEmpty()) return 0
        return if (filter == null) combinedLevel(events, goals) else typeLevel(events, goals, filter)
    }

    private fun combinedLevel(events: List<ActivityEvent>, goals: Goals): Int {
        var score = 0.0
        val gymSessions = events.count { it.type == ActivityType.GYM }
        if (gymSessions > 0) score += 2.0
        val calories = events.filter { it.type == ActivityType.FOOD }.sumOf { it.value ?: 0.0 }
        if (calories > 0) score += 0.5
        if (calories >= goals.calorieTarget * 0.8) score += 0.5
        val protein = events.filter { it.type == ActivityType.PROTEIN }.sumOf { it.value ?: 0.0 }
        if (protein >= goals.proteinTargetG * 0.9) score += 1.0
        else if (protein > 0) score += 0.25
        val cardioKm = events.filter { it.type == ActivityType.CARDIO }.sumOf { it.value ?: 0.0 }
        if (cardioKm >= 2.0) score += 1.0
        else if (cardioKm > 0) score += 0.5
        val steps = events.filter { it.type == ActivityType.STEPS }.maxOfOrNull { it.value ?: 0.0 } ?: 0.0
        score += min(steps / goals.stepTarget.toDouble(), 1.0)
        val waterMl = events.filter { it.type == ActivityType.WATER }.sumOf { it.value ?: 0.0 }
        score += min(waterMl / goals.waterTargetMl.toDouble(), 1.0) * 0.5
        val habits = events.count { it.type == ActivityType.HABIT }
        if (habits >= 2) score += 0.5
        return scoreToLevel(score)
    }

    fun scoreToLevel(score: Double): Int = when {
        score <= 0.05 -> 0
        score < 1.0 -> 1
        score < 2.0 -> 2
        score < 3.5 -> 3
        else -> 4
    }

    private fun typeLevel(events: List<ActivityEvent>, goals: Goals, type: ActivityType): Int {
        val typed = events.filter { it.type == type }
        if (typed.isEmpty()) return 0
        return when (type) {
            ActivityType.GYM -> when {
                typed.size >= 2 || (typed.any { (it.detail?.toIntOrNull() ?: 0) >= 60 }) -> 4
                else -> 3
            }
            ActivityType.FOOD -> ratioLevel(typed.sumOf { it.value ?: 0.0 }, goals.calorieTarget.toDouble())
            ActivityType.PROTEIN -> ratioLevel(typed.sumOf { it.value ?: 0.0 }, goals.proteinTargetG.toDouble())
            ActivityType.STEPS -> ratioLevel(typed.maxOfOrNull { it.value ?: 0.0 } ?: 0.0, goals.stepTarget.toDouble())
            ActivityType.WATER -> ratioLevel(typed.sumOf { it.value ?: 0.0 }, goals.waterTargetMl.toDouble())
            ActivityType.CARDIO -> {
                val km = typed.sumOf { it.value ?: 0.0 }
                when {
                    km >= 10 -> 4
                    km >= 5 -> 3
                    km >= 2 -> 2
                    else -> 1
                }
            }
            ActivityType.WEIGHT -> 2
            ActivityType.HABIT -> {
                val done = typed.size
                when {
                    done >= 4 -> 4
                    done == 3 -> 3
                    done == 2 -> 2
                    else -> 1
                }
            }
        }
    }

    private fun ratioLevel(value: Double, target: Double): Int {
        if (value <= 0.0) return 0
        val r = value / target
        return when {
            r >= 1.0 -> 4
            r >= 0.8 -> 3
            r >= 0.5 -> 2
            else -> 1
        }
    }

    fun buildLevels(
        daysAscending: List<Long>,
        eventsByDay: Map<Long, List<ActivityEvent>>,
        goals: Goals,
        filter: ActivityType?
    ): List<Int> = daysAscending.map { day ->
        levelForDay(eventsByDay[day].orEmpty(), goals, filter)
    }
}
