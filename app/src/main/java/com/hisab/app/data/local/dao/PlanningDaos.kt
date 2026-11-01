package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.ReminderEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.domain.budget.BudgetKey
import com.hisab.app.domain.budget.BudgetRules
import com.hisab.app.domain.model.BudgetScope
import kotlinx.coroutines.flow.Flow

/**
 * Budgets are soft-deleted (isDeleted) so Trash can restore them, and "paused" (isActive = false) is
 * a different, independent state. Neither ever touches the transactions table.
 *
 * An abstract class (not an interface) so the conflict check + write can run as ONE Room
 * transaction — two quick taps on Save can't both pass the check and create duplicates.
 */
@Dao
abstract class BudgetDao {
    @Insert abstract suspend fun insert(budget: BudgetEntity)
    @Update abstract suspend fun update(budget: BudgetEntity)

    /** Live, non-deleted, enabled budgets. */
    @Query("SELECT * FROM budgets WHERE userId = :userId AND isDeleted = 0 AND isActive = 1")
    abstract fun getActiveBudgets(userId: String): Flow<List<BudgetEntity>>

    /** Live, non-deleted budgets, enabled or paused. */
    @Query("SELECT * FROM budgets WHERE userId = :userId AND isDeleted = 0 ORDER BY COALESCE(startDateEpochDay, 0) DESC, id ASC")
    abstract fun getAllBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND isDeleted = 0")
    abstract suspend fun getAllBudgetsOnce(userId: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND isDeleted = 1 ORDER BY updatedAtMillis DESC")
    abstract fun getDeletedBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    abstract suspend fun getById(id: String): BudgetEntity?

    @Query("UPDATE budgets SET isActive = :isActive, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract suspend fun setActive(id: String, isActive: Boolean, nowMillis: Long)

    @Query("UPDATE budgets SET isDeleted = 1, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract suspend fun softDelete(id: String, nowMillis: Long)

    @Query("UPDATE budgets SET isDeleted = 0, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract suspend fun restore(id: String, nowMillis: Long)

    /** Only ever removes a budget that is already in Trash. */
    @Query("DELETE FROM budgets WHERE id = :id AND isDeleted = 1")
    abstract suspend fun deletePermanently(id: String)

    private fun List<BudgetEntity>.toKeys() =
        map { BudgetKey(it.id, BudgetScope.valueOf(it.scope), it.categoryId, it.isDeleted) }

    /** @return false (and writes nothing) if another live budget already covers the same period + category. */
    @Transaction
    open suspend fun insertUnlessConflict(budget: BudgetEntity): Boolean {
        val conflict = BudgetRules.findConflict(
            getAllBudgetsOnce(budget.userId).toKeys(), BudgetScope.valueOf(budget.scope), budget.categoryId, budget.id
        )
        if (conflict != null) return false
        insert(budget)
        return true
    }

    @Transaction
    open suspend fun updateUnlessConflict(budget: BudgetEntity): Boolean {
        val conflict = BudgetRules.findConflict(
            getAllBudgetsOnce(budget.userId).toKeys(), BudgetScope.valueOf(budget.scope), budget.categoryId, budget.id
        )
        if (conflict != null) return false
        update(budget)
        return true
    }

    /** Restoring from Trash must not create a second budget for a slot that has been re-used since. */
    @Transaction
    open suspend fun restoreUnlessConflict(id: String, nowMillis: Long): Boolean {
        val budget = getById(id) ?: return false
        val conflict = BudgetRules.findConflict(
            getAllBudgetsOnce(budget.userId).toKeys(), BudgetScope.valueOf(budget.scope), budget.categoryId, budget.id
        )
        if (conflict != null) return false
        restore(id, nowMillis)
        return true
    }
}

@Dao
interface SavingsGoalDao {
    @Insert suspend fun insert(goal: SavingsGoalEntity)
    @Update suspend fun update(goal: SavingsGoalEntity)
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isActive = 1 ORDER BY createdAtMillis DESC")
    fun getActiveGoals(userId: String): Flow<List<SavingsGoalEntity>>

    // Atomic increment — avoids a read-modify-write race between two contributions.
    @Query("UPDATE savings_goals SET savedAmountMinor = savedAmountMinor + :amountMinor WHERE id = :id")
    suspend fun addToSaved(id: String, amountMinor: Long)
}

@Dao
interface RecurringTransactionDao {
    @Insert suspend fun insert(recurring: RecurringTransactionEntity)
    @Update suspend fun update(recurring: RecurringTransactionEntity)
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1")
    fun getActiveRecurring(userId: String): Flow<List<RecurringTransactionEntity>>
    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextRunEpochDay <= :todayEpochDay")
    suspend fun getDueForExecution(todayEpochDay: Long): List<RecurringTransactionEntity>
}

@Dao
interface ReminderDao {
    @Insert suspend fun insert(reminder: ReminderEntity)
    @Update suspend fun update(reminder: ReminderEntity)
    @Query("SELECT * FROM reminders WHERE userId = :userId AND isEnabled = 1 ORDER BY triggerAtMillis ASC")
    fun getActiveReminders(userId: String): Flow<List<ReminderEntity>>
}
