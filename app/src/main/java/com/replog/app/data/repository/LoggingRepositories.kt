package com.replog.app.data.repository

import androidx.room.withTransaction
import com.replog.app.data.local.CardioDao
import com.replog.app.data.local.CardioSessionEntity
import com.replog.app.data.local.CheckInDao
import com.replog.app.data.local.CheckInEntity
import com.replog.app.data.local.ExerciseSetEntity
import com.replog.app.data.local.FoodDao
import com.replog.app.data.local.FoodEntryEntity
import com.replog.app.data.local.HabitDao
import com.replog.app.data.local.HabitEntity
import com.replog.app.data.local.HabitLogEntity
import com.replog.app.data.local.MacroRow
import com.replog.app.data.local.PersonalRecordEntity
import com.replog.app.data.local.PrDao
import com.replog.app.data.local.RepLogDatabase
import com.replog.app.data.local.WeightDao
import com.replog.app.data.local.WeightEntryEntity
import com.replog.app.data.local.WorkoutDao
import com.replog.app.data.local.WorkoutExerciseEntity
import com.replog.app.data.local.WorkoutEntity
import com.replog.app.domain.logic.PRDetector
import com.replog.app.domain.logic.WorkoutMath
import com.replog.app.domain.model.ActivityType
import com.replog.app.domain.model.MacroTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private fun MacroRow?.toTotals() = MacroTotals(
    calories = this?.kcal ?: 0.0,
    protein = this?.protein ?: 0.0,
    carbs = this?.carbs ?: 0.0,
    fat = this?.fat ?: 0.0,
    fiber = this?.fiber ?: 0.0
)

@Singleton
class NutritionRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val activityRepository: ActivityRepository
) {
    fun observeDay(day: Long): Flow<List<FoodEntryEntity>> = foodDao.observeByDay(day)

    fun observeTotals(day: Long): Flow<MacroTotals> =
        foodDao.observeTotalsByDay(day).map { it.toTotals() }

    suspend fun addFood(
        epochDay: Long,
        name: String,
        mealType: String,
        servingSize: String?,
        quantity: Double,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double,
        timeMinutes: Int,
        source: String = "manual"
    ): Long {
        val id = foodDao.insert(
            FoodEntryEntity(
                epochDay = epochDay, name = name, mealType = mealType,
                servingSize = servingSize, quantity = quantity,
                calories = calories, proteinG = proteinG, carbsG = carbsG,
                fatG = fatG, fiberG = fiberG, timeMinutes = timeMinutes,
                pendingSync = true, createdAtMillis = System.currentTimeMillis()
            )
        )
        activityRepository.record(ActivityType.FOOD, epochDay, intensityOf(calories), "$name logged", source, calories.toDouble())
        if (proteinG > 0) {
            activityRepository.record(ActivityType.PROTEIN, epochDay, 1, "$name", "${proteinG}g protein", proteinG)
        }
        return id
    }

    private fun intensityOf(kcal: Int): Int = when {
        kcal >= 700 -> 4
        kcal >= 450 -> 3
        kcal >= 200 -> 2
        else -> 1
    }

    suspend fun delete(entryId: Long) = foodDao.deleteById(entryId)

    suspend fun addWater(epochDay: Long, ml: Int): Boolean {
        require(ml > 0)
        activityRepository.record(ActivityType.WATER, epochDay, 1, "+${ml} ml water", null, ml.toDouble())
        return true
    }

    suspend fun addSteps(epochDay: Long, steps: Int): Boolean {
        require(steps > 0)
        activityRepository.record(ActivityType.STEPS, epochDay, 2, "$steps steps", null, steps.toDouble())
        return true
    }

    suspend fun loggedDays(from: Long, to: Long): List<Long> = foodDao.loggedDays(from, to)

    suspend fun daysProteinHit(from: Long, to: Long, proteinMin: Double): List<Long> =
        foodDao.daysProteinHit(from, to, proteinMin)
}

data class ExerciseDraft(
    val exerciseName: String,
    val restSeconds: Int,
    val notes: String?,
    val sets: List<SetDraft>
)

data class SetDraft(val reps: Int, val weightKg: Double)

