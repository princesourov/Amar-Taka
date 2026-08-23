package com.hisab.app.domain.usecase

import com.hisab.app.data.repository.TransactionRepository

/** Soft-deletes one transaction. Every balance/total query already excludes isDeleted rows. */
class DeleteTransactionUseCase(private val repo: TransactionRepository) {
    suspend operator fun invoke(transactionId: String) {
        repo.softDeleteTransaction(transactionId)
    }
}
