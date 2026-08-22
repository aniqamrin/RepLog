package com.replog.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        GoalEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FoodEntryEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseSetEntity::class,
        CardioSessionEntity::class,
        WeightEntryEntity::class,
        CheckInEntity::class,
        ActivityEventEntity::class,
        PersonalRecordEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RepLogDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun goalDao(): GoalDao
    abstract fun habitDao(): HabitDao
    abstract fun foodDao(): FoodDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun cardioDao(): CardioDao
    abstract fun weightDao(): WeightDao
    abstract fun checkInDao(): CheckInDao
    abstract fun activityDao(): ActivityDao
    abstract fun prDao(): PrDao
    abstract fun aiDao(): AiDao
}
