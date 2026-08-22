package com.replog.app.domain.model

data class ActivityEvent(
    val id: Long = 0L,
    val epochDay: Long,
    val type: ActivityType,
    val intensity: Int,
    val title: String,
    val detail: String? = null,
    val value: Double? = null,
    val createdAtMillis: Long = 0L
)

data class DailyFacts(
    val epochDay: Long,
    val gym: Boolean = false,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val waterMl: Int = 0,
    val steps: Int = 0,
    val cardioKm: Double = 0.0,
    val habitCount: Int = 0,
    val habitCompleted: Int = 0
)

data class WeekStats(
    val epochDayStart: Long,
    val gymSessions: Int,
    val calories: Double,
    val avgCalories: Double,
    val protein: Double,
    val avgProtein: Double,
    val steps: Long,
    val cardioKm: Double,
    val consistencyPct: Int,
    val daysLogged: Int
)

data class Streaks(
    val gym: Int,
    val nutrition: Int,
    val protein: Int,
    val overall: Int,
    val longestOverall: Int
)

data class PersonalRecord(
    val exerciseName: String,
    val bestWeightKg: Double,
    val bestRepsAtBest: Int,
    val estimatedOneRm: Double,
    val totalVolumeKg: Double,
    val achievedEpochDay: Long,
    val previousBestKg: Double?
)

data class FitnessSnapshot(
    val name: String,
    val goalType: String,
    val calorieTarget: Int,
    val proteinTargetG: Int,
    val waterTargetMl: Int,
    val stepTarget: Int,
    val weightKg: Double?,
    val weightTrendKgPerWeek: Double?,
    val avgCalories7: Double,
    val avgProtein7: Double,
    val caloriesToday: Double,
    val proteinToday: Double,
    val waterTodayMl: Int,
    val stepsToday: Int,
    val workoutsThisWeek: Int,
    val workoutsLastWeek: Int,
    val avgWorkoutsPerWeek4w: Double,
    val cardioKm7: Double,
    val avgSteps7: Double,
    val recentExercises: List<String>,
    val strengthProgression: Map<String, StrengthDelta>,
    val habitRates: Map<String, Double>,
    val gymStreak: Int,
    val consistencyPct: Int
)

data class StrengthDelta(
    val exercise: String,
    val previousBestKg: Double,
    val currentBestKg: Double
)
