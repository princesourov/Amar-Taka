package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hisab.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** One person's debt position as returned by the aggregate queries (see [TransactionDao.observeDebtRows]). */
data class PersonDebtRow(val personId: String, val receivableMinor: Long, val payableMinor: Long) {
    val receivableClamped: Long get() = receivableMinor.coerceAtLeast(0)
    val payableClamped: Long get() = payableMinor.coerceAtLeast(0)
}

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    /** Insert unless a row with this id exists (even a trashed one). Returns -1 when ignored. Used for recurring runs. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0
        ORDER BY transactionDateMillis DESC
    """)
    fun getAllForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 1
        ORDER BY updatedAtMillis DESC
    """)
    fun getDeletedForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
        ORDER BY transactionDateMillis DESC
    """)
    fun getForUserBetween(userId: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    /**
     * Net movement for one account: everything that arrived minus everything
     * that left. Add this to AccountEntity.openingBalanceMinor to get the
     * current balance — see the class doc on TransactionEntity for why this
     * single formula is correct for every transaction type.
     */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN destinationAccountId = :accountId THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN sourceAccountId = :accountId THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE (sourceAccountId = :accountId OR destinationAccountId = :accountId)
        AND isDeleted = 0
    """)
    fun getNetMovementForAccount(accountId: String): Flow<Long>

    /**
     * Net spending: EXPENSE minus REFUND (see LedgerRules in domain/report). Transfers, loans and
     * repayments are never included.
     */
    @Query("""
        SELECT COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND type IN ('EXPENSE', 'REFUND') AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalExpenseBetween(userId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE userId = :userId AND type = 'INCOME' AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalIncomeBetween(userId: String, startMillis: Long, endMillis: Long): Flow<Long>

    /** Backs category-specific budget progress; same net-of-refunds rule as getTotalExpenseBetween. */
    @Query("""
        SELECT COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND type IN ('EXPENSE', 'REFUND') AND categoryId = :categoryId AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalExpenseForCategoryBetween(
        userId: String, categoryId: String, startMillis: Long, endMillis: Long
    ): Flow<Long>

    /** Budget spending: overall when [categoryId] is null, otherwise that category only. */
    @Query("""
        SELECT COALESCE(SUM(CASE type WHEN 'EXPENSE' THEN amountMinor WHEN 'REFUND' THEN -amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND type IN ('EXPENSE', 'REFUND') AND isDeleted = 0
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getSpentBetween(userId: String, categoryId: String?, startMillis: Long, endMillis: Long): Flow<Long>

    /** Full lending/borrowing/repayment history with one person, newest first. */
    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND personId = :personId AND isDeleted = 0
        ORDER BY transactionDateMillis DESC
    """)
    fun getTransactionsForPerson(userId: String, personId: String): Flow<List<TransactionEntity>>

    /**
     * Debt position of every person, directions kept apart (see domain/debt/DebtLedger):
     * receivable = lent - repayments received, payable = borrowed - repayments made.
     * Values are NOT clamped here; callers clamp at zero (DebtLedger.totals / PersonDebtRow.*Clamped).
     */
    @Query("""
        SELECT personId,
            COALESCE(SUM(CASE type WHEN 'LENDING' THEN amountMinor WHEN 'REPAYMENT_RECEIVED' THEN -amountMinor ELSE 0 END), 0) AS receivableMinor,
            COALESCE(SUM(CASE type WHEN 'BORROWING' THEN amountMinor WHEN 'REPAYMENT_MADE' THEN -amountMinor ELSE 0 END), 0) AS payableMinor
        FROM transactions
        WHERE userId = :userId AND isDeleted = 0 AND personId IS NOT NULL
        AND type IN ('LENDING', 'REPAYMENT_RECEIVED', 'BORROWING', 'REPAYMENT_MADE')
        GROUP BY personId
    """)
    fun observeDebtRows(userId: String): Flow<List<PersonDebtRow>>

    /**
     * One person's position, optionally ignoring one transaction (used to validate an edit against
     * everything EXCEPT the row being edited). Null when the person has no debt rows at all.
     */
    @Query("""
        SELECT personId,
            COALESCE(SUM(CASE type WHEN 'LENDING' THEN amountMinor WHEN 'REPAYMENT_RECEIVED' THEN -amountMinor ELSE 0 END), 0) AS receivableMinor,
            COALESCE(SUM(CASE type WHEN 'BORROWING' THEN amountMinor WHEN 'REPAYMENT_MADE' THEN -amountMinor ELSE 0 END), 0) AS payableMinor
        FROM transactions
        WHERE userId = :userId AND personId = :personId AND isDeleted = 0
        AND type IN ('LENDING', 'REPAYMENT_RECEIVED', 'BORROWING', 'REPAYMENT_MADE')
        AND (:excludeTransactionId IS NULL OR id != :excludeTransactionId)
        GROUP BY personId
    """)
    suspend fun getDebtRow(userId: String, personId: String, excludeTransactionId: String?): PersonDebtRow?

    /** Net movement for one account right now (suspend twin of [getNetMovementForAccount]). */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN destinationAccountId = :accountId THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN sourceAccountId = :accountId THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE (sourceAccountId = :accountId OR destinationAccountId = :accountId)
        AND isDeleted = 0
    """)
    suspend fun getNetMovementForAccountOnce(accountId: String): Long

    /** Transactions generated by one recurring schedule (ids are "rec-<scheduleId>-<epochDay>"), newest first. */
    @Query("SELECT * FROM transactions WHERE id LIKE 'rec-' || :scheduleId || '-%' ORDER BY transactionDateMillis DESC")
    suspend fun getGeneratedByRecurring(scheduleId: String): List<TransactionEntity>

    /** Backs the "suspiciously identical transaction" check from spec Section 47. */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE userId = :userId AND type = :type AND amountMinor = :amountMinor
        AND sourceAccountId IS :sourceAccountId AND categoryId IS :categoryId
        AND transactionDateMillis >= :sinceMillis AND isDeleted = 0
    """)
    suspend fun countPossibleDuplicates(
        userId: String,
        type: String,
        amountMinor: Long,
        sourceAccountId: String?,
        categoryId: String?,
        sinceMillis: Long
    ): Int

    /** Trashed transactions that reference any of the given rows (used before purging an account/category/person). */
    @Query("SELECT COUNT(*) FROM transactions WHERE sourceAccountId = :accountId OR destinationAccountId = :accountId")
    suspend fun countReferencingAccount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countReferencingCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE personId = :personId")
    suspend fun countReferencingPerson(personId: String): Int

    @Query("UPDATE transactions SET isDeleted = 1, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun softDelete(id: String, nowMillis: Long)

    @Query("UPDATE transactions SET isDeleted = 0, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun restore(id: String, nowMillis: Long)

    /**
     * Same formula as getNetBalanceForPerson, but excluding one specific transaction row.
     * Used when editing a repayment: re-validates the new amount against what's owed by
     * everything EXCEPT the row being edited (otherwise the edit would be checked against
     * a total that already includes its own old value).
     */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'LENDING' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_RECEIVED' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'BORROWING' THEN amountMinor ELSE 0 END), 0) +
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_MADE' THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND personId = :personId AND isDeleted = 0 AND id != :excludeTransactionId
    """)
    suspend fun getNetBalanceForPersonExcluding(userId: String, personId: String, excludeTransactionId: String): Long

    /** Search across note/category/account/person — all joined by name in the repository layer. */
    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0
        AND (:type IS NULL OR type = :type)
        AND (:accountId IS NULL OR sourceAccountId = :accountId OR destinationAccountId = :accountId)
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:personId IS NULL OR personId = :personId)
        AND (:startMillis IS NULL OR transactionDateMillis >= :startMillis)
        AND (:endMillis IS NULL OR transactionDateMillis <= :endMillis)
        AND (:noteQuery IS NULL OR note LIKE '%' || :noteQuery || '%')
        ORDER BY transactionDateMillis DESC
    """)
    fun searchTransactions(
        userId: String,
        type: String?,
        accountId: String?,
        categoryId: String?,
        personId: String?,
        startMillis: Long?,
        endMillis: Long?,
        noteQuery: String?
    ): Flow<List<TransactionEntity>>
}
