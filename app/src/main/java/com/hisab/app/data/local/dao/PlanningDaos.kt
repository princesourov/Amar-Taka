package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.ReminderEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert suspend fun insert(budget: BudgetEntity)
    @Update suspend fun update(budget: BudgetEntity)
    @Query("SELECT * FROM budgets WHERE userId = :userId AND isActive = 1")
    fun getActiveBudgets(userId: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY COALESCE(startDateEpochDay, 0) DESC")
    fun getAllBudgets(userId: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?
    @Query("UPDATE budgets SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean)
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
