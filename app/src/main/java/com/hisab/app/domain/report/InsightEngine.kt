package com.hisab.app.domain.report

import kotlin.math.abs

/**
 * Plain-data insights derived ONLY from numbers the calculator already produced. The UI turns each
 * one into a localized sentence; nothing here is estimated, extrapolated or invented, and an insight
 * is simply not produced when the data it needs doesn't exist (for example no income -> no savings
 * rate insight).
 */
sealed interface Insight {
    data class TopCategory(val categoryId: String?, val amountMinor: Long, val sharePercent: Double) : Insight
    data class SpendingChange(val percent: Double) : Insight // negative = spent less than the previous period
    data class IncomeChange(val percent: Double) : Insight
    data class CategoryIncrease(val categoryId: String?, val percent: Double, val deltaMinor: Long) : Insight
    data class NegativeCashFlow(val shortfallMinor: Long) : Insight
    data class SavingsRate(val percent: Double) : Insight
    data class SpentWithoutIncome(val expenseMinor: Long) : Insight
    data class BudgetsOver(val count: Int) : Insight
    data class BudgetsNearLimit(val count: Int) : Insight
}

object InsightEngine {

    /** Minimum change (in percent) worth mentioning — avoids "spending changed by 0.4%" noise. */
    private const val CHANGE_THRESHOLD = 5.0

    fun generate(
        current: ReportSummary,
        previous: ReportSummary?,
        budgetsOver: Int = 0,
        budgetsNear: Int = 0,
        maxInsights: Int = 4
    ): List<Insight> {
        val out = ArrayList<Insight>()
        if (!current.hasActivity) return out

        if (budgetsOver > 0) out += Insight.BudgetsOver(budgetsOver)
        if (current.netCashFlowMinor < 0 && current.incomeMinor > 0) {
            out += Insight.NegativeCashFlow(-current.netCashFlowMinor)
        } else if (current.incomeMinor == 0L && current.expenseMinor > 0) {
            out += Insight.SpentWithoutIncome(current.expenseMinor)
        }

        current.expenseByCategory.firstOrNull()?.let {
            if (current.expenseByCategory.size > 1 || it.sharePercent < 100.0) {
                out += Insight.TopCategory(it.categoryId, it.amountMinor, it.sharePercent)
            }
        }

        if (previous != null) {
            Change(current.expenseMinor, previous.expenseMinor).percent?.let {
                if (abs(it) >= CHANGE_THRESHOLD) out += Insight.SpendingChange(it)
            }
            val prevByCategory = previous.expenseByCategory.associate { it.categoryId to it.amountMinor }
            current.expenseByCategory
                .mapNotNull { cat ->
                    val prev = prevByCategory[cat.categoryId] ?: return@mapNotNull null
                    val change = Change(cat.amountMinor, prev)
                    val pct = change.percent ?: return@mapNotNull null
                    if (pct >= 20.0 && change.delta > 0) Insight.CategoryIncrease(cat.categoryId, pct, change.delta) else null
                }
                .maxByOrNull { it.deltaMinor }
                ?.let { out += it }
            Change(current.incomeMinor, previous.incomeMinor).percent?.let {
                if (abs(it) >= CHANGE_THRESHOLD) out += Insight.IncomeChange(it)
            }
        }

        current.savingsRate?.let { if (it > 0) out += Insight.SavingsRate(it) }
        if (budgetsNear > 0) out += Insight.BudgetsNearLimit(budgetsNear)
        return out.take(maxInsights)
    }
}
