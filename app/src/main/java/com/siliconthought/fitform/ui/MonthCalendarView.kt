package com.siliconthought.fitform.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.siliconthought.fitform.core.WorkoutMath
import java.util.Calendar

/**
 * A minimal month grid. Days passed via [setMonth] are drawn with a filled
 * highlight so the user can see their gym attendance at a glance.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private var year = 2024
    private var month = 0
    private var highlighted: Set<Int> = emptySet()

    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FB2D4")
        textSize = 12f * sp
        textAlign = Paint.Align.CENTER
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7E3F0")
        textSize = 14f * sp
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E5FF")
    }
    private val highlightedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06203A")
        textSize = 14f * sp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setMonth(year: Int, month: Int, highlightedDays: Set<Int>) {
        this.year = year
        this.month = month
        this.highlighted = highlightedDays
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 0.9f).toInt()
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rows = 6
        val headerHeight = height * 0.16f
        val cellWidth = width / 7f
        val cellHeight = (height - headerHeight) / rows

        val weekdayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
        for (column in 0 until 7) {
            val x = cellWidth * column + cellWidth / 2f
            canvas.drawText(weekdayLetters[column], x, headerHeight * 0.75f, weekdayPaint)
        }

        val daysInMonth = WorkoutMath.daysInMonth(year, month)
        val firstColumn = firstDayOfWeek() - 1

        for (day in 1..daysInMonth) {
            val index = firstColumn + (day - 1)
            if (index < 0) continue
            val row = index / 7
            if (row >= rows) break
            val column = index % 7

            val centerX = cellWidth * column + cellWidth / 2f
            val centerY = headerHeight + cellHeight * row + cellHeight / 2f

            if (day in highlighted) {
                canvas.drawCircle(centerX, centerY, minOf(cellWidth, cellHeight) * 0.33f, highlightPaint)
                canvas.drawText(day.toString(), centerX, centerY + 5f * density, highlightedTextPaint)
            } else {
                canvas.drawText(day.toString(), centerX, centerY + 5f * density, dayPaint)
            }
        }
    }

    private fun firstDayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        return calendar.get(Calendar.DAY_OF_WEEK)
    }
}
