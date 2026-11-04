package com.hisab.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// These four entities are defined now (Phase 1) so the schema matches the full
// spec from day one, but their screens aren't built yet — see the README for
// what's wired up vs. what's scaffolding only.

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val scope: String, // BudgetScope enum name
    val categoryId: String? = null, // null = overall budget, not category-specific
    val amountMinor: Long,
    val startDateEpochDay: Long? = null,
    val isActive: Boolean = true,
    // Added in database v2 (see Migrations.kt). Defaults here match the migration's column defaults.
    val name: String? = null,
    val isDeleted: Boolean = false, // soft delete -> Trash; a deleted budget never touches transactions
    val updatedAtMillis: Long = 0L
)

/**
 * A savings goal is an ALLOCATION model, not a money movement: nothing is transferred out of the
 * account and no transaction row is created. What is "saved" is the sum of the goal's rows in
 * [GoalContributionEntity] (positive = allocated, negative = released). [savedAmountMinor] is the
 * pre-v3 stored total, kept only so old installs can be migrated; it is no longer read or written
 * (the v2->v3 migration copies it into a contribution row).
 *
 * Soft delete = isActive = false (+ deletedAtMillis) -> Trash. A trashed goal stops reserving money.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val targetAmountMinor: Long,
    val savedAmountMinor: Long = 0, // legacy, see class doc
    val targetDateEpochDay: Long? = null,
    val isActive: Boolean = true,
    val createdAtMillis: Long,
    // Added in database v3:
    val accountId: String? = null, // default account this goal is funded from (optional)
    val iconKey: String = "savings",
    val note: String? = null,
    val updatedAtMillis: Long = 0L,
    val deletedAtMillis: Long? = null
)

/**
 * One allocation (+) or release (-) of money toward a goal, from a specific account. [accountId] is
 * null only for the balance carried over from versions before v3 (the account was never recorded).
 * Rows are append-only: a mistake is corrected with an opposite row, so every change stays traceable.
 */
@Entity(
    tableName = "goal_contributions",
    indices = [Index("goalId"), Index("accountId")]
)
data class GoalContributionEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val accountId: String? = null,
    val amountMinor: Long, // signed
    val note: String? = null,
    val dateMillis: Long,
    val createdAtMillis: Long
)

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // TransactionType enum name
    val amountMinor: Long,
    val categoryId: String? = null,
    val accountId: String,
    val personId: String? = null,
    val frequency: String, // RecurrenceFrequency enum name
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val nextRunEpochDay: Long,
    val note: String? = null,
    val isActive: Boolean = true
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val relatedTransactionId: String? = null,
    val relatedPersonId: String? = null,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val daysBeforeDue: Int? = null,
    val isEnabled: Boolean = true,
    val isSent: Boolean = false
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val uriString: String,
    val type: String, // AttachmentType enum name
    val createdAtMillis: Long
)
