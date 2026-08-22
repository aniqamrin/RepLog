package com.replog.app.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.local.FoodEntryEntity
import com.replog.app.data.repository.NutritionRepository
import com.replog.app.data.repository.ProfileRepository
import com.replog.app.domain.model.Goals
import com.replog.app.domain.model.MacroTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MealSection(
    val label: String,
    val entries: List<FoodEntryEntity>,
    val calories: Int,
    val protein: Double
)

data class NutritionUiState(
    val loading: Boolean = true,
    val goals: Goals = Goals(),
    val totals: MacroTotals = MacroTotals(),
    val waterMl: Int = 0,
    val meals: List<MealSection> = emptyList()
)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    profileRepository: ProfileRepository,
    private val activityRepository: com.replog.app.data.repository.ActivityRepository
) : ViewModel() {

    private val today: Long = LocalDate.now().toEpochDay()

    val uiState: StateFlow<NutritionUiState> = combine(
        profileRepository.observeGoals(),
        nutritionRepository.observeTotals(today),
        nutritionRepository.observeDay(today),
        activityRepository.observeDay(today)
    ) { goals, totals, entries, events ->
        val water = events.filter { it.type == com.replog.app.domain.model.ActivityType.WATER }
            .sumOf { it.value ?: 0.0 }.toInt()
            .coerceAtLeast(0)
        NutritionUiState(
            loading = false,
            goals = goals,
            totals = totals,
            waterMl = water,
            meals = mealSections(entries)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState())

    fun addFood(
        name: String, mealType: String, servingSize: String?, quantity: Double,
        calories: Int, protein: Double, carbs: Double, fat: Double, fiber: Double
    ) {
        viewModelScope.launch {
            nutritionRepository.addFood(
                epochDay = today, name = name, mealType = mealType,
                servingSize = servingSize, quantity = quantity,
                calories = calories, proteinG = protein, carbsG = carbs,
                fatG = fat, fiberG = fiber,
                timeMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            )
        }
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch { nutritionRepository.delete(id) }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { nutritionRepository.addWater(today, ml) }
    }

    private fun mealSections(entries: List<FoodEntryEntity>): List<MealSection> {
        return listOf("BREAKFAST" to "Breakfast", "LUNCH" to "Lunch", "DINNER" to "Dinner", "SNACK" to "Snack")
            .map { (key, label) ->
                val list = entries.filter { it.mealType == key }.sortedBy { it.timeMinutes }
                MealSection(
                    label = label,
                    entries = list,
                    calories = list.sumOf { it.calories },
                    protein = list.sumOf { it.proteinG }
                )
            }
    }
}
