package com.hisab.app.domain.model

import kotlin.math.abs

/** Compact money labels for chart axes: 1,250,000.00 -> "12.5K"-style, without a currency symbol. */
object MoneyFormat {
    fun compactMajor(minorUnits: Long): String {
        val negative = minorUnits < 0
        val major = abs(minorUnits) / 100.0
        val body = when {
            major >= 10_000_000 -> trim(major / 10_000_000) + "Cr"   // crore, the common unit in Bangladesh
            major >= 100_000 -> trim(major / 100_000) + "L"          // lakh
            major >= 1_000 -> trim(major / 1_000) + "K"
            else -> trim(major)
        }
        return if (negative) "-$body" else body
    }

    private fun trim(v: Double): String {
        val oneDecimal = Math.round(v * 10) / 10.0
        return if (oneDecimal == Math.floor(oneDecimal)) oneDecimal.toLong().toString() else oneDecimal.toString()
    }
}
