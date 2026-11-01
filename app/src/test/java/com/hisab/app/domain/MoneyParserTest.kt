package com.hisab.app.domain

import com.hisab.app.domain.budget.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParserTest {
    private fun ok(text: String) = (MoneyParser.parsePositive(text) as MoneyParser.Result.Ok).minorUnits

    @Test fun parses_exactly_without_floating_point_error() {
        assertEquals(7L, ok("0.07"))           // 0.07 * 100 as a Double is 7.000000000000001
        assertEquals(1_005L, ok("10.05"))
        assertEquals(29L, ok("0.29"))          // 0.29 * 100 == 28.999999999999996 as a Double
        assertEquals(123_456_789L, ok("1234567.89"))
        assertEquals(50L, ok(".5"))
        assertEquals(500L, ok("5."))
        assertEquals(500L, ok("5.00"))
        assertEquals(510L, ok("5.100"))        // trailing zeros beyond 2dp are not extra precision
        assertEquals(100_000L, ok("1,000"))
    }

    @Test fun rejects() {
        assertEquals(MoneyParser.Result.Empty, MoneyParser.parsePositive("   "))
        assertEquals(MoneyParser.Result.NotPositive, MoneyParser.parsePositive("0"))
        assertEquals(MoneyParser.Result.Invalid, MoneyParser.parsePositive("1e3"))
        assertEquals(MoneyParser.Result.Invalid, MoneyParser.parsePositive("৳50"))
        assertEquals(MoneyParser.Result.TooManyDecimals, MoneyParser.parsePositive("0.001"))
        assertEquals(MoneyParser.Result.TooLarge, MoneyParser.parsePositive("10000000001"))
    }

    @Test fun limit_is_inclusive() {
        assertEquals(MoneyParser.MAX_MINOR, ok("10000000000"))
    }

    @Test fun formatting() {
        assertEquals("1234.5", MoneyParser.toPlainDecimal(123_450))
        assertEquals("1234", MoneyParser.toPlainDecimal(123_400))
        assertEquals("0.07", MoneyParser.toPlainDecimal(7))
        assertEquals("0.7", MoneyParser.toPlainDecimal(70))
        assertEquals("-12.34", MoneyParser.toPlainDecimal(-1_234))
        assertEquals("1234.50", MoneyParser.toFixedDecimal(123_450))
        assertEquals("0.05", MoneyParser.toFixedDecimal(5))
        assertEquals("-0.05", MoneyParser.toFixedDecimal(-5))
    }

    @Test fun plain_decimal_round_trips_through_the_parser() {
        for (v in listOf(1L, 7L, 70L, 100L, 123_450L, 99_999_999L)) assertEquals(v, ok(MoneyParser.toPlainDecimal(v)))
    }
}
