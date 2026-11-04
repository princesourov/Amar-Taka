package com.hisab.app.domain.budget

import java.math.BigDecimal

/**
 * Parses what a user types into an amount field into exact minor units (poisha) using BigDecimal,
 * never Double — "0.07" * 100 as a Double is 7.000000000000001, and drifting by a poisha is exactly
 * the kind of error a finance app must not make.
 */
object MoneyParser {

    sealed interface Result {
        data class Ok(val minorUnits: Long) : Result
        data object Empty : Result
        data object Invalid : Result
        data object TooManyDecimals : Result
        data object NotPositive : Result
        data object TooLarge : Result
    }

    /** Upper bound: 10 billion major units — far beyond any real budget, blocks overflow/typos. */
    const val MAX_MINOR: Long = 10_000_000_000L * 100L

    fun parsePositive(text: String?): Result {
        val cleaned = text?.trim()?.replace(",", "")?.replace(" ", "").orEmpty()
        if (cleaned.isEmpty()) return Result.Empty
        if (!Regex("""^\d*\.?\d*$""").matches(cleaned) || cleaned == ".") return Result.Invalid
        val value = try { BigDecimal(cleaned) } catch (e: NumberFormatException) { return Result.Invalid }
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) return Result.TooManyDecimals
        if (value.signum() <= 0) return Result.NotPositive
        val minor = value.movePointRight(2)
        if (minor > BigDecimal.valueOf(MAX_MINOR)) return Result.TooLarge
        return Result.Ok(minor.longValueExact())
    }

    /** Convenience for forms: the exact minor units of a valid positive amount, else null. */
    fun parsePositiveMinor(text: String?): Long? = (parsePositive(text) as? Result.Ok)?.minorUnits

    /**
     * Opening-balance style input: blank = 0, a leading minus is allowed (a starting debt), at most two
     * decimals. Exact (BigDecimal), null when invalid.
     */
    fun parseSignedOrZero(text: String?): Long? {
        val cleaned = text?.trim()?.replace(",", "")?.replace(" ", "").orEmpty()
        if (cleaned.isEmpty()) return 0L
        if (!Regex("""^-?\d*\.?\d*$""").matches(cleaned) || cleaned == "-" || cleaned == "." || cleaned == "-.") return null
        val value = try { BigDecimal(cleaned) } catch (e: NumberFormatException) { return null }
        if (value.stripTrailingZeros().scale() > 2) return null
        val minor = value.movePointRight(2)
        if (minor.abs() > BigDecimal.valueOf(MAX_MINOR)) return null
        return minor.longValueExact()
    }

    /** "1234.5" for 123450 — plain decimal used to pre-fill edit fields and CSV (no grouping, no symbol). */
    fun toPlainDecimal(minorUnits: Long): String {
        val negative = minorUnits < 0
        val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits)
        val major = abs / 100
        val minor = abs % 100
        val body = if (minor == 0L) "$major" else "$major.${minor.toString().padStart(2, '0').trimEnd('0')}"
        return if (negative) "-$body" else body
    }

    /** Always two decimals: 123450 -> "1234.50". */
    fun toFixedDecimal(minorUnits: Long): String {
        val negative = minorUnits < 0
        val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minorUnits)
        val body = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        return if (negative) "-$body" else body
    }
}
