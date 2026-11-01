package com.hisab.app.domain.model

import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.domain.budget.BudgetEvaluation
import com.hisab.app.domain.budget.BudgetState

data class BudgetProgressItem(
    val budget: BudgetEntity,
    val categoryName: String?, // null = overall budget, not category-specific
    val spentMinor: Long,
    val periodLabel: String,
    val scope: BudgetScope,
    val evaluation: BudgetEvaluation
) {
    /** The user's own name for the budget, else its category, else null (overall, unnamed). */
    val displayName: String? get() = budget.name?.takeIf { it.isNotBlank() } ?: categoryName
    val remainingMinor: Long get() = evaluation.remainingMinor
    val isOverBudget: Boolean get() = evaluation.state == BudgetState.OVER
    val isActive: Boolean get() = budget.isActive
}

/** Compact figures for the Dashboard's Budgets card — the full per-budget list lives in BudgetsFragment. */
data class BudgetsSummary(val totalBudgets: Int, val overBudgetCount: Int)

/** Compact figures for the Dashboard's Savings Goals card. */
data class GoalsSummary(val totalGoals: Int, val totalSavedMinor: Long, val totalTargetMinor: Long)
