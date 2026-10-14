package com.siliconthought.fitform

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.core.MuscleGroup
import com.siliconthought.fitform.core.WorkoutMath
import com.siliconthought.fitform.data.Prefs
import com.siliconthought.fitform.data.WorkoutStore
import com.siliconthought.fitform.databinding.ActivityStatsBinding
import java.util.Calendar

/**
 * Calendar + monthly statistics + training-distribution radar chart.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var store: WorkoutStore
    private lateinit var prefs: Prefs

    private var year = 0
    private var month = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = WorkoutStore(this)
        prefs = Prefs(this)

        val now = Calendar.getInstance()
        year = now.get(Calendar.YEAR)
        month = now.get(Calendar.MONTH)

        binding.btnPrevMonth.setOnClickListener {
            month--
            if (month < 0) {
                month = 11
                year--
            }
            refresh()
        }
        binding.btnNextMonth.setOnClickListener {
            month++
            if (month > 11) {
                month = 0
                year++
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sessions = store.sessions()

        binding.tvMonth.text = WorkoutMath.monthLabel(year, month)
        binding.calendar.setMonth(year, month, WorkoutMath.workoutDaysInMonth(sessions, year, month))

        val stats = WorkoutMath.monthlyStats(sessions, year, month)
        binding.tvDays.text = stats.workoutDays.toString()
        binding.tvTime.text = WorkoutMath.formatDuration(stats.totalMillis)
        binding.tvVolume.text = WorkoutMath.formatVolume(stats.totalVolumeKg, prefs.units)

        val volumes = WorkoutMath.muscleVolumesForMonth(sessions, year, month)
        val groups = MuscleGroup.values()
        binding.radar.setData(
            FloatArray(groups.size) { (volumes[groups[it]] ?: 0.0).toFloat() },
            groups.map { it.label }
        )

        binding.llLegend.removeAllViews()
        for (group in groups) {
            val row = layoutInflater.inflate(R.layout.item_calorie_row, binding.llLegend, false)
            row.findViewById<TextView>(R.id.tvLabel).text = group.label
            row.findViewById<TextView>(R.id.tvDelta).text = ""
            row.findViewById<TextView>(R.id.tvValue).text =
                WorkoutMath.formatVolume(volumes[group] ?: 0.0, prefs.units)
            binding.llLegend.addView(row)
        }
    }
}
