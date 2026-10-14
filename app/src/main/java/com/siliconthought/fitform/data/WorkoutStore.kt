package com.siliconthought.fitform.data

import android.content.Context
import com.siliconthought.fitform.core.LoggedSet
import com.siliconthought.fitform.core.MuscleGroup
import com.siliconthought.fitform.core.Routine
import com.siliconthought.fitform.core.RoutineExercise
import com.siliconthought.fitform.core.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight JSON-file persistence for routines and logged workout sessions.
 *
 * Deliberately dependency-free (uses org.json, which ships with Android) so the
 * project needs no annotation processor. All data is stored in the app's private
 * files directory. At this data scale, reading everything into memory is fine.
 */
class WorkoutStore(context: Context) {

    private val routinesFile = File(context.filesDir, "routines.json")
    private val sessionsFile = File(context.filesDir, "sessions.json")

    // -----------------------------------------------------------------------
    // Routines
    // -----------------------------------------------------------------------

    fun routines(): List<Routine> =
        readObjects(routinesFile).mapNotNull { it.toRoutineOrNull() }
            .sortedBy { it.name.lowercase() }

    fun routine(id: String): Routine? = routines().firstOrNull { it.id == id }

    fun upsertRoutine(routine: Routine) {
        val list = routines().toMutableList()
        val index = list.indexOfFirst { it.id == routine.id }
        if (index >= 0) list[index] = routine else list.add(routine)
        writeObjects(routinesFile, list.map { it.toJson() })
    }

    fun deleteRoutine(id: String) {
        writeObjects(routinesFile, routines().filterNot { it.id == id }.map { it.toJson() })
    }

    // -----------------------------------------------------------------------
    // Sessions
    // -----------------------------------------------------------------------

    fun sessions(): List<WorkoutSession> =
        readObjects(sessionsFile).mapNotNull { it.toSessionOrNull() }
            .sortedBy { it.startMillis }

    fun addSession(session: WorkoutSession) {
        val list = sessions().toMutableList()
        list.add(session)
        writeObjects(sessionsFile, list.map { it.toJson() })
    }

    fun deleteSession(id: String) {
        writeObjects(sessionsFile, sessions().filterNot { it.id == id }.map { it.toJson() })
    }

    // -----------------------------------------------------------------------
    // File helpers
    // -----------------------------------------------------------------------

    private fun readObjects(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeObjects(file: File, items: List<JSONObject>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        file.writeText(array.toString())
    }
}

// ---------------------------------------------------------------------------
// JSON mapping
// ---------------------------------------------------------------------------

private fun Routine.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("exercises", JSONArray().apply {
        exercises.forEach { exercise ->
            put(JSONObject().apply {
                put("name", exercise.name)
                put("setCount", exercise.setCount)
                put("muscles", JSONArray().apply { exercise.muscleGroups.forEach { put(it.name) } })
            })
        }
    })
}

private fun JSONObject.toRoutineOrNull(): Routine? {
    val id = optString("id")
    val name = optString("name")
    if (id.isBlank() || name.isBlank()) return null
    val exercises = optJSONArray("exercises")?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            RoutineExercise(
                name = obj.optString("name"),
                setCount = obj.optInt("setCount", 3),
                muscleGroups = obj.optJSONArray("muscles").toMuscleList()
            )
        }
    } ?: emptyList()
    return Routine(id, name, exercises)
}

private fun WorkoutSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("routineName", routineName)
    put("start", startMillis)
    put("end", endMillis)
    put("sets", JSONArray().apply {
        sets.forEach { set ->
            put(JSONObject().apply {
                put("exerciseName", set.exerciseName)
                put("muscles", JSONArray().apply { set.muscleGroups.forEach { put(it.name) } })
                put("weightKg", set.weightKg)
                put("reps", set.reps)
                put("completed", set.completed)
            })
        }
    })
}

private fun JSONObject.toSessionOrNull(): WorkoutSession? {
    val id = optString("id")
    if (id.isBlank()) return null
    val sets = optJSONArray("sets")?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            LoggedSet(
                exerciseName = obj.optString("exerciseName"),
                muscleGroups = obj.optJSONArray("muscles").toMuscleList(),
                weightKg = obj.optDouble("weightKg", 0.0),
                reps = obj.optInt("reps", 0),
                completed = obj.optBoolean("completed", false)
            )
        }
    } ?: emptyList()
    return WorkoutSession(
        id = id,
        routineName = optString("routineName"),
        startMillis = optLong("start", 0L),
        endMillis = optLong("end", 0L),
        sets = sets
    )
}

private fun JSONArray?.toMuscleList(): List<MuscleGroup> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { MuscleGroup.fromName(optString(it)) }
}
