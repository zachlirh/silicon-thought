package com.siliconthought.fitform

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.Routine
import com.siliconthought.fitform.data.WorkoutStore
import com.siliconthought.fitform.databinding.ActivityRoutinesBinding

/** Lists saved routines; tapping one starts a workout log. */
class RoutinesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoutinesBinding
    private lateinit var store: WorkoutStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = WorkoutStore(this)
        binding.btnCreate.setOnClickListener {
            startActivity(Intent(this, RoutineEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val routines = store.routines()
        binding.tvEmpty.visibility = if (routines.isEmpty()) View.VISIBLE else View.GONE
        binding.llRoutines.removeAllViews()

        for (routine in routines) {
            val view = layoutInflater.inflate(R.layout.item_routine, binding.llRoutines, false)
            view.findViewById<TextView>(R.id.tvName).text = routine.name
            view.findViewById<TextView>(R.id.tvMeta).text =
                "${routine.exercises.size} exercises · ${routine.totalSets} sets"
            view.findViewById<View>(R.id.rowBody).setOnClickListener { startLog(routine) }
            view.findViewById<View>(R.id.btnEdit).setOnClickListener {
                startActivity(
                    Intent(this, RoutineEditActivity::class.java)
                        .putExtra(RoutineEditActivity.EXTRA_ROUTINE_ID, routine.id)
                )
            }
            view.findViewById<View>(R.id.btnDelete).setOnClickListener { confirmDelete(routine) }
            binding.llRoutines.addView(view)
        }
    }

    private fun startLog(routine: Routine) {
        startActivity(
            Intent(this, WorkoutLogActivity::class.java)
                .putExtra(WorkoutLogActivity.EXTRA_ROUTINE_ID, routine.id)
        )
    }

    private fun confirmDelete(routine: Routine) {
        AlertDialog.Builder(this)
            .setTitle("Delete routine?")
            .setMessage("\"${routine.name}\" will be removed. Logged workouts are kept.")
            .setPositiveButton("Delete") { _, _ ->
                store.deleteRoutine(routine.id)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
