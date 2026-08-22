package com.replog.app.domain.model

enum class ActivityType(val label: String) {
    GYM("Gym"),
    FOOD("Nutrition"),
    PROTEIN("Protein"),
    CARDIO("Cardio"),
    STEPS("Steps"),
    WATER("Water"),
    WEIGHT("Weight"),
    HABIT("Habits");

    companion object {
        fun from(raw: String?): ActivityType = entries.firstOrNull { it.name == raw } ?: GYM
    }
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun from(raw: String?): MealType = entries.firstOrNull { it.name == raw } ?: SNACK
    }
}

enum class WorkoutType(val label: String) {
    PUSH("Push"), PULL("Pull"), LEGS("Legs"), UPPER("Upper"),
    LOWER("Lower"), FULL_BODY("Full Body"), CARDIO("Cardio"), CUSTOM("Custom");

    companion object {
        fun from(raw: String?): WorkoutType = entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class CardioType(val label: String) {
    RUNNING("Running"), WALKING("Walking"), CYCLING("Cycling"), SWIMMING("Swimming"), OTHER("Other");

    companion object {
        fun from(raw: String?): CardioType = entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class GoalType(val label: String) {
    FAT_LOSS("Fat loss"), MUSCLE_GAIN("Muscle gain"), MAINTENANCE("Maintenance"),
    STRENGTH("Strength"), ENDURANCE("Endurance"), GENERAL_FITNESS("General fitness")
}

data class MacroTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0
)

data class DayTotals(
    val epochDay: Long,
    val totals: MacroTotals
)

data class Goals(
    val goalType: GoalType = GoalType.GENERAL_FITNESS,
    val calorieTarget: Int = 2500,
    val proteinTargetG: Int = 150,
    val carbsTargetG: Int = 280,
    val fatTargetG: Int = 80,
    val fiberTargetG: Int = 30,
    val waterTargetMl: Int = 3000,
    val stepTarget: Int = 10000,
    val workoutDaysPerWeek: Int = 5
)
