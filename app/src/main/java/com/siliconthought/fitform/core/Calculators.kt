package com.siliconthought.fitform.core

import kotlin.math.log10
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Calorie calculator (inspired by calculator.net/calorie-calculator.html)
// ---------------------------------------------------------------------------

data class CalorieRow(val label: String, val calories: Int, val deltaKcal: Int)

data class CalorieResult(val bmr: Int, val maintenance: Int, val rows: List<CalorieRow>)

object CalorieCalculator {

    /** 1 step == 0.25 kg (~0.5 lb) per week == 250 kcal/day. */
    private const val KCAL_PER_STEP = 250

    /** Mifflin-St Jeor basal metabolic rate. */
    fun bmr(gender: Gender, weightKg: Double, heightCm: Double, age: Int): Double =
        if (gender == Gender.MALE) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }

    fun calculate(
        gender: Gender,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        activity: ActivityLevel
    ): CalorieResult {
        val bmr = bmr(gender, weightKg, heightCm, age)
        val maintenance = (bmr * activity.factor).roundToInt()
        val rows = listOf(
            CalorieRow("Maintain weight", maintenance, 0),
            CalorieRow("Mild weight loss", maintenance - KCAL_PER_STEP, -KCAL_PER_STEP),
            CalorieRow("Weight loss", maintenance - 2 * KCAL_PER_STEP, -2 * KCAL_PER_STEP),
            CalorieRow("Extreme weight loss", maintenance - 4 * KCAL_PER_STEP, -4 * KCAL_PER_STEP),
            CalorieRow("Mild weight gain", maintenance + KCAL_PER_STEP, KCAL_PER_STEP),
            CalorieRow("Weight gain", maintenance + 2 * KCAL_PER_STEP, 2 * KCAL_PER_STEP),
            CalorieRow("Extreme weight gain", maintenance + 4 * KCAL_PER_STEP, 4 * KCAL_PER_STEP)
        )
        return CalorieResult(bmr.roundToInt(), maintenance, rows)
    }
}

// ---------------------------------------------------------------------------
// Body fat calculator (inspired by calculator.net/body-fat-calculator.html)
// ---------------------------------------------------------------------------

enum class BodyFatMethod(val label: String) {
    NAVY("U.S. Navy Method"),
    BMI("BMI Method")
}

data class BodyFatResult(
    val bodyFatPercent: Double,
    val fatMassKg: Double,
    val leanMassKg: Double,
    val bmi: Double
)

object BodyFatCalculator {

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val meters = heightCm / 100.0
        return if (meters <= 0.0) 0.0 else weightKg / (meters * meters)
    }

    /** U.S. Navy circumference method. All inputs in centimetres. */
    fun navyPercent(
        gender: Gender,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double?
    ): Double = if (gender == Gender.MALE) {
        495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
    } else {
        val hip = hipCm ?: waistCm
        495.0 / (1.29579 - 0.35004 * log10(waistCm + hip - neckCm) + 0.22100 * log10(heightCm)) - 450.0
    }

    /** BMI-based (Deurenberg) estimate. */
    fun bmiPercent(gender: Gender, bmi: Double, age: Int): Double {
        val sex = if (gender == Gender.MALE) 1.0 else 0.0
        return 1.20 * bmi + 0.23 * age - 10.8 * sex - 5.4
    }

    fun result(percent: Double, weightKg: Double, heightCm: Double): BodyFatResult {
        val clamped = percent.coerceIn(2.0, 70.0)
        val fatMass = weightKg * clamped / 100.0
        return BodyFatResult(
            bodyFatPercent = clamped,
            fatMassKg = fatMass,
            leanMassKg = weightKg - fatMass,
            bmi = bmi(weightKg, heightCm)
        )
    }
}
