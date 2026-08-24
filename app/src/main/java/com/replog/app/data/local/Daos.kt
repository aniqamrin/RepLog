package com.replog.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MacroRow(
    val calories: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?
) {
    val kcal: Double get() = calories ?: 0.0
    val protein: Double get() = proteinG ?: 0.0
    val carbs: Double get() = carbsG ?: 0.0
    val fat: Double get() = fatG ?: 0.0
    val fiber: Double get() = fiberG ?: 0.0
}

data class DayMacroRow(
    val epochDay: Long,
    val calories: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?
) {
    val protein: Double get() = proteinG ?: 0.0
}

data class SetWithDay(
    val id: Long,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val done: Boolean,
    val epochDay: Long,
    val exerciseName: String
)

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE id = 1")
    fun observe(): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = 1")
    suspend fun get(): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAtMillis ASC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAtMillis ASC")
    suspend fun getAll(): List<HabitEntity>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Query("UPDATE habits SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeLogsBetween(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay BETWEEN :from AND :to AND completed = 1")
    suspend fun completedBetween(habitId: Long, from: Long, to: Long): List<HabitLogEntity>

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND epochDay = :day AND completed = 1")
    suspend fun isCompleted(habitId: Long, day: Long): Int

    @Query("SELECT * FROM habit_logs WHERE epochDay = :day")
    suspend fun logsForDay(day: Long): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: HabitLogEntity)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY timeMinutes ASC")
    fun observeByDay(day: Long): Flow<List<FoodEntryEntity>>

    @Query(
        "SELECT SUM(calories) AS calories, SUM(proteinG) AS proteinG, SUM(carbsG) AS carbsG, " +
            "SUM(fatG) AS fatG, SUM(fiberG) AS fiberG FROM food_entries WHERE epochDay = :day"
    )
    fun observeTotalsByDay(day: Long): Flow<MacroRow?>

    @Query(
        "SELECT epochDay, SUM(calories) AS calories, SUM(proteinG) AS proteinG, SUM(carbsG) AS carbsG, " +
            "SUM(fatG) AS fatG, SUM(fiberG) AS fiberG FROM food_entries WHERE epochDay BETWEEN :from AND :to " +
            "GROUP BY epochDay ORDER BY epochDay ASC"
    )
    fun observeTotalsRange(from: Long, to: Long): Flow<List<DayMacroRow>>

    @Query("SELECT DISTINCT epochDay FROM food_entries WHERE epochDay BETWEEN :from AND :to")
    suspend fun loggedDays(from: Long, to: Long): List<Long>

    @Query("SELECT DISTINCT epochDay FROM food_entries WHERE epochDay BETWEEN :from AND :to AND proteinG >= :proteinMin")
    suspend fun daysProteinHit(from: Long, to: Long, proteinMin: Double): List<Long>

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntryEntity>)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE food_entries SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM food_entries WHERE pendingSync = 1 LIMIT :limit")
    suspend fun pending(limit: Int): List<FoodEntryEntity>
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY epochDay DESC, createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC")
    suspend fun between(from: Long, to: Long): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts WHERE epochDay BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT DISTINCT epochDay FROM workouts WHERE epochDay BETWEEN :from AND :to")
    fun observeGymDaysBetween(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM workouts WHERE epochDay BETWEEN :from AND :to")
    fun observeCountBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT DISTINCT epochDay FROM workouts WHERE epochDay BETWEEN :from AND :to")
    suspend fun gymDaysBetween(from: Long, to: Long): List<Long>

    @Query(
        "SELECT COALESCE(SUM(s.weightKg * s.reps), 0.0) FROM exercise_sets s " +
            "INNER JOIN workout_exercises e ON s.workoutExerciseId = e.id " +
            "INNER JOIN workouts w ON e.workoutId = w.id WHERE w.epochDay BETWEEN :from AND :to"
    )
    suspend fun volumeBetween(from: Long, to: Long): Double

    @Query(
        "SELECT COALESCE(AVG(durationMin), 0.0) FROM workouts WHERE epochDay BETWEEN :from AND :to"
    )
    suspend fun avgDurationBetween(from: Long, to: Long): Double

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWithExercises(id: Long): Flow<WorkoutWithExercises?>

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>): List<Long>

    @Insert
    suspend fun insertSets(sets: List<ExerciseSetEntity>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT s.id AS id, s.workoutExerciseId AS workoutExerciseId, s.setIndex AS setIndex, " +
            "s.reps AS reps, s.weightKg AS weightKg, s.done AS done, w.epochDay AS epochDay, " +
            "e.exerciseName AS exerciseName FROM exercise_sets s " +
            "INNER JOIN workout_exercises e ON s.workoutExerciseId = e.id " +
            "INNER JOIN workouts w ON e.workoutId = w.id " +
            "WHERE LOWER(e.exerciseName) = LOWER(:name) AND s.done = 1 ORDER BY w.epochDay ASC"
    )
    suspend fun setsForExercise(name: String): List<SetWithDay>

    @Query(
        "SELECT e.workoutId AS workoutId, COALESCE(SUM(s.weightKg * s.reps), 0.0) AS volume " +
            "FROM exercise_sets s INNER JOIN workout_exercises e ON s.workoutExerciseId = e.id " +
            "GROUP BY e.workoutId"
    )
    suspend fun volumeByWorkout(): List<WorkoutVolumeRow>

    @Query(
        "SELECT DISTINCT e.exerciseName FROM workout_exercises e INNER JOIN workouts w ON e.workoutId = w.id " +
            "ORDER BY w.epochDay DESC LIMIT :limit"
    )
    suspend fun recentExerciseNames(limit: Int): List<String>
}

