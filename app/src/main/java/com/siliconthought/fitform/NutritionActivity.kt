package com.siliconthought.fitform

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.ActivityLevel
import com.siliconthought.fitform.core.CalorieCalculator
import com.siliconthought.fitform.core.Gender
import com.siliconthought.fitform.core.UnitSystem
import com.siliconthought.fitform.core.Units
import com.siliconthought.fitform.data.Prefs
import com.siliconthought.fitform.databinding.ActivityNutritionBinding

/**
 * Calorie calculator: BMR + daily targets to maintain, lose or gain weight.
 * Calculations follow Mifflin-St Jeor (BMR) and calculator.net's activity
 * multipliers and weight-change steps (250 kcal per 0.25 kg/week).
 */
class NutritionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNutritionBinding
    private lateinit var prefs: Prefs

    private var units = UnitSystem.METRIC
    private var activityIndex = 2 // "Moderate" default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNutritionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        units = prefs.units

        binding.toggleUnits.check(if (units == UnitSystem.METRIC) R.id.btnMetric else R.id.btnUs)
        binding.toggleUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            units = if (checkedId == R.id.btnUs) UnitSystem.US else UnitSystem.METRIC
            prefs.units = units
            applyUnits()
        }

        binding.toggleGender.check(R.id.btnMale)

        val labels = ActivityLevel.values().map { it.label }
        binding.acActivity.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        binding.acActivity.setText(labels[activityIndex], false)
        binding.acActivity.setOnItemClickListener { _, _, position, _ -> activityIndex = position }

        binding.btnCalculate.setOnClickListener { calculate() }

        applyUnits()
    }

    private fun applyUnits() {
        val metric = units == UnitSystem.METRIC
        binding.etHeightCm.visibility = if (metric) View.VISIBLE else View.GONE
        binding.llHeightImperial.visibility = if (metric) View.GONE else View.VISIBLE
        binding.etWeight.hint = "Weight (${Units.weightLabel(units)})"
    }

    private fun calculate() {
        hideError()

        val age = binding.etAge.text.toString().trim().toIntOrNull()
        if (age == null || age !in 1..119) return showError("Enter a valid age (1-119).")

        val heightCm = readHeightCm() ?: return showError("Enter a valid height.")
        if (heightCm < 50.0 || heightCm > 260.0) return showError("Enter a valid height.")

        val weightValue = binding.etWeight.text.toString().trim().toDoubleOrNull()
            ?: return showError("Enter a valid weight.")
        val weightKg = Units.toKg(weightValue, units)
        if (weightKg < 20.0 || weightKg > 500.0) return showError("Enter a valid weight.")

        val gender = if (binding.toggleGender.checkedButtonId == R.id.btnFemale) {
            Gender.FEMALE
        } else {
            Gender.MALE
        }
        val activity = ActivityLevel.values()[activityIndex]

        val result = CalorieCalculator.calculate(gender, weightKg, heightCm, age, activity)

        binding.cardResults.visibility = View.VISIBLE
        binding.tvBmr.text = "Basal Metabolic Rate (BMR): ${result.bmr} kcal/day"
        binding.tvMaintenance.text = "Maintain weight: ${result.maintenance} kcal/day"

        binding.llResults.removeAllViews()
        for (row in result.rows) {
            val view = layoutInflater.inflate(R.layout.item_calorie_row, binding.llResults, false)
            view.findViewById<TextView>(R.id.tvLabel).text = row.label
            view.findViewById<TextView>(R.id.tvValue).text = "${row.calories} kcal"
            view.findViewById<TextView>(R.id.tvDelta).text = when {
                row.deltaKcal > 0 -> "+${row.deltaKcal}"
                row.deltaKcal < 0 -> row.deltaKcal.toString()
                else -> ""
            }
            binding.llResults.addView(view)
        }
    }

    private fun readHeightCm(): Double? {
        return if (units == UnitSystem.METRIC) {
            binding.etHeightCm.text.toString().trim().toDoubleOrNull()
        } else {
            val feet = binding.etHeightFt.text.toString().trim().toIntOrNull() ?: return null
            val inches = binding.etHeightIn.text.toString().trim().toDoubleOrNull() ?: 0.0
            Units.ftInToCm(feet, inches)
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }
}
