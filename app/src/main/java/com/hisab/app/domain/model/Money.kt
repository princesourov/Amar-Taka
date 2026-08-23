package com.hisab.app.domain.model

/**
 * All amounts are stored and moved around as Long minor units (poisha — 1 BDT
 * = 100 poisha), never as Float/Double. That's the "safe monetary calculations"
 * requirement from spec Section 29, satisfied via exact integer arithmetic
 * instead of BigDecimal, which is simpler and just as exact for a single-minor-
 * unit currency like BDT.
 *
 * Room columns stay plain `Long` for guaranteed compatibility — this wrapper is
 * for domain/UI code that wants safer arithmetic than raw Long addition.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(minorUnits - other.minorUnits)
    override fun compareTo(other: Money) = minorUnits.compareTo(other.minorUnits)

    companion object {
        val ZERO = Money(0L)
        fun fromMajorUnits(amount: Double): Money = Money(Math.round(amount * 100))
    }
}