data class WorkoutVolumeRow(val workoutId: Long, val volume: Double)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithSets>
)

data class WorkoutExerciseWithSets(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<ExerciseSetEntity>
)

@Dao
interface CardioDao {
    @Query("SELECT * FROM cardio_sessions ORDER BY epochDay DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<CardioSessionEntity>>

    @Query("SELECT COALESCE(SUM(distanceKm), 0.0) FROM cardio_sessions WHERE epochDay BETWEEN :from AND :to")
    suspend fun totalDistanceBetween(from: Long, to: Long): Double

    @Query("SELECT COUNT(*) FROM cardio_sessions WHERE epochDay BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query(
        "SELECT epochDay, SUM(distanceKm) AS total FROM cardio_sessions " +
            "WHERE epochDay BETWEEN :from AND :to GROUP BY epochDay"
    )
    fun observeDistanceByDayBetween(from: Long, to: Long): Flow<List<DayValueRow>>

    @Query(
        "SELECT epochDay, SUM(distanceKm) AS total FROM cardio_sessions " +
            "WHERE epochDay BETWEEN :from AND :to GROUP BY epochDay"
    )
    suspend fun rawDistanceByDay(from: Long, to: Long): List<DayValueRow>

    @Insert
    suspend fun insert(session: CardioSessionEntity): Long

    @Query("DELETE FROM cardio_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class DayValueRow(val epochDay: Long, val total: Double)

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun between(from: Long, to: Long): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries ORDER BY epochDay ASC")
    suspend fun all(): List<WeightEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity): Long
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE epochDay = :day")
    fun observeForDay(day: Long): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE epochDay BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE epochDay = :day")
    suspend fun forDay(day: Long): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkIn: CheckInEntity): Long

    @Query("UPDATE check_ins SET steps = :steps WHERE epochDay = :day")
    suspend fun updateSteps(day: Long, steps: Int)

    @Query("UPDATE check_ins SET steps = steps + :delta WHERE epochDay = :day")
    suspend fun addSteps(day: Long, delta: Int)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_events WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, createdAtMillis DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_events WHERE epochDay = :day ORDER BY createdAtMillis DESC")
    fun observeForDay(day: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_events WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun listBetween(from: Long, to: Long): List<ActivityEventEntity>

    @Query("SELECT COUNT(*) FROM activity_events")
    suspend fun totalCount(): Int

    @Insert
    suspend fun insert(event: ActivityEventEntity): Long
}

@Dao
interface PrDao {
    @Query("SELECT * FROM personal_records ORDER BY estimatedOneRmKg DESC")
    fun observeAll(): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records")
    suspend fun all(): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records WHERE LOWER(exerciseName) = LOWER(:name)")
    suspend fun byName(name: String): PersonalRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PersonalRecordEntity)

    @Query("DELETE FROM personal_records WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_conversations ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatestConversation(): Flow<AiConversationEntity?>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>>

    @Insert
    suspend fun insertConversation(conversation: AiConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("DELETE FROM ai_conversations")
    suspend fun clearConversations()
}

@Dao
interface SyncDao {
    @Update
    suspend fun updateWorkouts(workouts: List<WorkoutEntity>)
}
