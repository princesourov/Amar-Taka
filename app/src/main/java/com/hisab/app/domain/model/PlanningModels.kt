package com.hisab.app.domain.model

import com.hisab.app.data.local.entity.BudgetEntity

data class BudgetProgressItem(
    val budget: BudgetEntity,
    val categoryName: String?, // null = overall budget, not category-specific
    val spentMinor: Long,
    val periodLabel: String
) {
    val remainingMinor: Long get() = budget.amountMinor - spentMinor
    val isOverBudget: Boolean get() = spentMinor > budget.amountMinor
    val isActive: Boolean get() = budget.isActive
    val percentUsed: Float
        get() = if (budget.amountMinor <= 0) 0f else spentMinor.toFloat() / budget.amountMinor.toFloat()
}

/** Compact figures for the Dashboard's Budgets card — the full per-budget list lives in BudgetsFragment. */
data class BudgetsSummary(val totalBudgets: Int, val overBudgetCount: Int)

/** Compact figures for the Dashboard's Savings Goals card. */
data class GoalsSummary(val totalGoals: Int, val totalSavedMinor: Long, val totalTargetMinor: Long)
