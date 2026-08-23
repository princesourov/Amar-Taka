package com.hisab.app.data.local.entity

import androidx.room.Entity
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
    val isActive: Boolean = true
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val targetAmountMinor: Long,
    val savedAmountMinor: Long = 0,
    val targetDateEpochDay: Long? = null,
    val isActive: Boolean = true,
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
