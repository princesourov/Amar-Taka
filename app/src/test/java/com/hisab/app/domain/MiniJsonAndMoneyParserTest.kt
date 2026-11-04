package com.hisab.app.domain

import com.hisab.app.data.backup.MiniJson
import com.hisab.app.domain.budget.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MiniJsonAndMoneyParserTest {
    @Test fun roundTripsAllValueKinds() {
        val v = linkedMapOf<String, Any?>(
            "s" to "quote\" back\\ nl\n tab\t unicode ৳ é 😀", "n" to null, "t" to true, "l" to Long.MAX_VALUE,
            "neg" to -42L, "d" to 1.5, "list" to listOf(1L, "x", null, listOf<Any?>(), linkedMapOf<String, Any?>()),
            "ctl" to "\u0001"
        )
        assertEquals(v, MiniJson.parse(MiniJson.write(v)))
    }

    @Test fun writingIsDeterministicAndCompact() {
        assertEquals("""{"a":1,"b":[true,null]}""", MiniJson.write(linkedMapOf("a" to 1L, "b" to listOf(true, null))))
    }

    @Test fun rejectsMalformedAndTruncatedInput() {
        for (bad in listOf("", "{", "{\"a\":1", "[1,2", "{\"a\":}", "{\"a\" 1}", "tru", "\"unterminated", "{\"a\":1}x", "{\"a\":1,\"a\":2}", "{'a':1}")) {
            try { MiniJson.parse(bad); fail("should reject: $bad") } catch (e: MiniJson.JsonException) { /* expected */ }
        }
    }

    @Test fun parsesWhitespaceAndNumbers() {
        val m = MiniJson.parse(" { \"a\" : [ 1 , -2 , 3.5 , 1e3 ] } ") as Map<*, *>
        assertEquals(listOf(1L, -2L, 3.5, 1000.0), m["a"])
    }

    @Test fun parsePositiveMinorIsExactForFloatingPointTraps() {
        assertEquals(7L, MoneyParser.parsePositiveMinor("0.07"))
        assertEquals(28L, MoneyParser.parsePositiveMinor("0.28"))      // 0.285*100 style traps never happen with BigDecimal
        assertEquals(123_456L, MoneyParser.parsePositiveMinor("1,234.56"))
        assertNull(MoneyParser.parsePositiveMinor("0")); assertNull(MoneyParser.parsePositiveMinor("-5"))
        assertNull(MoneyParser.parsePositiveMinor("1.234")); assertNull(MoneyParser.parsePositiveMinor("abc")); assertNull(MoneyParser.parsePositiveMinor(""))
    }

    @Test fun signedOpeningBalance() {
        assertEquals(0L, MoneyParser.parseSignedOrZero("")); assertEquals(0L, MoneyParser.parseSignedOrZero("  "))
        assertEquals(-5_050L, MoneyParser.parseSignedOrZero("-50.5")); assertEquals(10_000L, MoneyParser.parseSignedOrZero("100"))
        assertNull(MoneyParser.parseSignedOrZero("-")); assertNull(MoneyParser.parseSignedOrZero("1.999")); assertNull(MoneyParser.parseSignedOrZero("x1"))
        assertTrue(MoneyParser.parseSignedOrZero("99999999999999999") == null)
    }
}
