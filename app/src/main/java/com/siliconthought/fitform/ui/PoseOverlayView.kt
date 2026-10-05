package com.siliconthought.fitform.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

/**
 * Draws the skeleton on top of the camera preview.
 *
 * Mapping matches PreviewView's default FILL_CENTER behavior:
 *   scale = max(viewW/imgW, viewH/imgH), then center-crop.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 7f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var pose: Pose? = null
    private var imageWidth = 1
    private var imageHeight = 1
    private var mirror = false

    private val connections = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
    )

    fun update(newPose: Pose?, imgW: Int, imgH: Int, mirrorImage: Boolean) {
        pose = newPose
        imageWidth = if (imgW > 0) imgW else 1
        imageHeight = if (imgH > 0) imgH else 1
        mirror = mirrorImage
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pose ?: return

        val scale = max(width / imageWidth.toFloat(), height / imageHeight.toFloat())
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        fun mapX(lm: PoseLandmark): Float {
            val px = if (mirror) 1f - lm.position.x else lm.position.x
            return offsetX + px * imageWidth * scale
        }

        fun mapY(lm: PoseLandmark): Float = offsetY + lm.position.y * imageHeight * scale

        // Bones
        for ((a, b) in connections) {
            val la = p.getPoseLandmark(a) ?: continue
            val lb = p.getPoseLandmark(b) ?: continue
            canvas.drawLine(mapX(la), mapY(la), mapX(lb), mapY(lb), linePaint)
        }

        // Landmarks
        for (lm in p.allPoseLandmarks) {
            canvas.drawCircle(mapX(lm), mapY(lm), 9f, pointPaint)
        }
    }
}
