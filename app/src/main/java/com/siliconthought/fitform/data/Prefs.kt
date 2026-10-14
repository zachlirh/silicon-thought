package com.siliconthought.fitform.data

import android.content.Context
import com.siliconthought.fitform.core.UnitSystem

/** Small preference holder for the app-wide unit system. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("fitform_prefs", Context.MODE_PRIVATE)

    var units: UnitSystem
        get() = if (sp.getString(KEY_UNITS, UnitSystem.METRIC.name) == UnitSystem.US.name) {
            UnitSystem.US
        } else {
            UnitSystem.METRIC
        }
        set(value) {
            sp.edit().putString(KEY_UNITS, value.name).apply()
        }

    private companion object {
        const val KEY_UNITS = "units"
    }
}
