package com.siliconthought.fitform.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Spider / radar chart used to visualise training volume across the six
 * muscle groups. Values are normalised against the largest value, so the
 * biggest group always reaches the outer ring.
 */
class RadarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private var values: FloatArray = FloatArray(6)
    private var labels: List<String> = listOf("Back", "Chest", "Core", "Shoulders", "Arms", "Legs")

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#4DFFFFFF")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5500E5FF")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00E5FF")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E5FF")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E3F0")
        textSize = 12f * sp
        textAlign = Paint.Align.CENTER
    }

    fun setData(values: FloatArray, labels: List<String> = emptyList()) {
        this.values = values
        if (labels.isNotEmpty()) this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val n = values.size
        if (n < 3) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.64f

        // Concentric grid rings
        for (ring in 1..4) {
            val r = radius * ring / 4f
            val path = Path()
            for (i in 0 until n) {
                val a = angleFor(i, n)
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, gridPaint)
        }

        // Spokes and axis labels
        for (i in 0 until n) {
            val a = angleFor(i, n)
            canvas.drawLine(cx, cy, cx + radius * cos(a), cy + radius * sin(a), gridPaint)
            val lx = cx + (radius + 26f * density) * cos(a)
            val ly = cy + (radius + 26f * density) * sin(a) + 4f * density
            canvas.drawText(labels.getOrElse(i) { "" }, lx, ly, labelPaint)
        }

        // Data polygon
        val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val dataPath = Path()
        for (i in 0 until n) {
            val a = angleFor(i, n)
            val r = radius * (values[i] / maxValue).coerceIn(0f, 1f)
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        canvas.drawPath(dataPath, fillPaint)
        canvas.drawPath(dataPath, linePaint)

        // Vertex markers
        for (i in 0 until n) {
            val a = angleFor(i, n)
            val r = radius * (values[i] / maxValue).coerceIn(0f, 1f)
            canvas.drawCircle(cx + r * cos(a), cy + r * sin(a), 3f * density, dotPaint)
        }
    }

    private fun angleFor(i: Int, n: Int): Float =
        (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
}
