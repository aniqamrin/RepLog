package com.replog.app.di

import android.content.Context
import androidx.room.Room
import com.replog.app.BuildConfig
import com.replog.app.data.local.ActivityDao
import com.replog.app.data.local.AiDao
import com.replog.app.data.local.CardioDao
import com.replog.app.data.local.CheckInDao
import com.replog.app.data.local.FoodDao
import com.replog.app.data.local.GoalDao
import com.replog.app.data.local.HabitDao
import com.replog.app.data.local.PrDao
import com.replog.app.data.local.RepLogDatabase
import com.replog.app.data.local.UserProfileDao
import com.replog.app.data.local.WorkoutDao
import com.replog.app.data.remote.ApiService
import com.replog.app.data.remote.AuthTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RepLogDatabase =
        Room.databaseBuilder(context, RepLogDatabase::class.java, "replog.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun userProfileDao(db: RepLogDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun goalDao(db: RepLogDatabase): GoalDao = db.goalDao()
    @Provides fun habitDao(db: RepLogDatabase): HabitDao = db.habitDao()
    @Provides fun foodDao(db: RepLogDatabase): FoodDao = db.foodDao()
    @Provides fun workoutDao(db: RepLogDatabase): WorkoutDao = db.workoutDao()
    @Provides fun cardioDao(db: RepLogDatabase): CardioDao = db.cardioDao()
    @Provides fun weightDao(db: RepLogDatabase) = db.weightDao()
    @Provides fun checkInDao(db: RepLogDatabase): CheckInDao = db.checkInDao()
    @Provides fun activityDao(db: RepLogDatabase): ActivityDao = db.activityDao()
    @Provides fun prDao(db: RepLogDatabase): PrDao = db.prDao()
    @Provides fun aiDao(db: RepLogDatabase): AiDao = db.aiDao()

    @Provides
    @Singleton
    fun provideApiService(tokenStore: AuthTokenStore): ApiService =
        ApiService.create(BuildConfig.API_BASE_URL) { tokenStore.token }
}
