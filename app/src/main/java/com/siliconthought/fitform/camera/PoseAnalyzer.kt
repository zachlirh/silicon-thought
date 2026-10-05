package com.siliconthought.fitform.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Feeds each CameraX frame to ML Kit for pose detection and forwards the result.
 *
 * Callback args: (pose or null, uprightImageWidth, uprightImageHeight),
 * where the upright dimensions are the image size after rotation, used to map
 * the normalized landmarks back onto the preview.
 */
class PoseAnalyzer(
    private val onResult: (pose: Pose?, uprightWidth: Int, uprightHeight: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // ML Kit returns landmarks normalized to the upright image coordinate
        // system, so swap width/height when the rotation is 90/270.
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

        val input = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(input)
            .addOnSuccessListener { pose -> onResult(pose, uprightWidth, uprightHeight) }
            .addOnFailureListener { onResult(null, uprightWidth, uprightHeight) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        detector.close()
    }
}
