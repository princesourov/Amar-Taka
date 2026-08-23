package com.hisab.app.data.repository

import com.hisab.app.data.local.dao.TransactionDao
import com.hisab.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TransactionRepository(private val transactionDao: TransactionDao) {

    sealed interface RecordResult {
        data class Success(val transactionId: String) : RecordResult
        data class PossibleDuplicate(val amountMinor: Long) : RecordResult
        data class ExceedsOutstanding(val outstandingMinor: Long) : RecordResult
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

    fun getTransactionsBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getForUserBetween(userId, startMillis, endMillis)

    fun getTotalExpenseBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getTotalExpenseBetween(userId, startMillis, endMillis)

    fun getTotalIncomeBetween(userId: String, startMillis: Long, endMillis: Long) =
        transactionDao.getTotalIncomeBetween(userId, startMillis, endMillis)

    fun getNetBalanceForPerson(userId: String, personId: String): Flow<Long> =
        transactionDao.getNetBalanceForPerson(userId, personId)

    suspend fun getNetBalanceForPersonOnce(userId: String, personId: String): Long =
        transactionDao.getNetBalanceForPerson(userId, personId).first()

    suspend fun getNetBalanceForPersonExcluding(userId: String, personId: String, excludeTransactionId: String): Long =
        transactionDao.getNetBalanceForPersonExcluding(userId, personId, excludeTransactionId)

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
