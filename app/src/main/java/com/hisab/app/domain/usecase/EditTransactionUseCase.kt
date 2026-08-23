package com.hisab.app.domain.usecase

import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.TransactionType

/**
 * Edits any transaction's amount/accounts/category/note/date in place — the row's `id` never
 * changes, so every derived value (account balances, person balances, budget progress, today's
 * totals) picks up the new figures automatically on their next Flow emission, with no separate
 * recompute step.
 *
 * For REPAYMENT_RECEIVED / REPAYMENT_MADE specifically, the "can't exceed what's owed" guard is
 * re-checked using [TransactionRepository.getNetBalanceForPersonExcluding] — the outstanding
 * balance as if this transaction's OLD amount didn't exist yet, so editing a repayment is
 * checked against the correct baseline rather than a total that already includes itself.
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
        force: Boolean = false
    ): TransactionRepository.RecordResult {
        val type = TransactionType.valueOf(original.type)
        val isRepayment = type == TransactionType.REPAYMENT_RECEIVED || type == TransactionType.REPAYMENT_MADE

        if (type == TransactionType.TRANSFER && newSourceAccountId != null && newSourceAccountId == newDestinationAccountId) {
            return TransactionRepository.RecordResult.InvalidInput("Source and destination accounts must differ")
        }

        if (!force && isRepayment && original.personId != null) {
            val baseline = repo.getNetBalanceForPersonExcluding(userId, original.personId, original.id)
            val outstanding = if (type == TransactionType.REPAYMENT_RECEIVED) baseline else -baseline
            if (outstanding <= 0 || newAmountMinor > outstanding) {
                return TransactionRepository.RecordResult.ExceedsOutstanding(outstanding.coerceAtLeast(0))
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
