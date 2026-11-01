package com.hisab.app.presentation.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Grouped bar chart drawn on a Canvas: one group per label, one bar per series. Values are plain
 * minor-unit Longs supplied by the caller (real ledger data); with no data nothing is drawn but the
 * axis. Tapping a group highlights it and reports its index so the screen can show exact figures
 * in text, which also keeps the numbers accessible.
 */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Series(val name: String, val color: Int, val values: List<Long>)

    var onGroupSelected: ((Int) -> Unit)? = null

    private var labels: List<String> = emptyList()
    private var series: List<Series> = emptyList()
    private var axisLabel: (Long) -> String = { it.toString() }
    private var selected = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    init {
        textPaint.color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666.toInt())
        textPaint.textSize = sp(10f)
        gridPaint.color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0xFFDDDDDD.toInt())
        highlightPaint.color = (MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()) and 0x00FFFFFF) or 0x14000000
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setData(labels: List<String>, series: List<Series>, axisLabel: (Long) -> String, description: String) {
        require(series.all { it.values.size == labels.size }) { "every series needs one value per label" }
        this.labels = labels
        this.series = series
        this.axisLabel = axisLabel
        this.selected = -1
        contentDescription = description
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(280f).toInt(), widthMeasureSpec),
            resolveSize(dp(200f).toInt(), heightMeasureSpec)
        )
    }

    private fun plotRect(maxValue: Long): RectF {
        val left = textPaint.measureText(axisLabel(maxValue)) + dp(10f)
        return RectF(left, dp(8f), width - dp(4f), height - dp(24f))
    }

    override fun onDraw(canvas: Canvas) {
        val maxValue = max(1L, series.maxOfOrNull { s -> s.values.maxOfOrNull { it.coerceAtLeast(0) } ?: 0L } ?: 0L)
        val plot = plotRect(maxValue)

        // gridlines + y labels at 0%, 50%, 100%
        for (step in 0..2) {
            val y = plot.bottom - plot.height() * step / 2f
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(axisLabel(maxValue * step / 2), plot.left - dp(4f), y + textPaint.textSize / 3, textPaint)
        }
        val n = labels.size
        if (n == 0 || series.isEmpty()) return

        val groupWidth = plot.width() / n
        val barWidth = min(groupWidth * 0.8f / series.size, dp(20f))
        val totalBars = barWidth * series.size
        val radius = min(barWidth / 2f, dp(4f))

        // x labels: skip some so they never overlap
        textPaint.textAlign = Paint.Align.CENTER
        val widest = labels.maxOf { textPaint.measureText(it) } + dp(6f)
        val every = max(1, ceil(widest / groupWidth).toInt())

        for (g in 0 until n) {
            val gx = plot.left + groupWidth * g
            if (g == selected) {
                rect.set(gx, plot.top, gx + groupWidth, plot.bottom)
                canvas.drawRect(rect, highlightPaint)
            }
            val startX = gx + (groupWidth - totalBars) / 2f
            series.forEachIndexed { si, s ->
                val v = s.values[g].coerceAtLeast(0)
                if (v > 0) {
                    val h = max(dp(2f), plot.height() * v / maxValue)
                    val x = startX + si * barWidth
                    rect.set(x + dp(0.5f), plot.bottom - h, x + barWidth - dp(0.5f), plot.bottom)
                    barPaint.color = s.color
                    canvas.drawRoundRect(rect, radius, radius, barPaint)
                }
            }
            if (g % every == 0) canvas.drawText(labels[g], gx + groupWidth / 2f, height - dp(6f), textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && labels.isNotEmpty()) {
            val maxValue = max(1L, series.maxOfOrNull { s -> s.values.maxOfOrNull { it.coerceAtLeast(0) } ?: 0L } ?: 0L)
            val plot = plotRect(maxValue)
            if (event.x >= plot.left && event.x <= plot.right) {
                val idx = ((event.x - plot.left) / (plot.width() / labels.size)).toInt().coerceIn(0, labels.lastIndex)
                selected = if (idx == selected) -1 else idx
                invalidate()
                if (selected >= 0) onGroupSelected?.invoke(selected)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
