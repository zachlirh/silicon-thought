package com.siliconthought.fitform.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class MonthlyStats(
    val year: Int,
    /** 0-based month, matching Calendar.MONTH. */
    val month: Int,
    val workoutDays: Int,
    val totalMillis: Long,
    val totalVolumeKg: Double
)

object WorkoutMath {

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%dh %02dm %02ds", h, m, s)
        } else {
            String.format(Locale.US, "%dm %02ds", m, s)
        }
    }

    /** Formats a volume stored in kg into the user's preferred unit. */
    fun formatVolume(volumeKg: Double, units: UnitSystem): String {
        val value = Units.fromKg(volumeKg, units)
        return String.format(Locale.US, "%,.0f %s", value, Units.weightLabel(units))
    }

    fun formatWeight(weightKg: Double, units: UnitSystem): String {
        val value = Units.fromKg(weightKg, units)
        return if (value == value.toLong().toDouble()) {
            String.format(Locale.US, "%,d %s", value.toLong(), Units.weightLabel(units))
        } else {
            String.format(Locale.US, "%,.1f %s", value, Units.weightLabel(units))
        }
    }

    private fun calendar(millis: Long): Calendar =
        Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = millis }

    fun yearOf(millis: Long): Int = calendar(millis).get(Calendar.YEAR)

    /** 0-based month. */
    fun monthOf(millis: Long): Int = calendar(millis).get(Calendar.MONTH)

    fun dayOfMonth(millis: Long): Int = calendar(millis).get(Calendar.DAY_OF_MONTH)

    fun dayOfWeek(millis: Long): Int = calendar(millis).get(Calendar.DAY_OF_WEEK)

    fun daysInMonth(year: Int, month: Int): Int {
        val c = Calendar.getInstance()
        c.set(year, month, 1)
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun monthLabel(year: Int, month: Int): String {
        val c = Calendar.getInstance()
        c.set(year, month, 1)
        return String.format(Locale.US, "%tB %d", c, year)
    }

    fun monthlyStats(sessions: List<WorkoutSession>, year: Int, month: Int): MonthlyStats {
        val inMonth = sessions.filter { yearOf(it.startMillis) == year && monthOf(it.startMillis) == month }
        val days = inMonth.map { dayOfMonth(it.startMillis) }.toSet().size
        return MonthlyStats(
            year = year,
            month = month,
            workoutDays = days,
            totalMillis = inMonth.sumOf { it.durationMillis },
            totalVolumeKg = inMonth.sumOf { it.totalVolumeKg }
        )
    }

    /** Days-of-month (1..31) that contain at least one workout. */
    fun workoutDaysInMonth(sessions: List<WorkoutSession>, year: Int, month: Int): Set<Int> =
        sessions.filter { yearOf(it.startMillis) == year && monthOf(it.startMillis) == month }
            .map { dayOfMonth(it.startMillis) }
            .toSet()

    /**
     * Total volume per muscle group. A set that targets several groups credits
     * its full volume to each of them, so the chart reflects work done per area.
     */
    fun muscleVolumes(sessions: List<WorkoutSession>): Map<MuscleGroup, Double> {
        val totals = MuscleGroup.values().associateWith { 0.0 }.toMutableMap()
        for (session in sessions) {
            for (set in session.sets) {
                if (!set.completed) continue
                for (group in set.muscleGroups) {
                    totals[group] = (totals[group] ?: 0.0) + set.volumeKg
                }
            }
        }
        return totals
    }

    fun muscleVolumesForMonth(
        sessions: List<WorkoutSession>,
        year: Int,
        month: Int
    ): Map<MuscleGroup, Double> = muscleVolumes(
        sessions.filter { yearOf(it.startMillis) == year && monthOf(it.startMillis) == month }
    )
}