data class WorkoutDraft(
    val epochDay: Long,
    val name: String,
    val type: String,
    val durationMin: Int,
    val notes: String?,
    val exercises: List<ExerciseDraft>
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: RepLogDatabase,
    private val workoutDao: WorkoutDao,
    private val prDao: PrDao,
    private val activityRepository: ActivityRepository
) {
    fun observeRecent(limit: Int = 100): Flow<List<WorkoutEntity>> = workoutDao.observeRecent(limit)

    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?> = workoutDao.observeWithExercises(id)

    suspend fun save(draft: WorkoutDraft): Long {
        val workoutId = db.withTransaction {
            val wid = workoutDao.insert(
                WorkoutEntity(
                    epochDay = draft.epochDay, name = draft.name, type = draft.type,
                    durationMin = draft.durationMin, notes = draft.notes,
                    pendingSync = true, createdAtMillis = System.currentTimeMillis()
                )
            )
            val exerciseIds = workoutDao.insertExercises(
                draft.exercises.mapIndexed { idx, ex ->
                    WorkoutExerciseEntity(
                        workoutId = wid, exerciseName = ex.exerciseName,
                        position = idx, restSeconds = ex.restSeconds, notes = ex.notes
                    )
                }
            )
            val sets = draft.exercises.flatMapIndexed { exIdx, ex ->
                ex.sets.mapIndexed { setIdx, s ->
                    ExerciseSetEntity(
                        workoutExerciseId = exerciseIds[exIdx], setIndex = setIdx + 1,
                        reps = s.reps, weightKg = s.weightKg
                    )
                }
            }
            if (sets.isNotEmpty()) workoutDao.insertSets(sets)
            wid
        }

        activityRepository.record(
            ActivityType.GYM, draft.epochDay, if (draft.durationMin >= 45) 3 else 3,
            draft.name.ifBlank { draft.type }, "${draft.durationMin} min"
        )

        detectPRs()
        return workoutId
    }

    suspend fun detectPRs(): List<String> {
        val newPrs = mutableListOf<String>()
        val names = workoutDao.recentExerciseNames(40)
        for (name in names.distinct()) {
            val sets = workoutDao.setsForExercise(name)
            val candidate = PRDetector.buildRecord(name, sets.map { Triple(it.epochDay, it.weightKg, it.reps) }) ?: continue
            val existing = prDao.byName(name)
            if (existing == null) {
                prDao.upsert(candidate.toEntity(previousBest = null))
                if (candidate.bestWeightKg > 0) newPrs += name
            } else if (candidate.bestWeightKg > existing.bestWeightKg + 1e-9 ||
                (candidate.bestWeightKg == existing.bestWeightKg && candidate.bestRepsAtBest > existing.bestRepsAtBest)
            ) {
                prDao.upsert(candidate.toEntity(previousBest = existing.bestWeightKg))
                if (candidate.bestWeightKg > existing.bestWeightKg) newPrs += name
            } else {
                prDao.upsert(
                    candidate.toEntity(previousBest = existing.previousBestKg).copy(id = existing.id)
                )
            }
        }
        return newPrs
    }

    private fun com.replog.app.domain.model.PersonalRecord.toEntity(previousBest: Double?) =
        PersonalRecordEntity(
            exerciseName = exerciseName,
            bestWeightKg = bestWeightKg,
            bestRepsAtBest = bestRepsAtBest,
            estimatedOneRmKg = estimatedOneRm,
            totalVolumeKg = totalVolumeKg,
            achievedEpochDay = achievedEpochDay,
            previousBestKg = previousBest,
            updatedAtMillis = System.currentTimeMillis()
        )

    suspend fun delete(workoutId: Long) = workoutDao.deleteById(workoutId)

    fun observePRs(): Flow<List<PersonalRecordEntity>> = prDao.observeAll()

    suspend fun gymDays(from: Long, to: Long): List<Long> = workoutDao.gymDaysBetween(from, to)

    suspend fun countBetween(from: Long, to: Long): Int = workoutDao.countBetween(from, to)

    suspend fun volumeBetween(from: Long, to: Long): Double = workoutDao.volumeBetween(from, to)

    suspend fun avgDurationBetween(from: Long, to: Long): Double = workoutDao.avgDurationBetween(from, to)

    suspend fun recentExerciseNames(limit: Int = 8): List<String> = workoutDao.recentExerciseNames(limit)

    suspend fun setsForExercise(name: String) = workoutDao.setsForExercise(name)

    suspend fun strengthDeltas(): Map<String, Pair<Double?, Double>> =
        prDao.all().associate { it.exerciseName to (it.previousBestKg to it.bestWeightKg) }
}

