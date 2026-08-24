package com.replog.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.replog.app.data.local.CheckInEntity
import com.replog.app.data.repository.CheckInRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Singleton
class StepCounterMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkInRepository: CheckInRepository
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        context.getSharedPreferences("replog_step_sensor", Context.MODE_PRIVATE)
    }
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var pendingDelta = 0

    fun start() {
        if (!hasPermission()) return
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        flush()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values.firstOrNull()?.toInt() ?: return
        val lastRaw = prefs.getInt(KEY_LAST_RAW, -1)

        val delta = when {
            lastRaw < 0 -> 0
            raw < lastRaw -> 0
            else -> raw - lastRaw
        }
        prefs.edit().putInt(KEY_LAST_RAW, raw).apply()
        if (delta <= 0) return

        pendingDelta += delta
        if (pendingDelta >= FLUSH_THRESHOLD) flush()
    }

    private fun flush() {
        val delta = pendingDelta
        if (delta <= 0) return
        pendingDelta = 0
        val today = LocalDate.now().toEpochDay()
        scope.launch {
            runCatching {
                if (checkInRepository.observeForDay(today).firstOrNull() == null) {
                    checkInRepository.save(defaultCheckIn(today))
                }
                checkInRepository.addSteps(today, delta)
            }
        }
    }

    private fun defaultCheckIn(day: Long) = CheckInEntity(
        epochDay = day,
        trained = false,
        energyLevel = 3,
        mood = 3,
        sleepQuality = 3,
        hitCalories = false,
        hitProtein = false,
        steps = 0,
        createdAtMillis = System.currentTimeMillis()
    )

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val KEY_LAST_RAW = "last_raw"
        const val FLUSH_THRESHOLD = 20
    }
}
