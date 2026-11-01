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
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Lightweight donut chart drawn on a Canvas (no third-party chart library). Slices are proportional
 * to their values; with no data it draws an empty ring rather than a made-up slice. Tapping a slice
 * selects it and shows its label in the centre.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Long, val color: Int, val detail: String = "")

    var onSliceSelected: ((Int) -> Unit)? = null

    private var slices: List<Slice> = emptyList()
    private var centerTitle = ""
    private var centerSubtitle = ""
    private var selected = -1

    private val oval = RectF()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var trackColor = 0

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    init {
        titlePaint.color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())
        subPaint.color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666.toInt())
        trackColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0xFFDDDDDD.toInt())
        titlePaint.textSize = sp(16f)
        subPaint.textSize = sp(12f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setData(slices: List<Slice>, centerTitle: String, centerSubtitle: String, description: String) {
        this.slices = slices.filter { it.value > 0 }
        this.centerTitle = centerTitle
        this.centerSubtitle = centerSubtitle
        this.selected = -1
        contentDescription = description
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(resolveSize(dp(220f).toInt(), widthMeasureSpec), dp(260f).toInt())
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = dp(26f)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - stroke / 2f - dp(4f)
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        ring.strokeWidth = stroke
        val total = slices.sumOf { it.value }
        if (total <= 0L) {
            ring.color = trackColor
            canvas.drawArc(oval, 0f, 360f, false, ring)
        } else {
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = s.value * 360f / total
                ring.color = s.color
                ring.strokeWidth = if (i == selected) stroke + dp(6f) else stroke
                canvas.drawArc(oval, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, ring)
                start += sweep
            }
        }

        val (title, sub) = if (selected in slices.indices) {
            val s = slices[selected]
            s.label to s.detail
        } else centerTitle to centerSubtitle
        val maxTextWidth = radius * 1.5f
        titlePaint.textSize = fit(title, titlePaint, sp(16f), maxTextWidth)
        canvas.drawText(title, cx, cy + (if (sub.isEmpty()) titlePaint.textSize / 3 else -dp(2f)), titlePaint)
        if (sub.isNotEmpty()) {
            subPaint.textSize = fit(sub, subPaint, sp(12f), maxTextWidth)
            canvas.drawText(sub, cx, cy + subPaint.textSize + dp(4f), subPaint)
        }
    }

    private fun fit(text: String, paint: Paint, maxSize: Float, maxWidth: Float): Float {
        paint.textSize = maxSize
        val w = paint.measureText(text)
        return if (w <= maxWidth || w == 0f) maxSize else maxSize * maxWidth / w
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && slices.isNotEmpty()) {
            val dx = event.x - width / 2f
            val dy = event.y - height / 2f
            val dist = hypot(dx, dy)
            val outer = min(width, height) / 2f
            if (dist > outer * 0.45f && dist <= outer) {
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                val total = slices.sumOf { it.value }.toFloat()
                val idx = pick(angle, total)
                selected = if (idx == selected) -1 else idx
                invalidate()
                if (selected >= 0) onSliceSelected?.invoke(selected)
                performClick()
            } else if (selected != -1) {
                selected = -1; invalidate()
            }
        }
        return true
    }

    private fun pick(angle: Float, total: Float): Int {
        var acc = 0f
        for ((i, s) in slices.withIndex()) {
            acc += s.value * 360f / total
            if (angle < acc) return i
        }
        return slices.lastIndex
    }

    override fun performClick(): Boolean = super.performClick()
}
