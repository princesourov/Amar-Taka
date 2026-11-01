package com.hisab.app.domain

import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.Insight
import com.hisab.app.domain.report.InsightEngine
import com.hisab.app.domain.report.LedgerRow
import com.hisab.app.domain.report.ReportCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightEngineTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 3, 31)
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private var n = 0
    private fun r(type: String, amt: Long, m: Int, cat: String? = null) = LedgerRow("i${n++}", type, amt, cat, dateMillis = ms(m, 5))
    private fun summary(rows: List<LedgerRow>, span: DateSpan) = ReportCalculator.compute(rows, span, zone, today, DayOfWeek.MONDAY)
    private val march = DateSpan(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
    private val feb = DateSpan(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))

    @Test fun no_data_no_insights() {
        assertTrue(InsightEngine.generate(summary(emptyList(), march), null).isEmpty())
    }

    @Test fun spending_increase_and_top_category() {
        val cur = summary(listOf(r("INCOME", 100_000, 3), r("EXPENSE", 60_000, 3, "food"), r("EXPENSE", 20_000, 3, "bus")), march)
        val prev = summary(listOf(r("EXPENSE", 40_000, 2, "food"), r("EXPENSE", 20_000, 2, "bus")), feb)
        val insights = InsightEngine.generate(cur, prev, maxInsights = 10)
        val top = insights.filterIsInstance<Insight.TopCategory>().single()
        assertEquals("food", top.categoryId); assertEquals(75.0, top.sharePercent, 1e-9)
        assertEquals(33.333, insights.filterIsInstance<Insight.SpendingChange>().single().percent, 0.001)
        val inc = insights.filterIsInstance<Insight.CategoryIncrease>().single()
        assertEquals("food", inc.categoryId); assertEquals(50.0, inc.percent, 1e-9)
    }

    @Test fun negative_cash_flow_when_spending_exceeds_income() {
        val cur = summary(listOf(r("INCOME", 10_000, 3), r("EXPENSE", 25_000, 3, "food")), march)
        val i = InsightEngine.generate(cur, null).filterIsInstance<Insight.NegativeCashFlow>().single()
        assertEquals(15_000L, i.shortfallMinor)
    }

    @Test fun spending_with_no_income_is_not_reported_as_a_savings_rate() {
        val cur = summary(listOf(r("EXPENSE", 25_000, 3, "food")), march)
        val all = InsightEngine.generate(cur, null)
        assertTrue(all.any { it is Insight.SpentWithoutIncome })
        assertTrue(all.none { it is Insight.SavingsRate })
    }

    @Test fun no_comparison_insight_when_previous_period_is_empty() {
        val cur = summary(listOf(r("EXPENSE", 25_000, 3, "food"), r("EXPENSE", 1, 3, "bus")), march)
        val prev = summary(emptyList(), feb)
        assertTrue(InsightEngine.generate(cur, prev).none { it is Insight.SpendingChange })
    }

    @Test fun small_changes_are_not_news() {
        val cur = summary(listOf(r("EXPENSE", 10_200, 3, "food"), r("EXPENSE", 100, 3, "bus")), march)
        val prev = summary(listOf(r("EXPENSE", 10_000, 2, "food"), r("EXPENSE", 100, 2, "bus")), feb)
        assertTrue(InsightEngine.generate(cur, prev).none { it is Insight.SpendingChange })
    }

    @Test fun budget_insights_and_cap() {
        val cur = summary(listOf(r("INCOME", 100_000, 3), r("EXPENSE", 10_000, 3, "food"), r("EXPENSE", 5_000, 3, "bus")), march)
        val list = InsightEngine.generate(cur, null, budgetsOver = 2, budgetsNear = 1, maxInsights = 2)
        assertEquals(2, list.size)
        assertEquals(Insight.BudgetsOver(2), list.first())
    }
}
