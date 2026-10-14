package com.siliconthought.fitform

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.MuscleGroup
import com.siliconthought.fitform.core.Routine
import com.siliconthought.fitform.core.RoutineExercise
import com.siliconthought.fitform.data.WorkoutStore
import com.siliconthought.fitform.databinding.ActivityRoutineEditBinding
import java.util.UUID

/** Create or edit a routine: custom name, exercises, set counts and groups. */
class RoutineEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"
    }

    private lateinit var binding: ActivityRoutineEditBinding
    private lateinit var store: WorkoutStore

    private val exercises = mutableListOf<RoutineExercise>()
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutineEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = WorkoutStore(this)
        editingId = intent.getStringExtra(EXTRA_ROUTINE_ID)

        if (editingId != null) {
            binding.tvTitle.text = "Edit Routine"
            store.routine(editingId!!)?.let { existing ->
                binding.etRoutineName.setText(existing.name)
                exercises.addAll(existing.exercises)
            }
        }

        binding.btnAddExercise.setOnClickListener { showAddExerciseDialog() }
        binding.btnSave.setOnClickListener { save() }
        render()
    }

    private fun render() {
        binding.llExercises.removeAllViews()
        binding.tvNoExercises.visibility = if (exercises.isEmpty()) View.VISIBLE else View.GONE

        exercises.forEachIndexed { index, exercise ->
            val view = layoutInflater.inflate(R.layout.item_routine_exercise, binding.llExercises, false)
            view.findViewById<TextView>(R.id.tvName).text = exercise.name
            val groups = if (exercise.muscleGroups.isEmpty()) {
                "no muscle group"
            } else {
                exercise.muscleGroups.joinToString(", ") { it.label }
            }
            view.findViewById<TextView>(R.id.tvMeta).text = "${exercise.setCount} sets · $groups"
            view.findViewById<View>(R.id.btnRemove).setOnClickListener {
                exercises.removeAt(index)
                render()
            }
            binding.llExercises.addView(view)
        }
    }

    private fun showAddExerciseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_exercise, null)
        val etName = dialogView.findViewById<EditText>(R.id.etExerciseName)
        val etSets = dialogView.findViewById<EditText>(R.id.etSets)
        etSets.setText("3")

        val boxes = mapOf(
            MuscleGroup.BACK to dialogView.findViewById<CheckBox>(R.id.cbBack),
            MuscleGroup.CHEST to dialogView.findViewById<CheckBox>(R.id.cbChest),
            MuscleGroup.CORE to dialogView.findViewById<CheckBox>(R.id.cbCore),
            MuscleGroup.SHOULDERS to dialogView.findViewById<CheckBox>(R.id.cbShoulders),
            MuscleGroup.ARMS to dialogView.findViewById<CheckBox>(R.id.cbArms),
            MuscleGroup.LEGS to dialogView.findViewById<CheckBox>(R.id.cbLegs)
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add exercise")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val sets = etSets.text.toString().trim().toIntOrNull()
                val groups = boxes.filter { it.value.isChecked }.keys.toList()

                when {
                    name.isBlank() -> toast("Enter an exercise name.")
                    sets == null || sets !in 1..20 -> toast("Sets must be between 1 and 20.")
                    groups.isEmpty() -> toast("Pick at least one muscle group.")
                    else -> {
                        exercises.add(RoutineExercise(name, sets, groups))
                        render()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun save() {
        val name = binding.etRoutineName.text.toString().trim()
        when {
            name.isBlank() -> return showError("Enter a routine name.")
            exercises.isEmpty() -> return showError("Add at least one exercise.")
        }
        val id = editingId ?: UUID.randomUUID().toString()
        store.upsertRoutine(Routine(id, name, exercises.toList()))
        finish()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
