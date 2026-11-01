package com.hisab.app.presentation.widget

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.hisab.app.R
import kotlin.math.abs

/** Stable colours for chart series/categories; the same category always gets the same colour. */
object ChartPalette {
    private val slots = intArrayOf(
        R.color.chart_1, R.color.chart_2, R.color.chart_3, R.color.chart_4, R.color.chart_5,
        R.color.chart_6, R.color.chart_7, R.color.chart_8, R.color.chart_9, R.color.chart_10
    )

    fun colorFor(context: Context, key: String?, hex: String?): Int {
        if (!hex.isNullOrBlank()) {
            try { return Color.parseColor(hex) } catch (_: IllegalArgumentException) { /* fall through to palette */ }
        }
        val idx = if (key == null) slots.size - 1 else abs(key.hashCode()) % slots.size
        return ContextCompat.getColor(context, slots[idx])
    }
}
