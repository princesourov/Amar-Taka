package com.hisab.app.domain

import com.hisab.app.domain.dashboard.DailySummaryCalculator
import com.hisab.app.domain.dashboard.DashboardMath
import com.hisab.app.domain.report.LedgerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailySummaryCalculatorTest {

    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 11, 5)

    private fun row(type: String, amount: Long, date: LocalDate, hour: Int = 12, minute: Int = 0, id: String = "$type-$amount-$date-$hour") =
        LedgerRow(
            id = id, type = type, amountMinor = amount,
            dateMillis = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        )

    @Test fun alwaysReturnsThirtyDaysNewestFirstEvenWithNoTransactions() {
        val days = DailySummaryCalculator.compute(emptyList(), today, zone)
        assertEquals(30, days.size)
        assertEquals(today, days.first().date)
        assertEquals(today.minusDays(1), days[1].date)
        assertEquals(today.minusDays(29), days.last().date)
        assertTrue(days.zipWithNext().all { (a, b) -> a.date.isAfter(b.date) })
        days.forEach {
            assertEquals(0, it.incomeMinor); assertEquals(0, it.expenseMinor); assertEquals(0, it.netCashFlowMinor)
            assertNull(it.savingsRatePercent)
            assertFalse(it.hasActivity)
        }
    }

    @Test fun sumsIncomeAndExpensePerTransactionDate() {
        val rows = listOf(
            row("INCOME", 10_000, today), row("INCOME", 5_000, today, hour = 8),
            row("EXPENSE", 6_000, today), row("EXPENSE", 1_000, today.minusDays(2)),
            row("INCOME", 20_000, today.minusDays(2))
        )
        val days = DailySummaryCalculator.compute(rows, today, zone)
        val t = days[0]
        assertEquals(15_000, t.incomeMinor); assertEquals(6_000, t.expenseMinor)
        assertEquals(9_000, t.netCashFlowMinor); assertEquals(60, t.savingsRatePercent)
        assertEquals(0, days[1].incomeMinor)
        val d2 = days[2]
        assertEquals(20_000, d2.incomeMinor); assertEquals(1_000, d2.expenseMinor)
        assertEquals(19_000, d2.netCashFlowMinor); assertEquals(95, d2.savingsRatePercent)
    }

    @Test fun usesTheDashboardRulesRefundsReduceExpenseAndNonIncomeExpenseTypesAreIgnored() {
        val rows = listOf(
            row("EXPENSE", 10_000, today), row("REFUND", 2_500, today),
            row("TRANSFER", 99_999, today), row("LENDING", 99_999, today), row("BORROWING", 99_999, today),
            row("REPAYMENT_RECEIVED", 99_999, today), row("REPAYMENT_MADE", 99_999, today),
            row("GIFT", 99_999, today), row("OTHER", 99_999, today)
        )
        val t = DailySummaryCalculator.compute(rows, today, zone)[0]
        assertEquals(0, t.incomeMinor)
        assertEquals(7_500, t.expenseMinor)
        assertEquals(-7_500, t.netCashFlowMinor)
        assertNull(t.savingsRatePercent) // no income -> not calculable, no division by zero
    }

    @Test fun savingsRateIsExactlyTheDashboardFormulaIncludingNegativeRates() {
        val t = DailySummaryCalculator.compute(
            listOf(row("INCOME", 10_000, today), row("EXPENSE", 15_000, today)), today, zone
        )[0]
        assertEquals(DashboardMath.savingsRatePercent(10_000, 15_000), t.savingsRatePercent)
        assertEquals(-50, t.savingsRatePercent)
        assertEquals(-5_000, t.netCashFlowMinor)
    }

    @Test fun dayBoundariesFollowTheLocalZoneNotUtc() {
        // 23:59 and 00:00 local, one minute apart, land on different days.
        val lateYesterday = row("EXPENSE", 100, today.minusDays(1), hour = 23, minute = 59, id = "a")
        val earlyToday = row("EXPENSE", 200, today, hour = 0, minute = 0, id = "b")
        val days = DailySummaryCalculator.compute(listOf(lateYesterday, earlyToday), today, zone)
        assertEquals(200, days[0].expenseMinor)
        assertEquals(100, days[1].expenseMinor)
    }

    @Test fun rowsOutsideTheThirtyDayWindowAreIgnored() {
        val rows = listOf(
            row("INCOME", 1_000, today.minusDays(29)), // oldest included day
            row("INCOME", 7_000, today.minusDays(30)), // too old
            row("INCOME", 9_000, today.plusDays(1))    // future
        )
        val days = DailySummaryCalculator.compute(rows, today, zone)
        assertEquals(1_000, days.last().incomeMinor)
        assertEquals(1_000, days.sumOf { it.incomeMinor })
    }

    @Test fun totalsOverThirtyDaysEqualTheDashboardTotalsForTheSameRows() {
        val rows = (0..29).flatMap { i ->
            listOf(
                row("INCOME", 1_000L + i, today.minusDays(i.toLong())),
                row("EXPENSE", 400L + i, today.minusDays(i.toLong()), hour = 9),
                row("REFUND", 10L, today.minusDays(i.toLong()), hour = 10),
                row("TRANSFER", 5_000L, today.minusDays(i.toLong()), hour = 11)
            )
        }
        val days = DailySummaryCalculator.compute(rows, today, zone)
        val income = days.sumOf { it.incomeMinor }
        val expense = days.sumOf { it.expenseMinor }
        assertEquals((0..29).sumOf { 1_000L + it }, income)
        assertEquals((0..29).sumOf { 400L + it - 10L }, expense)
        assertEquals(DashboardMath.netCashFlow(income, expense), days.sumOf { it.netCashFlowMinor })
    }
}
