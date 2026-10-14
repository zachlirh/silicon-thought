package com.siliconthought.fitform

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.LoggedSet
import com.siliconthought.fitform.core.MuscleGroup
import com.siliconthought.fitform.core.Routine
import com.siliconthought.fitform.core.Units
import com.siliconthought.fitform.core.WorkoutMath
import com.siliconthought.fitform.core.WorkoutSession
import com.siliconthought.fitform.data.Prefs
import com.siliconthought.fitform.data.WorkoutStore
import com.siliconthought.fitform.databinding.ActivityWorkoutLogBinding
import java.util.Locale
import java.util.UUID

/**
 * Runs a workout against a routine: a timer starts on Start and stops on
 * Finish, and the summary reports duration plus total training volume
 * (sum of weight x reps across completed sets).
 */
class WorkoutLogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"
    }

    private class SetRow(
        val exerciseName: String,
        val muscleGroups: List<MuscleGroup>,
        val weight: EditText,
        val reps: EditText,
        val done: CheckBox
    )

    private lateinit var binding: ActivityWorkoutLogBinding
    private lateinit var store: WorkoutStore
    private lateinit var prefs: Prefs

    private var routine: Routine? = null
    private val rows = mutableListOf<SetRow>()

    private var startMillis = 0L
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            if (running) handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = WorkoutStore(this)
        prefs = Prefs(this)

        val id = intent.getStringExtra(EXTRA_ROUTINE_ID)
        routine = if (id != null) store.routine(id) else null

        val current = routine
        if (current == null) {
            Toast.makeText(this, "Routine not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvRoutineName.text = current.name
        binding.btnFinish.isEnabled = false
        buildRows(current)

        binding.btnStart.setOnClickListener { startWorkout() }
        binding.btnFinish.setOnClickListener { finishWorkout() }

        updateTimer()
    }

    private fun buildRows(routine: Routine) {
        val weightUnit = Units.weightLabel(prefs.units)
        binding.llExercises.removeAllViews()
        rows.clear()

        for (exercise in routine.exercises) {
            val item = layoutInflater.inflate(R.layout.item_log_exercise, binding.llExercises, false)
            item.findViewById<TextView>(R.id.tvExerciseName).text = exercise.name
            val groups = exercise.muscleGroups.joinToString(", ") { it.label }
            item.findViewById<TextView>(R.id.tvExerciseMeta).text =
                if (groups.isEmpty()) "${exercise.setCount} sets" else "${exercise.setCount} sets · $groups"

            val setsContainer = item.findViewById<LinearLayout>(R.id.llSets)
            for (setNumber in 1..exercise.setCount) {
                val setView = layoutInflater.inflate(R.layout.item_log_set, setsContainer, false)
                setView.findViewById<TextView>(R.id.tvSetLabel).text = "Set $setNumber"

                val weight = setView.findViewById<EditText>(R.id.etWeight)
                val reps = setView.findViewById<EditText>(R.id.etReps)
                val done = setView.findViewById<CheckBox>(R.id.cbDone)
                weight.hint = weightUnit

                // Ticking is automatic once reps are entered, but can be undone.
                reps.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val value = s?.toString()?.trim()?.toIntOrNull() ?: 0
                        if (value > 0) done.isChecked = true
                    }
                })

                setsContainer.addView(setView)
                rows.add(SetRow(exercise.name, exercise.muscleGroups, weight, reps, done))
            }
            binding.llExercises.addView(item)
        }
    }

    private fun startWorkout() {
        if (running) return
        startMillis = System.currentTimeMillis()
        running = true
        binding.btnStart.isEnabled = false
        binding.btnFinish.isEnabled = true
        binding.tvStatus.text = "Workout in progress - tap Finish when done."
        handler.post(ticker)
    }

    private fun updateTimer() {
        val elapsed = if (running) System.currentTimeMillis() - startMillis else 0L
        val seconds = elapsed / 1000
        binding.tvTimer.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun finishWorkout() {
        if (!running) {
            Toast.makeText(this, "Tap Start first.", Toast.LENGTH_SHORT).show()
            return
        }

        val endMillis = System.currentTimeMillis()
        running = false
        handler.removeCallbacks(ticker)

        val sets = rows.mapNotNull { row ->
            val reps = row.reps.text.toString().trim().toIntOrNull() ?: 0
            val weightValue = row.weight.text.toString().trim().toDoubleOrNull() ?: 0.0
            val completed = row.done.isChecked
            if (reps <= 0 && !completed) return@mapNotNull null
            LoggedSet(
                exerciseName = row.exerciseName,
                muscleGroups = row.muscleGroups,
                weightKg = Units.toKg(weightValue, prefs.units),
                reps = reps,
                completed = completed
            )
        }

        val session = WorkoutSession(
            id = UUID.randomUUID().toString(),
            routineName = routine?.name ?: "Workout",
            startMillis = startMillis,
            endMillis = endMillis,
            sets = sets
        )
        store.addSession(session)

        AlertDialog.Builder(this)
            .setTitle("Workout complete")
            .setMessage(
                "Duration: ${WorkoutMath.formatDuration(session.durationMillis)}\n" +
                    "Sets logged: ${sets.size}\n" +
                    "Total volume: ${WorkoutMath.formatVolume(session.totalVolumeKg, prefs.units)}"
            )
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
    }
}
