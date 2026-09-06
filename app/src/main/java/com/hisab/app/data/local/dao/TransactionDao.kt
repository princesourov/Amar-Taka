package com.hisab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hisab.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)

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

    @Query("""
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE userId = :userId AND type = 'EXPENSE' AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalExpenseBetween(userId: String, startMillis: Long, endMillis: Long): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE userId = :userId AND type = 'INCOME' AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalIncomeBetween(userId: String, startMillis: Long, endMillis: Long): Flow<Long>

    /** Backs category-specific budget progress (spec Section 40's "Food budget: ৳3,000/month"). */
    @Query("""
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE userId = :userId AND type = 'EXPENSE' AND categoryId = :categoryId AND isDeleted = 0
        AND transactionDateMillis BETWEEN :startMillis AND :endMillis
    """)
    fun getTotalExpenseForCategoryBetween(
        userId: String, categoryId: String, startMillis: Long, endMillis: Long
    ): Flow<Long>

    /** Full lending/borrowing/repayment history with one person, newest first. */
    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND personId = :personId AND isDeleted = 0
        ORDER BY transactionDateMillis DESC
    """)
    fun getTransactionsForPerson(userId: String, personId: String): Flow<List<TransactionEntity>>

    /**
     * Net balance with one person: positive = they owe the user (You Will
     * Receive), negative = the user owes them (You Will Pay). Lending and a
     * received repayment move opposite directions; borrowing and a made
     * repayment move opposite directions too — this is spec Section 22/23's
     * status logic as one signed number.
     */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'LENDING' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_RECEIVED' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'BORROWING' THEN amountMinor ELSE 0 END), 0) +
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_MADE' THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND personId = :personId AND isDeleted = 0
    """)
    fun getNetBalanceForPerson(userId: String, personId: String): Flow<Long>

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'LENDING' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_RECEIVED' THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND isDeleted = 0
    """)
    fun getTotalReceivable(userId: String): Flow<Long>

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'BORROWING' THEN amountMinor ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN type = 'REPAYMENT_MADE' THEN amountMinor ELSE 0 END), 0)
        FROM transactions
        WHERE userId = :userId AND isDeleted = 0
    """)
    fun getTotalPayable(userId: String): Flow<Long>

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
