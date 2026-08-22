package com.replog.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.replog.app.RepLogApp
import com.replog.app.data.local.FoodDao
import com.replog.app.data.local.WorkoutDao
import com.replog.app.data.prefs.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val foodDao: FoodDao,
    private val workoutDao: WorkoutDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        val today = LocalDate.now().toEpochDay()
        val hour = LocalTime.now().hour

        if (hour < 11 || hour > 22) return Result.success()
        if (!canNotify()) return Result.success()

        val totals = foodDao.observeTotalsByDay(today).first()

        if (settings.remindFood && hour >= 13 && (totals == null || (totals.kcal ?: 0.0) < 400)) {
            notify(1001, "Time to log your food", "Nothing logged yet today — add breakfast or lunch.")
        }

        if (settings.remindWorkout && hour >= 17 && workoutDao.countBetween(today, today) == 0) {
            notify(1002, "You haven't trained yet today", "A 30-minute session keeps the streak alive.")
        }

        if (settings.remindCheckIn && hour >= 20 && hour <= 21) {
            notify(1003, "Daily check-in", "30 seconds: log energy, sleep and how today went.")
        }

        return Result.success()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun notify(id: Int, title: String, body: String) {
        try {
            val notification = NotificationCompat.Builder(applicationContext, RepLogApp.CHANNEL_REMINDERS)
                .setSmallIcon(com.replog.app.R.drawable.ic_stat_replog)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
