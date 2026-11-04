package com.hisab.app.domain.usecase

import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.debt.DebtDirection
import com.hisab.app.domain.model.TransactionType

/**
 * Edits any transaction's amount/accounts/category/note/date in place — the row's `id` never
 * changes, so every derived value (account balances, person balances, budget progress, today's
 * totals) picks up the new figures automatically on their next Flow emission, with no separate
 * recompute step.
 *
 * For REPAYMENT_RECEIVED / REPAYMENT_MADE specifically, the "can't exceed what's owed" guard is
 * re-checked with [TransactionRepository.getRawPosition] excluding the edited row, so an edit is
 * checked against the correct baseline rather than a total that already includes itself. A loan can
 * likewise not be edited below what has already been repaid against it.
 */
class EditTransactionUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(
        userId: String,
        original: TransactionEntity,
        newAmountMinor: Long,
        newSourceAccountId: String?,
        newDestinationAccountId: String?,
        newCategoryId: String?,
        newNote: String?,
        newDateMillis: Long,
        @Suppress("UNUSED_PARAMETER") force: Boolean = false
    ): TransactionRepository.RecordResult {
        val type = TransactionType.valueOf(original.type)
        if (newAmountMinor <= 0) return TransactionRepository.RecordResult.InvalidInput("Amount must be greater than zero")

        if (type == TransactionType.TRANSFER && newSourceAccountId != null && newSourceAccountId == newDestinationAccountId) {
            return TransactionRepository.RecordResult.InvalidInput("Source and destination accounts must differ")
        }

        // Debt rows are validated against everything EXCEPT the row being edited, per direction.
        val personId = original.personId
        if (personId != null) {
            val repaymentDirection = when (type) {
                TransactionType.REPAYMENT_RECEIVED -> DebtDirection.RECEIVABLE
                TransactionType.REPAYMENT_MADE -> DebtDirection.PAYABLE
                else -> null
            }
            val loanDirection = when (type) {
                TransactionType.LENDING -> DebtDirection.RECEIVABLE
                TransactionType.BORROWING -> DebtDirection.PAYABLE
                else -> null
            }
            if (repaymentDirection != null) {
                // position without this repayment = what was still owed before it; the new amount must fit in it
                val owed = repo.getRawPosition(userId, personId, repaymentDirection, original.id).coerceAtLeast(0)
                if (owed <= 0 || newAmountMinor > owed) {
                    return TransactionRepository.RecordResult.ExceedsOutstanding(owed)
                }
            }
            if (loanDirection != null) {
                // position without this loan is (other loans - repayments); the loan must still cover the repayments
                val others = repo.getRawPosition(userId, personId, loanDirection, original.id)
                if (others + newAmountMinor < 0) {
                    return TransactionRepository.RecordResult.BelowRepaid(-others)
                }
            }
        }

        return repo.updateTransaction(
            original.copy(
                amountMinor = newAmountMinor,
                sourceAccountId = newSourceAccountId,
                destinationAccountId = newDestinationAccountId,
                categoryId = newCategoryId,
                note = newNote,
                transactionDateMillis = newDateMillis
            )
        )
    }
}
