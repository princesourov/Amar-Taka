package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.BudgetDao
import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.domain.budget.BudgetPeriods
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.domain.model.BudgetSpent
import com.hisab.app.domain.report.DateSpan
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Budgets are recurring limits (see BudgetPeriods). Spending always comes from the transaction
 * ledger through TransactionDao.getSpentBetween — the same net-of-refunds rule Reports and the
 * Dashboard use — and every figure is a Flow, so adding, editing, deleting or restoring a
 * transaction re-evaluates every budget automatically.
 */
class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) {
    sealed interface SaveResult {
        data class Saved(val id: String) : SaveResult
        /** Another live budget already covers the same period + category. */
        data object Conflict : SaveResult
        data object NotFound : SaveResult
    }

    fun getActiveBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getActiveBudgets(userId)

    /** Live (not in Trash) budgets, enabled or paused. */
    fun getAllBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getAllBudgets(userId)

    fun getDeletedBudgets(userId: String): Flow<List<BudgetEntity>> = budgetDao.getDeletedBudgets(userId)

    suspend fun getBudgetById(budgetId: String): BudgetEntity? = budgetDao.getById(budgetId)

    fun getSpent(userId: String, budget: BudgetEntity, period: DateSpan): Flow<Long> {
        val zone = DateRanges.zone()
        return transactionDao.getSpentBetween(userId, budget.categoryId, period.startMillis(zone), period.endMillis(zone))
    }

    /**
     * Budgets in effect for [month], each with the period evaluated and its spending. MONTHLY
     * budgets use the viewed month; DAILY/WEEKLY use the day/week containing today (current month)
     * or the last day of the viewed month (past months).
     */
    fun observeBudgetsForMonth(
        userId: String,
        month: YearMonth,
        activeOnly: Boolean = false
    ): Flow<List<BudgetSpent>> {
        val reference = BudgetPeriods.referenceDateForMonth(month, LocalDate.now(DateRanges.zone()))
        val firstDay = DateRanges.firstDayOfWeek()
        val source = if (activeOnly) getActiveBudgets(userId) else getAllBudgets(userId)
        return source.flatMapLatest { budgets ->
            val inEffect = budgets.mapNotNull { b ->
                val period = BudgetPeriods.periodContaining(BudgetScope.valueOf(b.scope), reference, firstDay)
                if (BudgetPeriods.isInEffect(b.startDateEpochDay, period)) b to period else null
            }
            if (inEffect.isEmpty()) flowOf(emptyList())
            else combine(inEffect.map { (b, p) -> getSpent(userId, b, p).map { BudgetSpent(b, p, it) } }) { it.toList() }
        }
    }

    /** Pair view kept for the Dashboard: enabled budgets only, with their spent amount. */
    fun getBudgetsWithSpentForMonth(userId: String, month: YearMonth): Flow<List<Pair<BudgetEntity, Long>>> =
        observeBudgetsForMonth(userId, month, activeOnly = true).map { list -> list.map { it.budget to it.spentMinor } }

    suspend fun createBudget(
        userId: String,
        name: String?,
        scope: BudgetScope,
        categoryId: String?,
        amountMinor: Long,
        startDateEpochDay: Long?,
        isActive: Boolean = true
    ): SaveResult {
        val id = UUID.randomUUID().toString()
        val saved = budgetDao.insertUnlessConflict(
            BudgetEntity(
                id = id, userId = userId, scope = scope.name, categoryId = categoryId, amountMinor = amountMinor,
                startDateEpochDay = startDateEpochDay, isActive = isActive, name = name,
                isDeleted = false, updatedAtMillis = System.currentTimeMillis()
            )
        )
        return if (saved) SaveResult.Saved(id) else SaveResult.Conflict
    }

    suspend fun updateBudget(
        budgetId: String,
        name: String?,
        scope: BudgetScope,
        categoryId: String?,
        amountMinor: Long,
        startDateEpochDay: Long?,
        isActive: Boolean
    ): SaveResult {
        val current = budgetDao.getById(budgetId) ?: return SaveResult.NotFound
        val saved = budgetDao.updateUnlessConflict(
            current.copy(
                name = name, scope = scope.name, categoryId = categoryId, amountMinor = amountMinor,
                startDateEpochDay = startDateEpochDay, isActive = isActive,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return if (saved) SaveResult.Saved(budgetId) else SaveResult.Conflict
    }

    suspend fun setBudgetActive(budgetId: String, isActive: Boolean) {
        budgetDao.setActive(budgetId, isActive, System.currentTimeMillis())
    }

    /** Moves the budget to Trash. Transactions are never touched. */
    suspend fun deleteBudget(budgetId: String) {
        budgetDao.softDelete(budgetId, System.currentTimeMillis())
    }

    suspend fun restoreBudget(budgetId: String): SaveResult =
        if (budgetDao.restoreUnlessConflict(budgetId, System.currentTimeMillis())) SaveResult.Saved(budgetId)
        else if (budgetDao.getById(budgetId) == null) SaveResult.NotFound else SaveResult.Conflict
}
