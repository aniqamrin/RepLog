package com.replog.app.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.replog.app.R
import com.replog.app.RepLogApp
import com.replog.app.data.local.FoodEntryEntity
import com.replog.app.data.remote.ApiService
import com.replog.app.data.remote.AuthTokenStore
import com.replog.app.data.remote.SyncBatchRequest
import com.replog.app.data.remote.SyncFoodEntry
import com.replog.app.data.remote.SyncWeight
import com.replog.app.data.remote.SyncWorkout
import com.replog.app.data.repository.NutritionRepository
import com.replog.app.data.repository.WeightRepository
import com.replog.app.data.local.RepLogDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: RepLogDatabase,
    private val api: ApiService,
    private val tokenStore: AuthTokenStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val token = tokenStore.token ?: return Result.success()
        return try {
            val foodPending = db.foodDao().pending(200)
            val weights = db.weightDao().between(today() - 30, today())
            val workouts = db.workoutDao().between(today() - 30, today())
            if (foodPending.isEmpty() && weights.isEmpty() && workouts.isEmpty()) {
                return Result.success()
            }
            val volumeByWorkout = db.workoutDao().volumeByWorkout().associate { it.workoutId to it.volume }
            val ack = api.sync(
                "Bearer $token",
                SyncBatchRequest(
                    device_id = tokenStore.deviceId,
                    food_entries = foodPending.map { it.toSync() },
                    workouts = workouts.map { w ->
                        SyncWorkout(
                            client_id = w.id, date = epochDayToDate(w.epochDay),
                            name = w.name, type = w.type, duration_min = w.durationMin, notes = w.notes,
                            volume_kg = volumeByWorkout[w.id] ?: 0.0
                        )
                    },
                    weight_entries = weights.map { w ->
                        SyncWeight(
                            date = epochDayToDate(w.epochDay),
                            weight_kg = w.weightKg, body_fat_pct = w.bodyFatPct
                        )
                    }
                )
            )
            if (ack.accepted > 0 || ack.server_time.isNotBlank()) {
                db.foodDao().markSynced(foodPending.map { it.id })
            }
            notifySynced()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun FoodEntryEntity.toSync() = SyncFoodEntry(
        client_id = id,
        date = epochDayToDate(epochDay),
        name = name,
        meal_type = mealType,
        calories = calories,
        protein_g = proteinG,
        carbs_g = carbsG,
        fat_g = fatG,
        fiber_g = fiberG
    )

    private fun today(): Long = LocalDate.now().toEpochDay()

    private fun epochDayToDate(day: Long): String = LocalDate.ofEpochDay(day).toString()

    private fun notifySynced() {
        try {
            val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            val notification = NotificationCompat.Builder(applicationContext, RepLogApp.CHANNEL_SYNC)
                .setSmallIcon(R.drawable.ic_stat_replog)
                .setContentTitle("REPLOG")
                .setContentText("Data synced")
                .setSilent(true)
                .setAutoCancel(true)
                .build()
            manager.notify(3001, notification)
        } catch (_: Exception) {
        }
    }
}
