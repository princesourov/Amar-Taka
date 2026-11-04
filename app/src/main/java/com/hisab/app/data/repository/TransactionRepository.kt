package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import com.hisab.app.domain.debt.DebtDirection

class TransactionRepository(private val transactionDao: TransactionDao) {

    sealed interface RecordResult {
        data class Success(val transactionId: String) : RecordResult
        data class PossibleDuplicate(val amountMinor: Long) : RecordResult
        data class ExceedsOutstanding(val outstandingMinor: Long) : RecordResult
        /** Editing a loan below what has already been repaid against it. */
        data class BelowRepaid(val minimumMinor: Long) : RecordResult
        data class InvalidInput(val message: String) : RecordResult
    }

    /**
     * Inserts a transaction. Unless [skipDuplicateCheck] is true, first checks
     * for a same-amount, same-type, same-account, same-category transaction
     * within the last minute and returns [RecordResult.PossibleDuplicate]
     * instead of silently saving — spec Section 47's "are you sure this is a
     * new transaction?" safeguard. Callers re-invoke with skipDuplicateCheck =
     * true once the user confirms.
     */
    suspend fun recordTransaction(
        transaction: TransactionEntity,
        skipDuplicateCheck: Boolean = false
    ): RecordResult {
        if (!skipDuplicateCheck) {
            val duplicates = transactionDao.countPossibleDuplicates(
                userId = transaction.userId,
                type = transaction.type,
                amountMinor = transaction.amountMinor,
                sourceAccountId = transaction.sourceAccountId,
                categoryId = transaction.categoryId,
                sinceMillis = System.currentTimeMillis() - DUPLICATE_WINDOW_MILLIS
            )
            if (duplicates > 0) return RecordResult.PossibleDuplicate(transaction.amountMinor)
        }
        transactionDao.insert(transaction)
        return RecordResult.Success(transaction.id)
    }

    fun getRecentTransactions(userId: String): Flow<List<TransactionEntity>> =
        transactionDao.getAllForUser(userId)

    fun getDeletedTransactions(userId: String): Flow<List<TransactionEntity>> =
        transactionDao.getDeletedForUser(userId)

    fun getTransactionsBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getForUserBetween(userId, startMillis, endMillis)

    fun getTotalExpenseBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getTotalExpenseBetween(userId, startMillis, endMillis)

    fun getTotalIncomeBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getTotalIncomeBetween(userId, startMillis, endMillis)

    /**
     * What a person still owes the user (RECEIVABLE) or the user still owes them (PAYABLE), ignoring
     * [excludeTransactionId] when editing. Never negative.
     */
    suspend fun getOutstanding(userId: String, personId: String, direction: DebtDirection, excludeTransactionId: String? = null): Long {
        val row = transactionDao.getDebtRow(userId, personId, excludeTransactionId)
        return when (direction) {
            DebtDirection.RECEIVABLE -> row?.receivableClamped ?: 0L
            DebtDirection.PAYABLE -> row?.payableClamped ?: 0L
        }
    }

    /** Unclamped principal-minus-repayments for a direction (can be negative if repayments exceed principal). */
    suspend fun getRawPosition(userId: String, personId: String, direction: DebtDirection, excludeTransactionId: String? = null): Long {
        val row = transactionDao.getDebtRow(userId, personId, excludeTransactionId)
        return when (direction) {
            DebtDirection.RECEIVABLE -> row?.receivableMinor ?: 0L
            DebtDirection.PAYABLE -> row?.payableMinor ?: 0L
        }
    }

    suspend fun getTransactionByIdOnce(id: String): TransactionEntity? = transactionDao.getById(id)

    /**
     * Edits are NOT subject to the duplicate-transaction guard above — that check exists to
     * catch accidental double-entry of a NEW transaction, not to second-guess a deliberate
     * correction to an existing one. Because balances are always derived live from this table
     * (never stored), a plain @Update is sufficient: every downstream Flow — account balance,
     * budget progress, person balance, today's totals — re-emits automatically the moment this
     * row changes. There is no separate "recalculate" step, by construction.
     */
    suspend fun updateTransaction(transaction: TransactionEntity): RecordResult {
        transactionDao.update(transaction.copy(updatedAtMillis = System.currentTimeMillis(), syncStatus = "PENDING"))
        return RecordResult.Success(transaction.id)
    }

    /** Soft delete — every balance/total query already filters `AND isDeleted = 0`. Nothing is destroyed. */
    suspend fun softDeleteTransaction(id: String) {
        transactionDao.softDelete(id, System.currentTimeMillis())
    }

    /** Restoring puts the row back into every balance, budget and report automatically (they are all derived live). */
    suspend fun restoreTransaction(id: String) {
        transactionDao.restore(id, System.currentTimeMillis())
    }

    fun searchTransactions(
        userId: String,
        type: String? = null,
        accountId: String? = null,
        categoryId: String? = null,
        personId: String? = null,
        startMillis: Long? = null,
        endMillis: Long? = null,
        noteQuery: String? = null
    ): Flow<List<TransactionEntity>> = transactionDao.searchTransactions(
        userId, type, accountId, categoryId, personId, startMillis, endMillis, noteQuery?.ifBlank { null }
    )

    companion object {
        private const val DUPLICATE_WINDOW_MILLIS = 60_000L
    }
}
