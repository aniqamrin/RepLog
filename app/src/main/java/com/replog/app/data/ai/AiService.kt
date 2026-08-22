package com.replog.app.data.ai

import com.replog.app.domain.logic.AiContextBuilder
import com.replog.app.domain.model.FitnessSnapshot
import javax.inject.Inject
import javax.inject.Singleton

interface AiService {
    suspend fun chat(question: String, history: List<Pair<String, String>>, snapshot: FitnessSnapshot): Result<String>
    suspend fun analyzeFood(base64Jpeg: String): Result<String>
    val isRemote: Boolean
}

@Singleton
class OfflineCoach @Inject constructor() {

    fun reply(question: String, s: FitnessSnapshot): String {
        val q = question.lowercase()
        return when {
            containsAny(q, "ate", "eat today", "what did i eat", "food today") ->
                "You've logged ${fmt(s.caloriesToday)} kcal and ${fmt(s.proteinToday)}g protein today " +
                    "against targets of ${s.calorieTarget} kcal / ${s.proteinTargetG}g. " +
                    remainingLine(s)
            containsAny(q, "protein left", "protein have left") -> {
                val left = (s.proteinTargetG - s.proteinToday).coerceAtLeast(0.0)
                "You have ${fmt(left)}g protein left today (${fmt(s.proteinToday)}g of ${s.proteinTargetG}g logged)."
            }
            containsAny(q, "calories left", "calorie budget", "fit this meal") -> {
                val left = (s.calorieTarget - s.caloriesToday).coerceAtLeast(0.0)
                "You have ${fmt(left)} kcal left today. A meal around ${fmt((left * 0.6).coerceAtLeast(200.0))} kcal would keep you on track."
            }
            containsAny(q, "high protein dinner", "dinner idea", "what should i eat tonight") ->
                "With ${fmt((s.calorieTarget - s.caloriesToday).coerceAtLeast(0.0))} kcal and " +
                    "${fmt((s.proteinTargetG - s.proteinToday).coerceAtLeast(0.0))}g protein left, a grilled chicken bowl " +
                    "(180g chicken, rice, vegetables) is roughly 550 kcal with 45g protein — a strong fit."
            containsAny(q, "how did i do this week", "this week", "did i train enough") ->
                "This week you trained ${s.workoutsThisWeek} time(s), averaged ${fmt(s.avgCalories7)} kcal and " +
                    "${fmt(s.avgProtein7)}g protein per day, with ${fmt(s.cardioKm7)} km cardio. " +
                    "Consistency is at ${s.consistencyPct}%."
            containsAny(q, "consistent", "consistency") ->
                "Your consistency score over recent weeks is ${s.consistencyPct}%, with a current gym streak of ${s.gymStreak} day(s)."
            containsAny(q, "why am i not progressing", "not progressing", "plateau") -> {
                val trend = s.weightTrendKgPerWeek?.let { fmt(it) } ?: "unknown"
                "Weight trend is $trend kg/week at ${s.weightKg ?: "?"}kg, training ${s.workoutsThisWeek}x this week " +
                    "(4-week average ${fmt(s.avgWorkoutsPerWeek4w)}x). If progress stalled, check whether calories or " +
                    "session volume drifted from your plan before changing anything else."
            }
            containsAny(q, "bench", "squat", "deadlift") -> {
                val match = s.strengthProgression.entries.firstOrNull { q.contains(it.key.lowercase()) }
                if (match != null) {
                    "Your ${match.key}: previous best ${fmt(match.value.previousBestKg)}kg, current best ${fmt(match.value.currentBestKg)}kg."
                } else {
                    "Recent lifts tracked: ${s.strengthProgression.keys.take(5).joinToString()}. Ask me about any of them by name."
                }
            }
            containsAny(q, "compare this month", "last month") ->
                "This month you averaged ${fmt(s.avgCalories7)} kcal/day recently, trained ~${fmt(s.avgWorkoutsPerWeek4w)}x/week, " +
                    "and consistency sits at ${s.consistencyPct}%."
            containsAny(q, "water") ->
                "You've logged ${s.waterTodayMl}ml of water today against a ${s.waterTargetMl}ml target."
            containsAny(q, "steps") ->
                "You're at ${s.stepsToday} steps today vs your ${s.stepTarget} target (7-day avg ${s.avgSteps7.toInt()})."
            else ->
                "Here's where you stand: ${fmt(s.caloriesToday)}/${s.calorieTarget} kcal, " +
                    "${fmt(s.proteinToday)}/${s.proteinTargetG}g protein, ${s.workoutsThisWeek} workout(s) this week, " +
                    "consistency ${s.consistencyPct}%. Ask me about your week, protein, calories, lifts, or what to eat tonight."
        }
    }

