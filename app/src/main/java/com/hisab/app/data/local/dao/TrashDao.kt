package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Permanent deletion of items that are ALREADY in Trash. Every statement is restricted to
 * isDeleted = 1, so live data can never be purged through here. Multi-statement purges run as one
 * Room transaction.
 */
@Dao
abstract class TrashDao {
    @Query("DELETE FROM attachments WHERE transactionId = :id")
    protected abstract suspend fun deleteAttachmentsOf(id: String)

    @Query("DELETE FROM transactions WHERE id = :id AND isDeleted = 1")
    protected abstract suspend fun deleteTrashedTransaction(id: String)

    @Query("DELETE FROM attachments WHERE transactionId IN (SELECT id FROM transactions WHERE userId = :userId AND isDeleted = 1)")
    protected abstract suspend fun deleteAttachmentsOfAllTrashed(userId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId AND isDeleted = 1")
    protected abstract suspend fun deleteAllTrashedTransactions(userId: String)

    @Query("DELETE FROM budgets WHERE userId = :userId AND isDeleted = 1")
    protected abstract suspend fun deleteAllTrashedBudgets(userId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE id = :id AND isDeleted = 1")
    protected abstract suspend fun isTransactionTrashed(id: String): Int

    @Query("DELETE FROM budgets WHERE id = :id AND isDeleted = 1")
    abstract suspend fun purgeBudget(id: String)

    @Transaction
    open suspend fun purgeTransaction(id: String) {
        // Attachments are only removed when the transaction really is in Trash.
        if (isTransactionTrashed(id) == 0) return
        deleteAttachmentsOf(id)
        deleteTrashedTransaction(id)
    }

    @Transaction
    open suspend fun emptyTrash(userId: String) {
        deleteAttachmentsOfAllTrashed(userId)
        deleteAllTrashedTransactions(userId)
        deleteAllTrashedBudgets(userId)
    }
}