@Singleton
class CardioRepository @Inject constructor(
    private val cardioDao: CardioDao,
    private val activityRepository: ActivityRepository
) {
    fun observeRecent(limit: Int = 50): Flow<List<CardioSessionEntity>> = cardioDao.observeRecent(limit)

    suspend fun add(
        epochDay: Long, type: String, distanceKm: Double, durationMin: Double, calories: Int
    ): Long {
        val id = cardioDao.insert(
            CardioSessionEntity(
                epochDay = epochDay, type = type, distanceKm = distanceKm,
                durationMin = durationMin, calories = calories,
                pendingSync = true, createdAtMillis = System.currentTimeMillis()
            )
        )
        activityRepository.record(
            ActivityType.CARDIO, epochDay,
            when { distanceKm >= 10 -> 4; distanceKm >= 5 -> 3; else -> 2 },
            type.lowercase().replaceFirstChar { it.uppercase() } + " session",
            "${distanceKm} km · ${durationMin.toInt()} min",
            distanceKm
        )
        return id
    }

    suspend fun delete(id: Long) = cardioDao.deleteById(id)

    suspend fun totalDistanceBetween(from: Long, to: Long) = cardioDao.totalDistanceBetween(from, to)

    suspend fun pace(distanceKm: Double, durationMin: Double): String =
        if (distanceKm <= 0.0 || durationMin <= 0.0) "–"
        else {
            val minPerKm = durationMin / distanceKm
            String.format("%d:%02d /km", minPerKm.toInt(), ((minPerKm % 1) * 60).toInt())
        }
}

@Singleton
class WeightRepository @Inject constructor(
    private val weightDao: WeightDao,
    private val activityRepository: ActivityRepository
) {
    fun observeAll(): Flow<List<WeightEntryEntity>> = weightDao.observeAll()

    fun observeLatest(): Flow<WeightEntryEntity?> = weightDao.observeLatest()

    suspend fun upsert(epochDay: Long, weightKg: Double, bodyFatPct: Double?): Boolean {
        require(weightKg in 20.0..400.0)
        weightDao.upsert(
            WeightEntryEntity(
                epochDay = epochDay, weightKg = weightKg, bodyFatPct = bodyFatPct,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        activityRepository.record(ActivityType.WEIGHT, epochDay, 2, "Weigh-in", "$weightKg kg", weightKg)
        return true
    }

    suspend fun between(from: Long, to: Long): List<WeightEntryEntity> = weightDao.between(from, to)
}

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val activityRepository: ActivityRepository
) {
    fun observeHabits(): Flow<List<HabitEntity>> = habitDao.observeHabits()

    suspend fun habitCompletionRates(days: Int, todayEpochDay: Long): Map<String, Double> {
        if (days <= 0) return emptyMap()
        val from = todayEpochDay - days + 1
        return habitDao.getAll().associate { habit ->
            val done = habitDao.completedBetween(habit.id, from, todayEpochDay).size
            habit.name to done.toDouble() / days
        }
    }

    fun observeLogsBetween(from: Long, to: Long): Flow<List<HabitLogEntity>> =
        habitDao.observeLogsBetween(from, to)

    suspend fun addHabit(name: String): Long =
        habitDao.insert(HabitEntity(name = name.trim(), createdAtMillis = System.currentTimeMillis()))

    suspend fun archiveHabit(id: Long) = habitDao.archive(id)

    suspend fun toggle(habitId: Long, day: Long): Boolean {
        val done = habitDao.isCompleted(habitId, day) > 0
        habitDao.upsertLog(HabitLogEntity(habitId = habitId, epochDay = day, completed = !done))
        if (!done) {
            val habits = habitDao.getAll()
            val habit = habits.firstOrNull { it.id == habitId }
            activityRepository.record(
                ActivityType.HABIT, day, 1, habit?.name ?: "Habit completed", null, null
            )
        }
        return !done
    }
}

@Singleton
class CheckInRepository @Inject constructor(
    private val checkInDao: CheckInDao,
    private val activityRepository: ActivityRepository
) {
    fun observeForDay(day: Long): Flow<CheckInEntity?> = checkInDao.observeForDay(day)

    suspend fun save(checkIn: CheckInEntity): Boolean {
        checkInDao.upsert(checkIn.copy(createdAtMillis = System.currentTimeMillis()))
        if (checkIn.trained) {
            activityRepository.record(ActivityType.GYM, checkIn.epochDay, 2, "Check-in: trained", null)
        }
        if (checkIn.steps > 0) {
            activityRepository.record(ActivityType.STEPS, checkIn.epochDay, 2, "${checkIn.steps} steps", null, checkIn.steps.toDouble())
        }
        return true
    }

    suspend fun addSteps(day: Long, delta: Int) {
        require(delta != 0)
        checkInDao.addSteps(day, delta)
        val updated = checkInDao.forDay(day)
        activityRepository.record(
            ActivityType.STEPS, day, 2, "${updated?.steps ?: delta} steps", null,
            (updated?.steps ?: delta).toDouble()
        )
    }
}
