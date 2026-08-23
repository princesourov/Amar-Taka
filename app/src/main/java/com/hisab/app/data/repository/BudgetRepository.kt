package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.BudgetDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    fun getActiveBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getActiveBudgets(userId)

    /** Spent-so-far for one budget's current period (day/week/month), live from the ledger. */
    fun getSpent(userId: String, budget: BudgetEntity): Flow<Long> {
        val (start, end) = DateRanges.rangeForBudgetScope(BudgetScope.valueOf(budget.scope))
        return budget.categoryId?.let { categoryId ->
            transactionDao.getTotalExpenseForCategoryBetween(userId, categoryId, start, end)
        } ?: transactionDao.getTotalExpenseBetween(userId, start, end)
    }

    /**
     * Every active budget paired with its live spent amount. Shared by BudgetsViewModel (which
     * adds category names for the full list) and DashboardViewModel (which just needs counts) —
     * written once here so neither duplicates the multi-budget combine logic.
     */
    fun getBudgetsWithSpent(userId: String): Flow<List<Pair<BudgetEntity, Long>>> =
        getActiveBudgets(userId).flatMapLatest { budgets ->
            if (budgets.isEmpty()) flowOf(emptyList())
            else combine(budgets.map { b -> getSpent(userId, b).map { spent -> b to spent } }) { it.toList() }
        }

    suspend fun createBudget(userId: String, scope: BudgetScope, categoryId: String?, amountMinor: Long): String {
        val id = UUID.randomUUID().toString()
        budgetDao.insert(
            BudgetEntity(
                id = id, userId = userId, scope = scope.name, categoryId = categoryId,
                amountMinor = amountMinor, startDateEpochDay = null, isActive = true
            )
        )
        return id
    }
}
