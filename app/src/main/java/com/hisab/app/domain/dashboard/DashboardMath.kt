package com.hisab.app.domain.dashboard

/** Small pure helpers for the Dashboard numbers, defined once so Dashboard and Reports agree. */
object DashboardMath {

    /** Income minus expense for the period. Transfers, loans and repayments are never part of either figure. */
    fun netCashFlow(incomeMinor: Long, expenseMinor: Long): Long = incomeMinor - expenseMinor

    /**
     * Savings rate = net cash flow / income, as a whole percent (same definition as ReportCalculator).
     * null when there is no income: the rate is not calculable and is never invented.
     */
    fun savingsRatePercent(incomeMinor: Long, expenseMinor: Long): Int? =
        if (incomeMinor <= 0) null
        else Math.round((incomeMinor - expenseMinor) * 100.0 / incomeMinor).toInt()

    /** Money in accounts that no goal has claimed. May be negative if goals out-allocate what is left in the accounts. */
    fun availableMinor(totalBalanceMinor: Long, allocatedToGoalsMinor: Long): Long = totalBalanceMinor - allocatedToGoalsMinor
}
