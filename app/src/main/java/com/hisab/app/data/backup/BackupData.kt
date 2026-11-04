package com.hisab.app.data.backup

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

/** App settings that travel with a backup (the few that live outside the database). */
data class BackupPreferences(
    val currencySymbol: String = "৳",
    val themeMode: Int = -1,
    val notificationsEnabled: Boolean = true
)

/** Every user-owned table, exactly as stored. This is what a backup contains and what a restore writes. */
data class BackupData(
    val userProfiles: List<UserProfileEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<SavingsGoalEntity> = emptyList(),
    val contributions: List<GoalContributionEntity> = emptyList(),
    val recurring: List<RecurringTransactionEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
) {
    /** Row counts by table - shown in the restore preview and stored in the file header. */
    fun counts(): LinkedHashMap<String, Int> = linkedMapOf(
        "accounts" to accounts.size, "categories" to categories.size, "people" to people.size,
        "transactions" to transactions.size, "budgets" to budgets.size, "goals" to goals.size,
        "goal_contributions" to contributions.size, "recurring_transactions" to recurring.size,
        "reminders" to reminders.size, "attachments" to attachments.size, "user_profile" to userProfiles.size
    )

    /**
     * A backup made on another phone carries that phone's local user id. Everything is re-owned by
     * [userId] on restore so the rows show up for the current install.
     */
    fun withUserId(userId: String): BackupData = copy(
        userProfiles = userProfiles.map { it.copy(userId = userId) },
        accounts = accounts.map { it.copy(userId = userId) },
        categories = categories.map { it.copy(userId = userId) },
        people = people.map { it.copy(userId = userId) },
        transactions = transactions.map { it.copy(userId = userId) },
        budgets = budgets.map { it.copy(userId = userId) },
        goals = goals.map { it.copy(userId = userId) },
        recurring = recurring.map { it.copy(userId = userId) },
        reminders = reminders.map { it.copy(userId = userId) }
    )
}