    fun dailySummary(s: FitnessSnapshot): String {
        val kcalDelta = s.calorieTarget - s.caloriesToday
        val kcalPart = if (kcalDelta >= 0) {
            "${fmt(kcalDelta)} kcal below your target"
        } else {
            "${fmt(-kcalDelta)} kcal above your target"
        }
        val pDelta = s.proteinTargetG - s.proteinToday
        val proteinPart = if (pDelta >= 0) {
            "just ${fmt(pDelta)}g short of your goal"
        } else {
            "${fmt(-pDelta)}g over your goal"
        }
        val suggestion = when {
            pDelta > 25 -> "A Greek yogurt or a scoop of whey would close the protein gap easily."
            kcalDelta > 500 && s.waterTodayMl < s.waterTargetMl * 0.6 -> "Consider a solid dinner soon, and keep water intake moving toward your ${s.waterTargetMl}ml target."
            kcalDelta > 500 -> "You have room for a proper dinner — aim to include a lean protein source."
            else -> "You're well on track for today. Keep it up."
        }
        return "You logged ${fmt(s.caloriesToday)} kcal today, which is $kcalPart. Protein was ${fmt(s.proteinToday)}g, $proteinPart. $suggestion"
    }

    private fun remainingLine(s: FitnessSnapshot): String {
        val left = (s.calorieTarget - s.caloriesToday).coerceAtLeast(0.0)
        return "That leaves ${fmt(left)} kcal to work with today."
    }

    private fun containsAny(q: String, vararg keys: String) = keys.any { q.contains(it) }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
}

@Singleton
class LocalAiService @Inject constructor(private val offlineCoach: OfflineCoach) : AiService {
    override val isRemote: Boolean get() = false

    override suspend fun chat(
        question: String,
        history: List<Pair<String, String>>,
        snapshot: FitnessSnapshot
    ): Result<String> = Result.success(offlineCoach.reply(question, snapshot))

    override suspend fun analyzeFood(base64Jpeg: String): Result<String> =
        Result.failure(UnsupportedOperationException("Food scanning requires the REPLOG backend with an AI vision model configured"))
}

object AiPromptFactory {
    const val SYSTEM_PROMPT =
        "You are REPLOG AI, a concise fitness and nutrition coach. You receive structured JSON context " +
            "with the user's real logged data. Reference only numbers present in that context. Never invent statistics. " +
            "Be direct, practical, and brief."

    fun visionPrompt(): String =
        "Analyze this food photo. Estimate every distinct food item with portion size, calories, protein, carbs, fat, fiber in grams, " +
            "and a confidence level (low/medium/high). Respond ONLY with JSON matching: " +
            "{\"foods\":[{\"name\":\"...\",\"portion\":\"150g\",\"calories\":250,\"protein\":42,\"carbs\":0,\"fat\":8,\"fiber\":0,\"confidence\":\"medium\"}],\"total\":{\"calories\":250,\"protein\":42,\"carbs\":0,\"fat\":8}}"

    fun buildMessages(history: List<Pair<String, String>>, question: String): List<com.replog.app.data.remote.ChatMessageDto> =
        history.takeLast(8).map { com.replog.app.data.remote.ChatMessageDto(it.first, it.second) } +
            com.replog.app.data.remote.ChatMessageDto("user", question)

    fun contextFor(snapshot: FitnessSnapshot): String = AiContextBuilder.build(snapshot)
}
