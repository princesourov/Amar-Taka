package com.hisab.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The single ledger row every money movement becomes — expenses, income,
 * transfers, lending, borrowing, and both repayment directions all flow
 * through this one table (spec Section 28, "Universal Transaction System").
 *
 * Column semantics that make every transaction type share one balance formula:
 *  - sourceAccountId set        -> money LEAVES that account
 *  - destinationAccountId set   -> money ARRIVES at that account
 *  - either side can be null (e.g. an EXPENSE has no destination — the money
 *    leaves the tracked system; a BORROWING has no source — it arrives from
 *    a liability, not from one of the user's own accounts)
 *
 * That means account balance = openingBalance + SUM(destination hits) -
 * SUM(source hits), correct for every single transaction type without any
 * type-specific branching. See TransactionDao.getNetMovementForAccount.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("userId"), Index("sourceAccountId"), Index("destinationAccountId"),
        Index("personId"), Index("categoryId"), Index("transactionDateMillis")
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // TransactionType enum name
    val amountMinor: Long,
    val categoryId: String? = null,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val personId: String? = null,
    val note: String? = null,
    val transactionDateMillis: Long,
    val dueDateEpochDay: Long? = null,
    val isSampleData: Boolean = false,
    val syncStatus: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false // soft delete -> Trash (spec Section 46)
)
