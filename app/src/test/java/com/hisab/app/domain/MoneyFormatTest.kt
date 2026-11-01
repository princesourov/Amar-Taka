package com.hisab.app.domain

import com.hisab.app.domain.model.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test fun compact_labels() {
        assertEquals("0", MoneyFormat.compactMajor(0))
        assertEquals("950", MoneyFormat.compactMajor(95_000))
        assertEquals("1K", MoneyFormat.compactMajor(100_000))
        assertEquals("1.5K", MoneyFormat.compactMajor(150_000))
        assertEquals("1L", MoneyFormat.compactMajor(10_000_000))
        assertEquals("2.5L", MoneyFormat.compactMajor(25_000_000))
        assertEquals("1Cr", MoneyFormat.compactMajor(1_000_000_000))
        assertEquals("-1.5K", MoneyFormat.compactMajor(-150_000))
    }
}
