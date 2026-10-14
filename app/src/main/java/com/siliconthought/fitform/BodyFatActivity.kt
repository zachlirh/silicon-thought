package com.siliconthought.fitform

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.BodyFatCalculator
import com.siliconthought.fitform.core.BodyFatMethod
import com.siliconthought.fitform.core.Gender
import com.siliconthought.fitform.core.UnitSystem
import com.siliconthought.fitform.core.Units
import com.siliconthought.fitform.data.Prefs
import com.siliconthought.fitform.databinding.ActivityBodyFatBinding
import java.util.Locale

/**
 * Body fat calculator supporting the U.S. Navy circumference method and the
 * BMI-based (Deurenberg) estimate.
 */
class BodyFatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBodyFatBinding
    private lateinit var prefs: Prefs

    private var units = UnitSystem.METRIC
    private var method = BodyFatMethod.NAVY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBodyFatBinding.inflate(layoutInflater)
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

        binding.toggleMethod.check(R.id.btnNavy)
        binding.toggleMethod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            method = if (checkedId == R.id.btnBmi) BodyFatMethod.BMI else BodyFatMethod.NAVY
            applyMethodVisibility()
        }

        binding.toggleGender.check(R.id.btnMale)
        binding.toggleGender.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) applyMethodVisibility()
        }

        binding.btnCalculate.setOnClickListener { calculate() }

        applyUnits()
        applyMethodVisibility()
    }

    private fun applyUnits() {
        val metric = units == UnitSystem.METRIC
        binding.etHeightCm.visibility = if (metric) View.VISIBLE else View.GONE
        binding.llHeightImperial.visibility = if (metric) View.GONE else View.VISIBLE

        val girth = Units.girthLabel(units)
        binding.etWeight.hint = "Weight (${Units.weightLabel(units)})"
        binding.etNeck.hint = "Neck ($girth)"
        binding.etWaist.hint = "Waist ($girth)"
        binding.etHip.hint = "Hip ($girth)"
    }

    private fun applyMethodVisibility() {
        val navy = method == BodyFatMethod.NAVY
        binding.llNavy.visibility = if (navy) View.VISIBLE else View.GONE
        // Hip measurement is only required for women using the Navy method.
        binding.etHip.visibility = if (navy && isFemale()) View.VISIBLE else View.GONE
    }

    private fun isFemale() = binding.toggleGender.checkedButtonId == R.id.btnFemale

    private fun calculate() {
        hideError()

        val age = binding.etAge.text.toString().trim().toIntOrNull()
        if (age == null || age !in 1..119) return showError("Enter a valid age (1-119).")

        val heightCm = readHeightCm() ?: return showError("Enter a valid height.")
        if (heightCm <= 0.0) return showError("Enter a valid height.")

        val weightValue = binding.etWeight.text.toString().trim().toDoubleOrNull()
            ?: return showError("Enter a valid weight.")
        val weightKg = Units.toKg(weightValue, units)
        if (weightKg <= 0.0) return showError("Enter a valid weight.")

        val gender = if (isFemale()) Gender.FEMALE else Gender.MALE

        val percent = if (method == BodyFatMethod.NAVY) {
            val neckCm = readCm(binding.etNeck.text.toString())
                ?: return showError("Enter a valid neck measurement.")
            val waistCm = readCm(binding.etWaist.text.toString())
                ?: return showError("Enter a valid waist measurement.")
            val hipCm = if (gender == Gender.FEMALE) {
                readCm(binding.etHip.text.toString())
                    ?: return showError("Enter a valid hip measurement.")
            } else {
                null
            }
            if (waistCm <= neckCm) return showError("Waist must be larger than neck.")
            if (gender == Gender.FEMALE && waistCm + hipCm!! <= neckCm) {
                return showError("Check your circumference measurements.")
            }
            BodyFatCalculator.navyPercent(gender, heightCm, neckCm, waistCm, hipCm)
        } else {
            val bmi = BodyFatCalculator.bmi(weightKg, heightCm)
            BodyFatCalculator.bmiPercent(gender, bmi, age)
        }

        if (percent.isNaN() || percent.isInfinite()) {
            return showError("Could not compute - please check your measurements.")
        }

        val result = BodyFatCalculator.result(percent, weightKg, heightCm)

        binding.cardResults.visibility = View.VISIBLE
        binding.tvMethod.text = "Method: ${method.label}"
        binding.tvBodyFatPercent.text =
            String.format(Locale.US, "%.1f%%", result.bodyFatPercent)
        binding.tvFatMass.text =
            String.format(Locale.US, "Body fat mass: %.1f %s", Units.fromKg(result.fatMassKg, units), Units.weightLabel(units))
        binding.tvLeanMass.text =
            String.format(Locale.US, "Lean body mass: %.1f %s", Units.fromKg(result.leanMassKg, units), Units.weightLabel(units))
        binding.tvBmi.text = String.format(Locale.US, "BMI: %.1f", result.bmi)
    }

    private fun readCm(text: String): Double? {
        val value = text.trim().toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return Units.toCm(value, units)
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
