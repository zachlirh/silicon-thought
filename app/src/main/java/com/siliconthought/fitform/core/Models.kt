package com.siliconthought.fitform.core

/**
 * Pure domain layer: no Android dependencies, so it is unit-testable
 * and reusable if these features are ever split into their own module/app.
 */

enum class UnitSystem { METRIC, US }

object Units {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_IN = 2.54

    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun kgToLb(kg: Double) = kg / KG_PER_LB
    fun inToCm(inches: Double) = inches * CM_PER_IN
    fun cmToIn(cm: Double) = cm / CM_PER_IN
    fun ftInToCm(feet: Int, inches: Double) = inToCm(feet * 12.0 + inches)

    fun weightLabel(u: UnitSystem) = if (u == UnitSystem.METRIC) "kg" else "lb"
    fun girthLabel(u: UnitSystem) = if (u == UnitSystem.METRIC) "cm" else "in"
    fun heightLabel(u: UnitSystem) = if (u == UnitSystem.METRIC) "cm" else "ft / in"

    fun toKg(value: Double, u: UnitSystem) = if (u == UnitSystem.METRIC) value else lbToKg(value)
    fun toCm(value: Double, u: UnitSystem) = if (u == UnitSystem.METRIC) value else inToCm(value)
    fun fromKg(kg: Double, u: UnitSystem) = if (u == UnitSystem.METRIC) kg else kgToLb(kg)
    fun fromCm(cm: Double, u: UnitSystem) = if (u == UnitSystem.METRIC) cm else cmToIn(cm)
}

enum class Gender(val label: String) { MALE("Male"), FEMALE("Female") }

/** Activity multipliers follow calculator.net's calorie calculator. */
enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTARY("Sedentary: little or no exercise", 1.2),
    LIGHT("Light: exercise 1-3 times/week", 1.375),
    MODERATE("Moderate: exercise 4-5 times/week", 1.465),
    ACTIVE("Active: daily exercise or intense exercise 3-4 times/week", 1.55),
    VERY_ACTIVE("Very Active: intense exercise 6-7 times/week", 1.725),
    EXTRA_ACTIVE("Extra Active: very intense exercise daily, or physical job", 1.9)
}

/** The six muscle groups used by the training-distribution radar chart. */
enum class MuscleGroup(val label: String) {
    BACK("Back"),
    CHEST("Chest"),
    CORE("Core"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs");

    companion object {
        fun fromName(name: String?): MuscleGroup? = values().firstOrNull { it.name == name }
    }
}

// ---------------------------------------------------------------------------
// Workout logger models
// ---------------------------------------------------------------------------

data class RoutineExercise(
    val name: String,
    val setCount: Int,
    /** An exercise can belong to more than one group. */
    val muscleGroups: List<MuscleGroup>
)

data class Routine(
    val id: String,
    val name: String,
    val exercises: List<RoutineExercise>
) {
    val totalSets: Int get() = exercises.sumOf { it.setCount }
}

data class LoggedSet(
    val exerciseName: String,
    val muscleGroups: List<MuscleGroup>,
    /** Canonical unit is kilograms; display converts to the user's unit system. */
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean
) {
    /** Only completed sets contribute to training volume. */
    val volumeKg: Double get() = if (completed) weightKg * reps else 0.0
}

data class WorkoutSession(
    val id: String,
    val routineName: String,
    val startMillis: Long,
    val endMillis: Long,
    val sets: List<LoggedSet>
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
    val totalVolumeKg: Double get() = sets.sumOf { it.volumeKg }
    val completedSetCount: Int get() = sets.count { it.completed }
}
