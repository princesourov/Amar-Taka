package com.hisab.app.domain.dashboard

import com.hisab.app.domain.report.FlowKind
import com.hisab.app.domain.report.LedgerRow
import com.hisab.app.domain.report.LedgerRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One calendar day of the "Last 30 Days" history. Income and expense are the same figures the
 * Dashboard shows for a one-day period; net cash flow and savings rate are NOT re-implemented here,
 * they come straight from [DashboardMath].
 */
data class DailySummary(
    val date: LocalDate,
    val incomeMinor: Long,
    /** Net spending (expenses minus refunds), exactly as the Dashboard defines "expense". */
    val expenseMinor: Long
) {
    val netCashFlowMinor: Long get() = DashboardMath.netCashFlow(incomeMinor, expenseMinor)

    /** null when the day has no income (not calculable) - the UI shows the same dash the Dashboard does. */
    val savingsRatePercent: Int? get() = DashboardMath.savingsRatePercent(incomeMinor, expenseMinor)

    val hasActivity: Boolean get() = incomeMinor != 0L || expenseMinor != 0L
}

/** Pure (Android-free) per-day totals, so date bucketing is covered by plain JUnit tests. */
object DailySummaryCalculator {

    /** Same window as the Dashboard's "Last 30 Days" range (see DateRanges.resolve). */
    const val DAY_COUNT = 30

    /**
     * Returns exactly [dayCount] summaries, newest first: index 0 is [today], then yesterday, and so on.
     * Days without transactions are still present with zero income and expense.
     *
     * Classification follows [LedgerRules] (the single definition the Dashboard SQL mirrors):
     * INCOME adds to income, EXPENSE adds to expense, REFUND subtracts from expense, and transfers,
     * loans, repayments and anything else are ignored. Each row lands on the day of its own
     * transaction date in [zone]; rows outside the window are ignored. Callers pass only
     * non-deleted rows (the DAO query already filters trashed ones).
     */
    fun compute(rows: List<LedgerRow>, today: LocalDate, zone: ZoneId, dayCount: Int = DAY_COUNT): List<DailySummary> {
        val income = LongArray(dayCount)
        val expense = LongArray(dayCount)
        for (row in rows) {
            val date = Instant.ofEpochMilli(row.dateMillis).atZone(zone).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, today)
            if (daysAgo < 0 || daysAgo >= dayCount) continue
            val i = daysAgo.toInt()
            when (LedgerRules.kindOf(row.type)) {
                FlowKind.INCOME -> income[i] += row.amountMinor
                FlowKind.EXPENSE -> expense[i] += row.amountMinor
                FlowKind.REFUND -> expense[i] -= row.amountMinor
                else -> Unit
            }
        }
        return List(dayCount) { i -> DailySummary(today.minusDays(i.toLong()), income[i], expense[i]) }
    }
}
