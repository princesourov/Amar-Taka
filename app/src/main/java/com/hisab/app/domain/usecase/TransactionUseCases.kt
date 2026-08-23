package com.hisab.app.domain.usecase

import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.SyncStatus
import com.hisab.app.domain.model.TransactionType
import java.util.UUID

private fun newTransaction(
    userId: String,
    type: TransactionType,
    amountMinor: Long,
    categoryId: String? = null,
    sourceAccountId: String? = null,
    destinationAccountId: String? = null,
    personId: String? = null,
    note: String?,
    dateMillis: Long,
    dueDateEpochDay: Long? = null
): TransactionEntity {
    val now = System.currentTimeMillis()
    return TransactionEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        type = type.name,
        amountMinor = amountMinor,
        categoryId = categoryId,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        personId = personId,
        note = note,
        transactionDateMillis = dateMillis,
        dueDateEpochDay = dueDateEpochDay,
        isSampleData = false,
        syncStatus = SyncStatus.PENDING.name,
        createdAtMillis = now,
        updatedAtMillis = now,
        isDeleted = false
    )
}

/** Account - Amount; Expense + Amount. */
class RecordExpenseUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, categoryId: String, paidFromAccountId: String,
        note: String? = null, dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ) = repo.recordTransaction(
        newTransaction(
            userId, TransactionType.EXPENSE, amountMinor,
            categoryId = categoryId, sourceAccountId = paidFromAccountId, note = note, dateMillis = dateMillis
        ),
        skipDuplicateCheck = force
    )
}

/** Account + Amount; Income + Amount. */
class RecordIncomeUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, intoAccountId: String, categoryId: String? = null,
        note: String? = null, dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ) = repo.recordTransaction(
        newTransaction(
            userId, TransactionType.INCOME, amountMinor,
            categoryId = categoryId, destinationAccountId = intoAccountId, note = note, dateMillis = dateMillis
        ),
        skipDuplicateCheck = force
    )
}

/** Source - Amount; Destination + Amount. No expense — spec Section 61's core distinction. */
class TransferMoneyUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, fromAccountId: String, toAccountId: String,
        note: String? = null, dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ): TransactionRepository.RecordResult {
        if (fromAccountId == toAccountId) {
            return TransactionRepository.RecordResult.InvalidInput("Source and destination accounts must differ")
        }
        return repo.recordTransaction(
            newTransaction(
                userId, TransactionType.TRANSFER, amountMinor,
                sourceAccountId = fromAccountId, destinationAccountId = toAccountId, note = note, dateMillis = dateMillis
            ),
            skipDuplicateCheck = force
        )
    }
}

/** Source - Amount; Receivable + Amount. */
class LendMoneyUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, personId: String, fromAccountId: String,
        note: String? = null, dueDateEpochDay: Long? = null,
        dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ) = repo.recordTransaction(
        newTransaction(
            userId, TransactionType.LENDING, amountMinor,
            sourceAccountId = fromAccountId, personId = personId, note = note,
            dateMillis = dateMillis, dueDateEpochDay = dueDateEpochDay
        ),
        skipDuplicateCheck = force
    )
}

/** Destination + Amount; Liability + Amount. */
class BorrowMoneyUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, personId: String, intoAccountId: String,
        note: String? = null, dueDateEpochDay: Long? = null,
        dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ) = repo.recordTransaction(
        newTransaction(
            userId, TransactionType.BORROWING, amountMinor,
            destinationAccountId = intoAccountId, personId = personId, note = note,
            dateMillis = dateMillis, dueDateEpochDay = dueDateEpochDay
        ),
        skipDuplicateCheck = force
    )
}

/**
 * Destination + Amount; Receivable - Amount. Refuses (returns ExceedsOutstanding)
 * when the amount is more than what that person actually owes, unless [force] is
 * set — spec Section 24: "Never allow repayment above outstanding balance without
 * confirmation."
 */
class RecordRepaymentReceivedUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, personId: String, receivedIntoAccountId: String,
        note: String? = null, dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ): TransactionRepository.RecordResult {
        if (!force) {
            val outstanding = repo.getNetBalanceForPersonOnce(userId, personId)
            if (outstanding <= 0 || amountMinor > outstanding) {
                return TransactionRepository.RecordResult.ExceedsOutstanding(outstanding.coerceAtLeast(0))
            }
        }
        return repo.recordTransaction(
            newTransaction(
                userId, TransactionType.REPAYMENT_RECEIVED, amountMinor,
                destinationAccountId = receivedIntoAccountId, personId = personId, note = note, dateMillis = dateMillis
            ),
            skipDuplicateCheck = true // repaying the same round amount twice in a row is common and valid
        )
    }
}

/** Source - Amount; Liability - Amount. Same overpayment guard as above, mirrored. */
class RecordRepaymentMadeUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String, amountMinor: Long, personId: String, paidFromAccountId: String,
        note: String? = null, dateMillis: Long = System.currentTimeMillis(), force: Boolean = false
    ): TransactionRepository.RecordResult {
        if (!force) {
            val outstanding = -repo.getNetBalanceForPersonOnce(userId, personId) // positive when the user owes
            if (outstanding <= 0 || amountMinor > outstanding) {
                return TransactionRepository.RecordResult.ExceedsOutstanding(outstanding.coerceAtLeast(0))
            }
        }
        return repo.recordTransaction(
            newTransaction(
                userId, TransactionType.REPAYMENT_MADE, amountMinor,
                sourceAccountId = paidFromAccountId, personId = personId, note = note, dateMillis = dateMillis
            ),
            skipDuplicateCheck = true
        )
    }
}
