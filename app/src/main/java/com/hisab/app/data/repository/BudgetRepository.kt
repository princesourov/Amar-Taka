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
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    fun getActiveBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getActiveBudgets(userId)
    fun getAllBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getAllBudgets(userId)

    fun getBudgetsForMonth(userId: String, month: YearMonth): Flow<List<BudgetEntity>> {
        val monthStart = month.atDay(1).toEpochDay()
        val monthEnd = month.atEndOfMonth().toEpochDay()
        return getAllBudgets(userId).map { budgets ->
            budgets.filter { b ->
                val budgetStart = b.startDateEpochDay ?: monthStart
                budgetStart in monthStart..monthEnd
            }
        }
    }

    fun getSpent(userId: String, budget: BudgetEntity): Flow<Long> {
        val (start, end) = rangeForBudget(budget)
        return budget.categoryId?.let { categoryId ->
            transactionDao.getTotalExpenseForCategoryBetween(userId, categoryId, start, end)
        } ?: transactionDao.getTotalExpenseBetween(userId, start, end)
    }

    fun getBudgetsWithSpent(userId: String): Flow<List<Pair<BudgetEntity, Long>>> =
        getActiveBudgets(userId).flatMapLatest { budgets ->
            if (budgets.isEmpty()) flowOf(emptyList())
            else combine(budgets.map { b -> getSpent(userId, b).map { spent -> b to spent } }) { it.toList() }
        }

    fun getBudgetsWithSpentForMonth(userId: String, month: YearMonth): Flow<List<Pair<BudgetEntity, Long>>> =
        getBudgetsForMonth(userId, month).flatMapLatest { budgets ->
            if (budgets.isEmpty()) flowOf(emptyList())
            else combine(budgets.map { b -> getSpent(userId, b).map { spent -> b to spent } }) { it.toList() }
        }

    suspend fun createBudget(
        userId: String,
        scope: BudgetScope,
        categoryId: String?,
        amountMinor: Long,
        startDateEpochDay: Long? = null
    ): String {
        val id = UUID.randomUUID().toString()
        budgetDao.insert(
            BudgetEntity(
                id = id,
                userId = userId,
                scope = scope.name,
                categoryId = categoryId,
                amountMinor = amountMinor,
                startDateEpochDay = startDateEpochDay,
                isActive = true
            )
        )
        return id
    }

    suspend fun updateBudget(
        budgetId: String,
        scope: BudgetScope,
        categoryId: String?,
        amountMinor: Long,
        startDateEpochDay: Long?,
        isActive: Boolean
    ) {
        val current = budgetDao.getById(budgetId) ?: return
        budgetDao.update(
            current.copy(
                scope = scope.name,
                categoryId = categoryId,
                amountMinor = amountMinor,
                startDateEpochDay = startDateEpochDay,
                isActive = isActive
            )
        )
    }

    suspend fun setBudgetActive(budgetId: String, isActive: Boolean) {
        budgetDao.setActive(budgetId, isActive)
    }

    suspend fun deleteBudget(budgetId: String) {
        budgetDao.setActive(budgetId, false)
    }

    suspend fun getBudgetById(budgetId: String): BudgetEntity? = budgetDao.getById(budgetId)

    private fun rangeForBudget(budget: BudgetEntity): Pair<Long, Long> {
        val startDay = budget.startDateEpochDay
        if (startDay == null) return DateRanges.rangeForBudgetScope(BudgetScope.valueOf(budget.scope))
        val startDate = LocalDate.ofEpochDay(startDay)
        return when (BudgetScope.valueOf(budget.scope)) {
            BudgetScope.DAILY -> DateRanges.forDate(startDate).let { it.startMillis to it.endMillis }
            BudgetScope.WEEKLY -> {
                val endDate = startDate.plusDays(6)
                DateRanges.forDate(startDate).startMillis to DateRanges.forDate(endDate).endMillis
            }
            BudgetScope.MONTHLY -> DateRanges.forMonth(YearMonth.from(startDate)).let { it.startMillis to it.endMillis }
        }
    }
}
