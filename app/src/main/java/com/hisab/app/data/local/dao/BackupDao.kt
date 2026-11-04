package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.hisab.app.data.backup.BackupData
import com.hisab.app.data.repository.BackupStore
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.AttachmentEntity
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.local.entity.ReminderEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.local.entity.UserProfileEntity

/**
 * Whole-database read and replace for backup/restore. Both [readAll] and [replaceAll] are single Room
 * transactions: a snapshot is consistent, and a restore either finishes completely or rolls back
 * (an insert failing halfway undoes the deletes), so the database is never left partly restored.
 * Trashed rows are included on purpose - a backup is a faithful copy.
 */
@Dao
abstract class BackupDao : BackupStore {
    @Query("SELECT * FROM user_profile") protected abstract suspend fun profiles(): List<UserProfileEntity>
    @Query("SELECT * FROM accounts") protected abstract suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM categories") protected abstract suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM people") protected abstract suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM transactions") protected abstract suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets") protected abstract suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM savings_goals") protected abstract suspend fun goals(): List<SavingsGoalEntity>
    @Query("SELECT * FROM goal_contributions") protected abstract suspend fun contributions(): List<GoalContributionEntity>
    @Query("SELECT * FROM recurring_transactions") protected abstract suspend fun recurring(): List<RecurringTransactionEntity>
    @Query("SELECT * FROM reminders") protected abstract suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM attachments") protected abstract suspend fun attachments(): List<AttachmentEntity>

    @Query("DELETE FROM user_profile") protected abstract suspend fun clearProfiles()
    @Query("DELETE FROM accounts") protected abstract suspend fun clearAccounts()
    @Query("DELETE FROM categories") protected abstract suspend fun clearCategories()
    @Query("DELETE FROM people") protected abstract suspend fun clearPeople()
    @Query("DELETE FROM transactions") protected abstract suspend fun clearTransactions()
    @Query("DELETE FROM budgets") protected abstract suspend fun clearBudgets()
    @Query("DELETE FROM savings_goals") protected abstract suspend fun clearGoals()
    @Query("DELETE FROM goal_contributions") protected abstract suspend fun clearContributions()
    @Query("DELETE FROM recurring_transactions") protected abstract suspend fun clearRecurring()
    @Query("DELETE FROM reminders") protected abstract suspend fun clearReminders()
    @Query("DELETE FROM attachments") protected abstract suspend fun clearAttachments()

    @Insert protected abstract suspend fun insertProfiles(rows: List<UserProfileEntity>)
    @Insert protected abstract suspend fun insertAccounts(rows: List<AccountEntity>)
    @Insert protected abstract suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert protected abstract suspend fun insertPeople(rows: List<PersonEntity>)
    @Insert protected abstract suspend fun insertTransactions(rows: List<TransactionEntity>)
    @Insert protected abstract suspend fun insertBudgets(rows: List<BudgetEntity>)
    @Insert protected abstract suspend fun insertGoals(rows: List<SavingsGoalEntity>)
    @Insert protected abstract suspend fun insertContributions(rows: List<GoalContributionEntity>)
    @Insert protected abstract suspend fun insertRecurring(rows: List<RecurringTransactionEntity>)
    @Insert protected abstract suspend fun insertReminders(rows: List<ReminderEntity>)
    @Insert protected abstract suspend fun insertAttachments(rows: List<AttachmentEntity>)

    @Transaction
    override suspend fun readAll(): BackupData = BackupData(
        userProfiles = profiles(), accounts = accounts(), categories = categories(), people = people(),
        transactions = transactions(), budgets = budgets(), goals = goals(), contributions = contributions(),
        recurring = recurring(), reminders = reminders(), attachments = attachments()
    )

    /** Replaces EVERYTHING with [data]. Atomic: any failure rolls the whole thing back. Preferences are not stored here. */
    @Transaction
    override suspend fun replaceAll(data: BackupData) {
        clearAttachments(); clearContributions(); clearReminders(); clearRecurring(); clearBudgets()
        clearTransactions(); clearGoals(); clearPeople(); clearCategories(); clearAccounts(); clearProfiles()
        insertProfiles(data.userProfiles); insertAccounts(data.accounts); insertCategories(data.categories)
        insertPeople(data.people); insertGoals(data.goals); insertContributions(data.contributions)
        insertBudgets(data.budgets); insertRecurring(data.recurring); insertTransactions(data.transactions)
        insertReminders(data.reminders); insertAttachments(data.attachments)
    }
}
