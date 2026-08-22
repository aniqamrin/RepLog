package com.replog.app.domain.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VisionFoodItem(
    val name: String = "",
    val portion: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val confidence: String = "medium"
)

@Serializable
data class VisionResponse(
    val foods: List<VisionFoodItem> = emptyList(),
    val total: VisionTotals? = null
)

@Serializable
data class VisionTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
)

data class DetectedFood(
    val name: String,
    val portion: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val confidence: String
) {
    companion object {
        val VALID_CONFIDENCE = setOf("low", "medium", "high")
        fun from(item: VisionFoodItem): DetectedFood? {
            val name = item.name.trim()
            if (name.isEmpty()) return null
            return DetectedFood(
                name = name.take(80),
                portion = item.portion.trim().take(40),
                calories = item.calories.coerceIn(0.0, 5000.0).toInt(),
                protein = item.protein.coerceIn(0.0, 500.0),
                carbs = item.carbs.coerceIn(0.0, 800.0),
                fat = item.fat.coerceIn(0.0, 400.0),
                fiber = item.fiber.coerceIn(0.0, 100.0),
                confidence = if (item.confidence.lowercase() in VALID_CONFIDENCE) {
                    item.confidence.lowercase()
                } else "medium"
            )
        }
    }
}

object AiFoodParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): Result<List<DetectedFood>> {
        return try {
            val cleaned = extractJson(raw)
            val response = json.decodeFromString(VisionResponse.serializer(), cleaned)
            val foods = response.foods.mapNotNull { DetectedFood.from(it) }
            if (foods.isEmpty()) {
                Result.failure(IllegalArgumentException("No valid food items in AI response"))
            } else {
                Result.success(foods)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }
}
