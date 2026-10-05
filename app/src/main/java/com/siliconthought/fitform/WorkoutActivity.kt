package com.siliconthought.fitform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siliconthought.fitform.analysis.SquatAnalyzer
import com.siliconthought.fitform.camera.PoseAnalyzer
import com.siliconthought.fitform.databinding.ActivityWorkoutBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Workout screen: camera preview + skeleton overlay + rep counter + voice feedback.
 *
 * Tap Finish to end the set and return to the home screen.
 */
class WorkoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutBinding
    private lateinit var cameraExecutor: ExecutorService
    private var poseAnalyzer: PoseAnalyzer? = null
    private val squatAnalyzer = SquatAnalyzer()

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else binding.tvFeedback.text = "Camera permission is required to analyze your form"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        initTts()
        binding.btnFinish.setOnClickListener { confirmFinish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------------------------------------------------------------
    // Voice feedback
    // ---------------------------------------------------------------------

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                speak("Let's go. Stand tall, then start squatting.")
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        // QUEUE_FLUSH so the newest cue (e.g. "Knees out") interrupts the last one.
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    // ---------------------------------------------------------------------
    // Camera
    // ---------------------------------------------------------------------

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            // Use the same aspect ratio/resolution for preview and analysis
            // so the skeleton aligns with the preview.
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer = PoseAnalyzer { pose, imgW, imgH ->
                runOnUiThread {
                    // The back camera is not mirrored; set mirror to true if you
                    // switch to the front camera.
                    binding.overlayView.update(pose, imgW, imgH, mirrorImage = false)

                    val result = squatAnalyzer.analyze(pose)
                    binding.tvReps.text = result.reps.toString()
                    binding.tvFeedback.text = result.feedback
                    result.speech?.let { speak(it) }
                }
            }
            poseAnalyzer = analyzer
            imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                binding.tvFeedback.text = "Failed to start camera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------------------------------------------------------------
    // Finish
    // ---------------------------------------------------------------------

    private fun confirmFinish() {
        val reps = squatAnalyzer.reps
        val repWord = if (reps == 1) "rep" else "reps"
        speak("Set complete. You did $reps $repWord. Nice work.")
        MaterialAlertDialogBuilder(this)
            .setTitle("Set complete")
            .setMessage("Nice work! You did $reps $repWord.")
            .setPositiveButton("Back to Home") { _, _ -> finish() }
            .setNegativeButton("Keep going", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        poseAnalyzer?.close()
        cameraExecutor.shutdown()
        tts?.stop()
        tts?.shutdown()
    }
}
