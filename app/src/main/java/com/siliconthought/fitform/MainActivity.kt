package com.siliconthought.fitform

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.siliconthought.fitform.databinding.ActivityMainBinding

/**
 * Home screen: the hub linking the squat coach and the fitness tools.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener { open(WorkoutActivity::class.java) }
        binding.btnCalories.setOnClickListener { open(NutritionActivity::class.java) }
        binding.btnBodyFat.setOnClickListener { open(BodyFatActivity::class.java) }
        binding.btnRoutines.setOnClickListener { open(RoutinesActivity::class.java) }
        binding.btnStats.setOnClickListener { open(StatsActivity::class.java) }
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }
}
