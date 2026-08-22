package com.replog.app.data.repository

import com.replog.app.data.local.RepLogDatabase
import com.replog.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

@Singleton
class DemoSeeder @Inject constructor(
    private val db: RepLogDatabase,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val nutritionRepository: NutritionRepository,
    private val workoutRepository: WorkoutRepository,
    private val cardioRepository: CardioRepository,
    private val weightRepository: WeightRepository,
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository
) {
    suspend fun seedIfEmpty() {
        if (settingsRepository.settings.first().seeded) return
        profileRepository.ensureDefaults("Alex")
        seed()
        settingsRepository.setSeeded()
    }

    suspend fun reseed() {
        db.clearAllTables()
        profileRepository.ensureDefaults("Alex")
        seed()
        settingsRepository.setSeeded()
    }

    private suspend fun seed() {
        val rnd = Random(424242)
        val today = LocalDate.now().toEpochDay()
        val start = today - 182
        val now = System.currentTimeMillis()

        listOf(
            "Gym" to "🏋️", "Protein" to "🥩", "Water" to "💧",
            "Sleep 8h" to "😴", "Stretching" to "🧘"
        ).forEach { (name, emoji) ->
            habitRepository.addHabit(name)
        }
        val habits = db.habitDao().getAll()

        var weight = 78.4
        val workoutSplit = listOf("Push Day", "Pull Day", "Leg Day", "Full Body")

        for (day in start..today) {
            val daysIn = (day - start).toInt()
            val progress = daysIn / 182.0
            val dow = ((day + 3) % 7)

            // Weight: slow cut with noise and weekly weigh-in
            weight -= 0.028 + rnd.nextDouble(0.01)
            weight += (rnd.nextDouble(-0.25, 0.25))
            if (dow == 6L || day == today) {
                weightRepository.upsert(day, (weight * 10).toInt() / 10.0, null)
            }

            // Gym: ~4 sessions/week, rest on Mon/Fri pattern with misses
            val plannedGym = dow in setOf(1L, 2L, 4L, 5L)
            val gymToday = plannedGym && rnd.nextDouble() > 0.12 && day < today
            if (gymToday) {
                val name = workoutSplit[daysIn % workoutSplit.size]
                val duration = 45 + rnd.nextInt(30)
                val exercises = exercisesFor(name, progress, rnd)
                workoutRepository.save(
                    WorkoutDraft(
                        epochDay = day,
                        name = name,
                        type = typeFor(name),
                        durationMin = duration,
                        notes = null,
                        exercises = exercises
                    )
                )
            }

            // Cardio: 2x per week run
            if (dow in setOf(3L, 6L) && rnd.nextDouble() > 0.2) {
                val km = (2.5 + rnd.nextDouble(6.0))
                val pace = 5.0 + rnd.nextDouble(1.8)
                cardioRepository.add(
                    epochDay = day,
                    type = if (rnd.nextDouble() < 0.7) "RUNNING" else "CYCLING",
                    distanceKm = (km * 10).toInt() / 10.0,
                    durationMin = km * pace,
                    calories = (km * 62).toInt()
                )
            }

            // Food: 3 meals + snack hitting targets with variance
            val targetShift = when {
                progress > 0.75 -> 2400
                else -> 2500
            }
            val dayCalories = targetShift + rnd.nextInt(-350, 300)
            val dayProtein = 140 + rnd.nextInt(35)
            val meals = mealPlan(rnd)
            val totalPlanKcal = meals.sumOf { it.second }
            var timeMinutes = 7 * 60 + 30
            for ((food, kcalShare, proteinPct) in meals) {
                val share = kcalShare / totalPlanKcal
                val kcal = (dayCalories * share).toInt().coerceAtLeast(80)
                val protein = (dayProtein * proteinPct / 100.0).roundToIntCompat()
                nutritionRepository.addFood(
                    epochDay = day,
                    name = food.first,
                    mealType = food.second,
                    servingSize = null,
                    quantity = 1.0,
                    calories = kcal,
                    proteinG = protein.toDouble(),
                    carbsG = (kcal * 0.45 / 4),
                    fatG = (kcal * 0.27 / 9),
                    fiberG = (4 + rnd.nextInt(8)).toDouble(),
                    timeMinutes = timeMinutes,
                    source = "demo"
                )
                timeMinutes += 4 * 60 + rnd.nextInt(60)
            }

            // Water
            val waterGoalHit = rnd.nextDouble() < 0.72
            val totalWater = if (waterGoalHit) 2600 + rnd.nextInt(900) else 1200 + rnd.nextInt(1200)
            var added = 0
            while (added < totalWater) {
                val glass = minOf(400, totalWater - added)
                nutritionRepository.addWater(day, glass)
                added += glass
            }

            // Steps via check-in
            val steps = 5500 + rnd.nextInt(7000)
            checkInRepository.save(
                com.replog.app.data.local.CheckInEntity(
                    epochDay = day,
                    trained = gymToday,
                    energyLevel = 2 + rnd.nextInt(4),
                    mood = 2 + rnd.nextInt(4),
                    sleepQuality = 2 + rnd.nextInt(4),
                    hitCalories = rnd.nextDouble() < 0.8,
                    hitProtein = rnd.nextDouble() < 0.78,
                    steps = steps,
                    notes = null,
                    createdAtMillis = now
                )
            )

            // Habits completion
            for (habit in habits) {
                val chance = when (habit.name) {
                    "Gym" -> if (gymToday) 1.0 else 0.05
                    "Protein" -> 0.78
                    "Water" -> 0.72
                    "Sleep 8h" -> 0.6
                    else -> 0.55
                }
                if (rnd.nextDouble() < chance) {
                    habitRepository.toggle(habit.id, day)
                }
            }
        }
    }

    private fun exercisesFor(workoutName: String, progress: Double, rnd: Random): List<ExerciseDraft> {
        val base = when (workoutName) {
            "Push Day" -> listOf(
                Triple("Bench Press", 60.0, 8),
                Triple("Overhead Press", 40.0, 8),
                Triple("Incline Dumbbell Press", 22.0, 10),
                Triple("Tricep Rope Pushdown", 25.0, 12)
            )
            "Pull Day" -> listOf(
                Triple("Deadlift", 100.0, 5),
                Triple("Barbell Row", 55.0, 8),
                Triple("Lat Pulldown", 50.0, 10),
                Triple("Face Pull", 20.0, 15)
            )
            "Leg Day" -> listOf(
                Triple("Squat", 85.0, 6),
                Triple("Romanian Deadlift", 70.0, 8),
                Triple("Leg Press", 160.0, 10),
                Triple("Calf Raise", 60.0, 15)
            )
            else -> listOf(
                Triple("Squat", 70.0, 6),
                Triple("Bench Press", 52.0, 8),
                Triple("Barbell Row", 48.0, 8),
                Triple("Plank", 0.0, 3)
            )
        }
        return base.mapIndexed { idx, (name, startWeight, reps) ->
            val progression = 1.0 + progress * 0.14 + rnd.nextDouble(-0.02, 0.03)
            val w = (startWeight * progression * 2).toInt() / 2.0
            ExerciseDraft(
                exerciseName = name,
                restSeconds = if (idx == 0) 180 else 90,
                notes = null,
                sets = (0 until if (idx == 0) 3 else 3).map { s ->
                    SetDraft(reps = maxOf(3, reps - s), weightKg = w)
                }
            )
        }
    }

    private fun typeFor(name: String): String = when (name) {
        "Push Day" -> "PUSH"
        "Pull Day" -> "PULL"
        "Leg Day" -> "LEGS"
        else -> "FULL_BODY"
    }

    private fun mealPlan(rnd: Random): List<Triple<Pair<String, String>, Int, Int>> {
        val breakfasts = listOf(
            "Oats with whey and banana" to Meal.BREAKFAST,
            "Eggs and toast" to Meal.BREAKFAST,
            "Greek yogurt bowl" to Meal.BREAKFAST
        )
        val lunches = listOf(
            "Chicken rice bowl" to Meal.LUNCH,
            "Turkey sandwich" to Meal.LUNCH,
            "Beef stir fry with rice" to Meal.LUNCH
        )
        val dinners = listOf(
            "Salmon with potatoes" to Meal.DINNER,
            "Chicken pasta" to Meal.DINNER,
            "Lean beef tacos" to Meal.DINNER
        )
        val snacks = listOf("Whey shake" to Meal.SNACK, "Almonds and fruit" to Meal.SNACK)
        return listOf(
            Triple(breakfasts[rnd.nextInt(breakfasts.size)], 26, 25),
            Triple(lunches[rnd.nextInt(lunches.size)], 34, 35),
            Triple(dinners[rnd.nextInt(dinners.size)], 33, 34),
            Triple(snacks[rnd.nextInt(snacks.size)], 7, 6)
        )
    }

    private object Meal {
        const val BREAKFAST = "BREAKFAST"
        const val LUNCH = "LUNCH"
        const val DINNER = "DINNER"
        const val SNACK = "SNACK"
    }
}

private fun Double.roundToIntCompat(): Int = roundToInt()
