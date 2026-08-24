package com.replog.app.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replog.app.data.repository.AiRepository
import com.replog.app.data.repository.NutritionRepository
import com.replog.app.domain.logic.AiFoodParser
import com.replog.app.domain.logic.DetectedFood
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanPhase { CAMERA, ANALYZING, REVIEW, ERROR }

data class ScannerUiState(
    val phase: ScanPhase = ScanPhase.CAMERA,
    val photoPath: String? = null,
    val foods: List<DetectedFood> = emptyList(),
    val errorMessage: String = "",
    val saving: Boolean = false
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state

    fun reset() {
        _state.value = ScannerUiState()
    }

    fun analyzePhoto(file: java.io.File) {
        _state.update { it.copy(phase = ScanPhase.ANALYZING, photoPath = file.absolutePath) }
        viewModelScope.launch {
            try {
                val base64 = withContext(Dispatchers.IO) { encodeDownscaledJpeg(file) }
                val result = aiRepository.analyzeFood(base64)
                result
                    .onSuccess { raw ->
                        val parsed = AiFoodParser.parse(raw)
                        parsed
                            .onSuccess { foods ->
                                if (foods.isEmpty()) throw IllegalStateException("No foods recognized")
                                _state.update {
                                    it.copy(phase = ScanPhase.REVIEW, foods = foods)
                                }
                            }
                            .onFailure { e -> fail(e.message ?: "Could not parse AI response") }
                    }
                    .onFailure { e -> fail(scanErrorHint(e)) }
            } catch (e: Exception) {
                fail(scanErrorHint(e))
            }
        }
    }

    private fun scanErrorHint(e: Throwable): String =
        if (e is UnsupportedOperationException || e.message?.contains("backend", ignoreCase = true) == true ||
            e.message?.contains("Failed to connect", ignoreCase = true) == true ||
            e.message?.contains("Unable to resolve", ignoreCase = true) == true
        ) {
            "Food scanning needs the REPLOG backend with an AI vision model configured. " +
                "You can still log this meal manually via Log Food."
        } else {
            e.message ?: "Analysis failed"
        }

    private fun fail(message: String) {
        _state.update { it.copy(phase = ScanPhase.ERROR, errorMessage = message) }
    }

    private fun encodeDownscaledJpeg(file: java.io.File): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sample = 1
        while (options.outWidth / sample > 1280) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalStateException("Could not read captured photo")
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun updateFood(index: Int, food: DetectedFood) {
        _state.update { s ->
            s.copy(foods = s.foods.mapIndexed { i, f -> if (i == index) food else f })
        }
    }

    fun removeFood(index: Int) {
        _state.update { s -> s.copy(foods = s.foods.filterIndexed { i, _ -> i != index }) }
    }

    fun totalCalories(): Int = _state.value.foods.sumOf { it.calories }
    fun totalProtein(): Double = _state.value.foods.sumOf { it.protein }

    fun saveAll(mealType: String, timeMinutes: Int, onDone: () -> Unit) {
        val foods = _state.value.foods
        if (foods.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                foods.forEach { f ->
                    nutritionRepository.addFood(
                        epochDay = LocalDate.now().toEpochDay(),
                        name = f.name,
                        mealType = mealType,
                        servingSize = f.portion.takeIf { it.isNotBlank() },
                        quantity = 1.0,
                        calories = f.calories,
                        proteinG = f.protein,
                        carbsG = f.carbs,
                        fatG = f.fat,
                        fiberG = f.fiber,
                        timeMinutes = timeMinutes,
                        source = "ai-scan"
                    )
                }
            }
            _state.update { it.copy(saving = false) }
            onDone()
        }
    }

    fun newCaptureFile(): java.io.File =
        java.io.File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg").also {
            it.createNewFile()
        }
}
