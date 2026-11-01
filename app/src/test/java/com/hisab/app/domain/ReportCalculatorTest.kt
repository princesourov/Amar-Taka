package com.hisab.app.domain

import com.hisab.app.domain.report.BucketPoint
import com.hisab.app.domain.report.Change
import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.Granularity
import com.hisab.app.domain.report.LedgerRow
import com.hisab.app.domain.report.NameLookup
import com.hisab.app.domain.report.ReportCalculator
import com.hisab.app.domain.report.ReportFilter
import com.hisab.app.domain.report.TypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReportCalculatorTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 3, 31)
    private val march = DateSpan(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))

    private var seq = 0
    private fun millis(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0, nano: Int = 0) =
        LocalDateTime.of(y, m, d, h, min, s, nano).atZone(zone).toInstant().toEpochMilli()

    private fun row(
        type: String, amount: Long, day: Int, cat: String? = null, src: String? = null, dst: String? = null,
        note: String? = null, month: Int = 3, h: Int = 12
    ) = LedgerRow("r${seq++}", type, amount, cat, src, dst, note, millis(2026, month, day, h))

    private fun calc(rows: List<LedgerRow>, span: DateSpan = march, filter: ReportFilter = ReportFilter(), names: NameLookup = NameLookup.Empty) =
        ReportCalculator.compute(rows, span, zone, today, DayOfWeek.MONDAY, filter, names)

    // ---------- classification ----------

    @Test fun empty_history_has_zero_everything_and_no_invented_ratios() {
        val s = calc(emptyList())
        assertEquals(0L, s.incomeMinor); assertEquals(0L, s.expenseMinor); assertEquals(0L, s.netCashFlowMinor)
        assertNull(s.savingsRate); assertNull(s.expenseToIncomeRatio)
        assertFalse(s.hasActivity)
        assertTrue(s.expenseByCategory.isEmpty())
        assertTrue(s.countedRowIds.isEmpty())
    }

    @Test fun adding_income_and_expense() {
        val s = calc(listOf(row("INCOME", 100_000, 2, "salary", dst = "a"), row("EXPENSE", 25_050, 3, "food", src = "a")))
        assertEquals(100_000L, s.incomeMinor)
        assertEquals(25_050L, s.expenseMinor)
        assertEquals(74_950L, s.netCashFlowMinor)
        assertEquals(74.95, s.savingsRate!!, 1e-9)
        assertEquals(25.05, s.expenseToIncomeRatio!!, 1e-9)
    }

    @Test fun transfers_are_never_income_or_expense() {
        val s = calc(listOf(
            row("INCOME", 50_000, 2, dst = "a"),
            row("TRANSFER", 30_000, 3, src = "a", dst = "b"),
            row("TRANSFER", 10_000, 4, src = "b", dst = "a")
        ))
        assertEquals(50_000L, s.incomeMinor)
        assertEquals(0L, s.expenseMinor)
        assertEquals(2, s.excludedTransfers)
        assertEquals(1, s.countedRowIds.size)
    }

    @Test fun loans_and_repayments_do_not_double_count_principal() {
        val s = calc(listOf(
            row("LENDING", 100_000, 2, src = "a"),
            row("REPAYMENT_RECEIVED", 40_000, 5, dst = "a"),
            row("BORROWING", 20_000, 6, dst = "a"),
            row("REPAYMENT_MADE", 5_000, 7, src = "a")
        ))
        assertEquals(0L, s.incomeMinor); assertEquals(0L, s.expenseMinor)
        assertEquals(4, s.excludedLoanMovements)
        assertFalse(s.hasActivity)
    }

    @Test fun unknown_and_other_types_are_excluded_not_silently_counted() {
        val s = calc(listOf(row("GIFT", 500, 2), row("OTHER", 700, 2), row("SOMETHING_NEW", 900, 2)))
        assertEquals(3, s.excludedOther)
        assertEquals(0L, s.incomeMinor + s.expenseMinor)
    }

    @Test fun refund_reduces_spending_in_its_category_and_is_not_income() {
        val s = calc(listOf(
            row("EXPENSE", 10_000, 2, "shop"),
            row("EXPENSE", 5_000, 3, "food"),
            row("REFUND", 4_000, 9, "shop")
        ))
        assertEquals(0L, s.incomeMinor)
        assertEquals(15_000L, s.grossExpenseMinor)
        assertEquals(4_000L, s.refundMinor)
        assertEquals(11_000L, s.expenseMinor)
        val shop = s.expenseByCategory.first { it.categoryId == "shop" }
        assertEquals(6_000L, shop.amountMinor)
        assertEquals(2, shop.count)
    }

    @Test fun refund_larger_than_category_spend_is_left_out_of_breakdown_but_still_reduces_total() {
        val s = calc(listOf(
            row("EXPENSE", 1_000, 2, "shop"),
            row("EXPENSE", 9_000, 2, "food"),
            row("REFUND", 3_000, 4, "shop") // refund of a purchase made in an earlier period
        ))
        assertEquals(7_000L, s.expenseMinor)
        assertEquals(listOf("food"), s.expenseByCategory.map { it.categoryId })
        assertEquals(100.0, s.expenseByCategory.sumOf { it.sharePercent }, 1e-9)
    }

    @Test fun zero_amount_rows_are_harmless() {
        val s = calc(listOf(row("EXPENSE", 0, 2, "food"), row("INCOME", 0, 2)))
        assertEquals(0L, s.expenseMinor); assertEquals(0L, s.incomeMinor)
        assertNull(s.savingsRate)
    }

    @Test fun expense_with_no_income_gives_negative_cash_flow_and_null_rates() {
        val s = calc(listOf(row("EXPENSE", 1_234, 2, "food")))
        assertEquals(-1_234L, s.netCashFlowMinor)
        assertNull(s.savingsRate); assertNull(s.expenseToIncomeRatio)
    }

    @Test fun money_is_exact_with_awkward_amounts() {
        val rows = (1..1000).map { row("EXPENSE", 7, 2, "food") } // 1000 x 0.07
        assertEquals(7_000L, calc(rows).expenseMinor)
    }

    // ---------- date boundaries ----------

    @Test fun first_and_last_millisecond_of_period_are_included_and_neighbours_excluded() {
        val rows = listOf(
            LedgerRow("before", "EXPENSE", 100, "f", dateMillis = millis(2026, 2, 28, 23, 59, 59, 999_000_000)),
            LedgerRow("first", "EXPENSE", 10, "f", dateMillis = millis(2026, 3, 1, 0, 0, 0, 0)),
            LedgerRow("last", "EXPENSE", 20, "f", dateMillis = millis(2026, 3, 31, 23, 59, 59, 999_000_000)),
            LedgerRow("after", "EXPENSE", 1000, "f", dateMillis = millis(2026, 4, 1, 0, 0, 0, 0))
        )
        val s = calc(rows)
        assertEquals(30L, s.expenseMinor)
        assertEquals(setOf("first", "last"), s.countedRowIds.toSet())
    }

    @Test fun year_boundary_does_not_leak() {
        val rows = listOf(
            LedgerRow("dec", "INCOME", 5, dateMillis = millis(2025, 12, 31, 23, 59, 59)),
            LedgerRow("jan", "INCOME", 7, dateMillis = millis(2026, 1, 1, 0, 0, 0))
        )
        val jan = DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertEquals(7L, calc(rows, jan).incomeMinor)
    }

    // ---------- categories ----------

    @Test fun category_breakdown_totals_percent_count_and_order() {
        val s = calc(listOf(
            row("EXPENSE", 6_000, 2, "food"), row("EXPENSE", 2_000, 3, "food"),
            row("EXPENSE", 1_000, 4, "bus"), row("EXPENSE", 1_000, 5, null)
        ))
        assertEquals(listOf("food", "bus", null), s.expenseByCategory.map { it.categoryId }.let { l -> l })
        val food = s.expenseByCategory[0]
        assertEquals(8_000L, food.amountMinor); assertEquals(2, food.count); assertEquals(80.0, food.sharePercent, 1e-9)
        assertEquals(100.0, s.expenseByCategory.sumOf { it.sharePercent }, 1e-9)
        assertEquals(s.expenseMinor, s.expenseByCategory.sumOf { it.amountMinor })
    }

    @Test fun income_breakdown() {
        val s = calc(listOf(row("INCOME", 3_000, 2, "salary"), row("INCOME", 1_000, 5, "gift")))
        assertEquals(75.0, s.incomeByCategory.first().sharePercent, 1e-9)
    }

    // ---------- filters ----------

    private val names = object : NameLookup {
        val cats = mapOf("food" to "Food & Dining", "bus" to "Transportation")
        val accs = mapOf("a" to "Cash", "b" to "bKash")
        override fun categoryName(id: String?) = cats[id]
        override fun accountName(id: String?) = accs[id]
    }

    private fun sample() = listOf(
        row("INCOME", 100_000, 2, "salary", dst = "a", note = "March pay"),
        row("EXPENSE", 20_000, 3, "food", src = "a", note = "Lunch with team"),
        row("EXPENSE", 5_000, 4, "bus", src = "b", note = "ride"),
        row("TRANSFER", 9_000, 5, src = "a", dst = "b"),
        row("REFUND", 1_000, 6, "food", dst = "a", note = "refund")
    )

    @Test fun type_filter_income_only() {
        val s = calc(sample(), filter = ReportFilter(type = TypeFilter.INCOME))
        assertEquals(100_000L, s.incomeMinor); assertEquals(0L, s.expenseMinor)
    }

    @Test fun type_filter_expense_includes_refunds_that_offset_it() {
        val s = calc(sample(), filter = ReportFilter(type = TypeFilter.EXPENSE))
        assertEquals(0L, s.incomeMinor); assertEquals(24_000L, s.expenseMinor)
    }

    @Test fun category_filter() {
        val s = calc(sample(), filter = ReportFilter(categoryId = "food"))
        assertEquals(19_000L, s.expenseMinor); assertEquals(0L, s.incomeMinor)
    }

    @Test fun account_filter_matches_source_or_destination() {
        val s = calc(sample(), filter = ReportFilter(accountId = "b"))
        assertEquals(5_000L, s.expenseMinor)
        assertEquals(0L, s.incomeMinor)
        assertEquals(1, s.excludedTransfers) // the transfer touches account b
    }

    @Test fun keyword_matches_note_category_and_account_names_case_insensitively() {
        assertEquals(20_000L, calc(sample(), filter = ReportFilter(keyword = "LUNCH"), names = names).expenseMinor)
        assertEquals(19_000L, calc(sample(), filter = ReportFilter(keyword = "dining"), names = names).expenseMinor)
        assertEquals(5_000L, calc(sample(), filter = ReportFilter(keyword = "bkash"), names = names).expenseMinor)
        assertEquals(0L, calc(sample(), filter = ReportFilter(keyword = "zzz"), names = names).expenseMinor)
    }

    @Test fun combined_filters_are_and() {
        val s = calc(sample(), filter = ReportFilter(type = TypeFilter.EXPENSE, accountId = "a", keyword = "lunch"), names = names)
        assertEquals(20_000L, s.expenseMinor)
    }

    @Test fun filter_isActive_flag() {
        assertFalse(ReportFilter().isActive)
        assertTrue(ReportFilter(keyword = "x").isActive)
        assertFalse(ReportFilter(keyword = "   ").isActive)
    }

    // ---------- reconciliation ----------

    @Test fun totals_reconcile_with_the_listed_rows() {
        val rows = sample()
        val s = calc(rows)
        val byId = rows.associateBy { it.id }
        var income = 0L; var expense = 0L
        for (id in s.countedRowIds) {
            val r = byId.getValue(id)
            when (r.type) { "INCOME" -> income += r.amountMinor; "EXPENSE" -> expense += r.amountMinor; "REFUND" -> expense -= r.amountMinor }
        }
        assertEquals(s.incomeMinor, income); assertEquals(s.expenseMinor, expense)
        assertEquals(s.series.sumOf { it.incomeMinor }, s.incomeMinor)
        assertEquals(s.series.sumOf { it.expenseMinor }, s.expenseMinor)
    }

    @Test fun removing_a_row_models_delete_and_edit_changes_totals_correctly() {
        val rows = sample().toMutableList()
        val before = calc(rows).expenseMinor
        val removed = rows.first { it.type == "EXPENSE" && it.amountMinor == 5_000L }
        rows.remove(removed)                              // delete (isDeleted rows are never passed in)
        assertEquals(before - 5_000, calc(rows).expenseMinor)
        rows.add(removed.copy(amountMinor = 8_000))       // restore with an edited amount
        assertEquals(before + 3_000, calc(rows).expenseMinor)
    }

    // ---------- series ----------

    @Test fun series_granularity_by_span_length() {
        fun g(days: Long) = ReportCalculator.granularityFor(DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(days - 1)))
        assertEquals(Granularity.DAY, g(1)); assertEquals(Granularity.DAY, g(14))
        assertEquals(Granularity.WEEK, g(15)); assertEquals(Granularity.WEEK, g(92))
        assertEquals(Granularity.MONTH, g(93)); assertEquals(Granularity.MONTH, g(365))
    }

    @Test fun weekly_buckets_cover_the_month_without_gaps_or_overlap() {
        val s = calc(listOf(row("EXPENSE", 100, 1), row("EXPENSE", 200, 15), row("INCOME", 900, 31)))
        assertEquals(Granularity.WEEK, s.granularity)
        assertEquals(march.start, s.series.first().start)
        assertEquals(march.end, s.series.last().end)
        s.series.zipWithNext().forEach { (a, b) -> assertEquals(a.end.plusDays(1), b.start) }
        assertEquals(300L, s.series.sumOf { it.expenseMinor })
        assertEquals(900L, s.series.sumOf { it.incomeMinor })
    }

    @Test fun daily_buckets_for_short_spans() {
        val week = DateSpan(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15))
        val s = calc(listOf(row("EXPENSE", 50, 9), row("EXPENSE", 70, 15)), span = week)
        assertEquals(Granularity.DAY, s.granularity)
        assertEquals(7, s.series.size)
        assertEquals(50L, s.series.first().expenseMinor); assertEquals(70L, s.series.last().expenseMinor)
        assertEquals(0L, s.series[3].expenseMinor)
    }

    @Test fun monthly_buckets_for_a_year() {
        val year = DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        val today = LocalDate.of(2026, 5, 20)
        val rows = listOf(row("EXPENSE", 100, 10, month = 1), row("EXPENSE", 300, 10, month = 5))
        val s = ReportCalculator.compute(rows, year, zone, today, DayOfWeek.MONDAY)
        assertEquals(Granularity.MONTH, s.granularity)
        assertEquals(5, s.series.size) // Jan..May — future empty months are not drawn
        assertEquals(100L, s.series.first().expenseMinor); assertEquals(300L, s.series.last().expenseMinor)
    }

    @Test fun future_dated_activity_is_never_dropped_from_the_series() {
        val year = DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        val s = ReportCalculator.compute(listOf(row("EXPENSE", 123, 5, month = 9)), year, zone, LocalDate.of(2026, 5, 20))
        assertEquals(123L, s.series.sumOf { it.expenseMinor })
        assertEquals(s.expenseMinor, s.series.sumOf { it.expenseMinor })
    }

    @Test fun daily_average_uses_elapsed_days_for_in_progress_period() {
        // 10 days elapsed in March, 5,000 spent -> 500/day
        val s = ReportCalculator.compute(listOf(row("EXPENSE", 5_000, 2)), march, zone, LocalDate.of(2026, 3, 10))
        assertEquals(500L, s.dailyAverageExpenseMinor)
    }

    @Test fun largest_expense_is_reported() {
        val rows = listOf(row("EXPENSE", 100, 2), row("EXPENSE", 900, 3), row("EXPENSE", 400, 4))
        val s = calc(rows)
        assertEquals(900L, s.largestExpenseMinor)
        assertEquals(rows[1].id, s.largestExpenseId)
    }

    @Test fun timezone_decides_which_day_a_transaction_belongs_to() {
        // 2026-03-31T18:30Z is 2026-04-01 00:30 in Dhaka (UTC+6)
        val instant = LocalDateTime.of(2026, 3, 31, 18, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val r = LedgerRow("tz", "EXPENSE", 50, "f", dateMillis = instant)
        assertEquals(0L, calc(listOf(r)).expenseMinor)                       // March in Dhaka: not included
        val april = DateSpan(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))
        assertEquals(50L, ReportCalculator.compute(listOf(r), april, zone, LocalDate.of(2026, 4, 5)).expenseMinor)
    }

    // ---------- comparison ----------

    @Test fun change_percent() {
        assertEquals(50.0, Change(150, 100).percent!!, 1e-9)
        assertEquals(-25.0, Change(75, 100).percent!!, 1e-9)
        assertNull(Change(100, 0).percent)       // nothing to compare to: not a fake +infinity
        assertEquals(0.0, Change(0, 0).delta.toDouble(), 0.0)
    }
}
