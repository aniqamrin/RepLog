package com.replog.app.ui.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.local.CheckInEntity
import com.replog.app.data.repository.CardioRepository
import com.replog.app.data.repository.CheckInRepository
import com.replog.app.data.repository.NutritionRepository
import com.replog.app.data.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val weightRepository: WeightRepository,
    private val cardioRepository: CardioRepository,
    private val checkInRepository: CheckInRepository
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()

    fun addFood(
        name: String, mealType: String, servingSize: String?,
        calories: Int, protein: Double, carbs: Double, fat: Double, fiber: Double
    ) {
        viewModelScope.launch {
            val now = LocalTime.now()
            nutritionRepository.addFood(
                epochDay = today, name = name.trim(), mealType = mealType,
                servingSize = servingSize?.trim()?.takeIf { it.isNotEmpty() },
                quantity = 1.0,
                calories = calories,
                proteinG = protein, carbsG = carbs, fatG = fat, fiberG = fiber,
                timeMinutes = now.hour * 60 + now.minute
            )
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { nutritionRepository.addWater(today, ml) }
    }

    fun addWeight(weightKg: Double, bodyFatPct: Double?) {
        viewModelScope.launch { weightRepository.upsert(today, weightKg, bodyFatPct) }
    }

    fun addCardio(type: String, distanceKm: Double, durationMin: Double, calories: Int) {
        viewModelScope.launch {
            cardioRepository.add(today, type, distanceKm, durationMin, calories)
        }
    }

    fun saveCheckIn(
        trained: Boolean, energyLevel: Int, mood: Int, sleepQuality: Int,
        hitCalories: Boolean, hitProtein: Boolean, steps: Int, notes: String?
    ) {
        viewModelScope.launch {
            checkInRepository.save(
                CheckInEntity(
                    epochDay = today, trained = trained, energyLevel = energyLevel,
                    mood = mood, sleepQuality = sleepQuality, hitCalories = hitCalories,
                    hitProtein = hitProtein, steps = steps, notes = notes?.takeIf { it.isNotBlank() },
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun pace(distanceKm: Double, durationMin: Double): String =
        cardioRepository.pace(distanceKm, durationMin)
}
