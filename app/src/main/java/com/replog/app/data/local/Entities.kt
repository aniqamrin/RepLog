package com.replog.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String,
    val email: String? = null,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: Long = 1L,
    val goalType: String = "GENERAL_FITNESS",
    val calorieTarget: Int = 2500,
    val proteinTargetG: Int = 150,
    val carbsTargetG: Int = 280,
    val fatTargetG: Int = 80,
    val fiberTargetG: Int = 30,
    val waterTargetMl: Int = 3000,
    val stepTarget: Int = 10000,
    val workoutDaysPerWeek: Int = 5,
    val updatedAtMillis: Long = 0L
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String? = null,
    val archived: Boolean = false,
    val createdAtMillis: Long = 0L
)

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)]
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val habitId: Long,
    val epochDay: Long,
    val completed: Boolean
)

@Entity(tableName = "food_entries", indices = [Index("epochDay")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val name: String,
    val mealType: String,
    val servingSize: String? = null,
    val quantity: Double = 1.0,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val timeMinutes: Int,
    val pendingSync: Boolean = false,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "workouts", indices = [Index("epochDay")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val name: String,
    val type: String,
    val durationMin: Int,
    val notes: String? = null,
    val completed: Boolean = true,
    val pendingSync: Boolean = false,
    val createdAtMillis: Long = 0L
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long,
    val exerciseName: String,
    val position: Int,
    val restSeconds: Int = 90,
    val notes: String? = null
)

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutExerciseId")]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val done: Boolean = true
)

@Entity(tableName = "cardio_sessions", indices = [Index("epochDay")])
data class CardioSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val type: String,
    val distanceKm: Double,
    val durationMin: Double,
    val calories: Int,
    val notes: String? = null,
    val pendingSync: Boolean = false,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "weight_entries", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "check_ins", indices = [Index(value = ["epochDay"], unique = true)])
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val trained: Boolean,
    val energyLevel: Int,
    val mood: Int,
    val sleepQuality: Int,
    val hitCalories: Boolean,
    val hitProtein: Boolean,
    val steps: Int,
    val notes: String? = null,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "activity_events", indices = [Index("epochDay"), Index("type")])
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val type: String,
    val intensity: Int,
    val title: String,
    val detail: String? = null,
    val value: Double? = null,
    val createdAtMillis: Long = 0L
)

@Entity(tableName = "personal_records", indices = [Index(value = ["exerciseName"], unique = true)])
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val exerciseName: String,
    val bestWeightKg: Double,
    val bestRepsAtBest: Int,
    val estimatedOneRmKg: Double,
    val totalVolumeKg: Double,
    val achievedEpochDay: Long,
    val previousBestKg: Double? = null,
    val updatedAtMillis: Long = 0L
)

@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long = 0L
)

@Entity(
    tableName = "ai_messages",
    foreignKeys = [ForeignKey(
        entity = AiConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAtMillis: Long = 0L
)
