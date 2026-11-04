package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.GoalContributionEntity
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

/** Sum of a goal's contribution rows. */
data class GoalTotal(val goalId: String, val totalMinor: Long)

/** Money earmarked for goals from one account; [accountId] null = carried over from before v3. */
data class AccountAllocation(val accountId: String?, val totalMinor: Long)

/**
 * Goals + their allocation ledger. A goal is "live" while isActive = 1; isActive = 0 is the Trash
 * state (the rows are kept so Restore brings everything back, and a trashed goal stops reserving money
 * because every allocation query below joins on live goals only).
 */
@Dao
abstract class SavingsGoalDao {
    @Insert abstract suspend fun insert(goal: SavingsGoalEntity)
    @Update abstract suspend fun update(goal: SavingsGoalEntity)
    @Insert abstract suspend fun insertContribution(contribution: GoalContributionEntity)

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isActive = 1 ORDER BY createdAtMillis DESC")
    abstract fun getActiveGoals(userId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isActive = 0 ORDER BY deletedAtMillis DESC")
    abstract fun getDeletedGoals(userId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isActive = 0")
    abstract suspend fun getDeletedGoalsOnce(userId: String): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    abstract suspend fun getById(id: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    abstract fun observeById(id: String): Flow<SavingsGoalEntity?>

    /** Saved total per live goal (goals with no contributions simply have no row). */
    @Query("""
        SELECT c.goalId AS goalId, COALESCE(SUM(c.amountMinor), 0) AS totalMinor
        FROM goal_contributions c JOIN savings_goals g ON g.id = c.goalId
        WHERE g.userId = :userId AND g.isActive = 1
        GROUP BY c.goalId
    """)
    abstract fun observeGoalTotals(userId: String): Flow<List<GoalTotal>>

    /** Allocated money per account across live goals. */
    @Query("""
        SELECT c.accountId AS accountId, COALESCE(SUM(c.amountMinor), 0) AS totalMinor
        FROM goal_contributions c JOIN savings_goals g ON g.id = c.goalId
        WHERE g.userId = :userId AND g.isActive = 1
        GROUP BY c.accountId
    """)
    abstract fun observeAllocationsByAccount(userId: String): Flow<List<AccountAllocation>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY dateMillis DESC, createdAtMillis DESC")
    abstract fun observeContributions(goalId: String): Flow<List<GoalContributionEntity>>

    /** Everything one goal has allocated from one account (accountId null = the carried-over bucket). */
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM goal_contributions WHERE goalId = :goalId AND accountId IS :accountId")
    abstract suspend fun getGoalAllocationOnce(goalId: String, accountId: String?): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM goal_contributions WHERE goalId = :goalId")
    abstract suspend fun getGoalSavedOnce(goalId: String): Long

    /** Allocated from one account across live goals. */
    @Query("""
        SELECT COALESCE(SUM(c.amountMinor), 0)
        FROM goal_contributions c JOIN savings_goals g ON g.id = c.goalId
        WHERE g.isActive = 1 AND c.accountId = :accountId
    """)
    abstract suspend fun getAccountAllocatedOnce(accountId: String): Long

    /** How many goal rows (goals + contributions) point at an account; used to block purging it. */
    @Query("SELECT (SELECT COUNT(*) FROM goal_contributions WHERE accountId = :accountId) + (SELECT COUNT(*) FROM savings_goals WHERE accountId = :accountId)")
    abstract suspend fun countReferencingAccount(accountId: String): Int

    @Query("UPDATE savings_goals SET isActive = 0, deletedAtMillis = :nowMillis, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract suspend fun softDelete(id: String, nowMillis: Long)

    @Query("UPDATE savings_goals SET isActive = 1, deletedAtMillis = NULL, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract suspend fun restore(id: String, nowMillis: Long)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId AND goalId IN (SELECT id FROM savings_goals WHERE id = :goalId AND isActive = 0)")
    protected abstract suspend fun deleteContributionsOfTrashedGoal(goalId: String)

    @Query("DELETE FROM savings_goals WHERE id = :id AND isActive = 0")
    protected abstract suspend fun deleteTrashedGoal(id: String)

    /** Permanently removes a goal that is already in Trash, together with its contribution rows. */
    @Transaction
    open suspend fun purge(goalId: String) {
        deleteContributionsOfTrashedGoal(goalId)
        deleteTrashedGoal(goalId)
    }
}

@Dao
interface RecurringTransactionDao {
    @Insert suspend fun insert(recurring: RecurringTransactionEntity)
    @Update suspend fun update(recurring: RecurringTransactionEntity)
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1")
    fun getActiveRecurring(userId: String): Flow<List<RecurringTransactionEntity>>
    /** Active AND paused schedules, so a paused one stays visible and can be resumed. */
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId ORDER BY isActive DESC, nextRunEpochDay ASC")
    fun getAllRecurring(userId: String): Flow<List<RecurringTransactionEntity>>
    @Query("UPDATE recurring_transactions SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)
    /** Removes the schedule only; transactions it already generated are untouched. */
    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: String): RecurringTransactionEntity?
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :accountId AND isActive = 1")
    suspend fun countActiveForAccount(accountId: String): Int
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int
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
