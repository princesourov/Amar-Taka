package com.hisab.app.domain.model

import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.TransactionEntity

/** [receivableMinor]/[payableMinor] are the person's open debts per direction, already clamped at zero. */
data class PersonWithBalance(val person: PersonEntity, val receivableMinor: Long, val payableMinor: Long) {
    /** Positive = they owe the user overall; negative = the user owes them overall. */
    val netBalanceMinor: Long get() = receivableMinor - payableMinor

    val status: PersonStatus
        get() = when {
            netBalanceMinor > 0 -> PersonStatus.YOU_WILL_RECEIVE
            netBalanceMinor < 0 -> PersonStatus.YOU_WILL_PAY
            else -> PersonStatus.SETTLED
        }
}

data class AccountWithBalance(val account: AccountEntity, val balanceMinor: Long)

/**
 * A transaction row with its foreign-key-ish fields (categoryId, sourceAccountId,
 * destinationAccountId, personId) already resolved to display names. Built by combining
 * TransactionEntity with the categories/accounts/people lists already available from their
 * own repositories — no JOIN query, matching the same name-resolution pattern already used
 * for budget category names.
 */
data class TransactionDisplayItem(
    val transaction: TransactionEntity,
    val categoryName: String?,
    val sourceAccountName: String?,
    val destinationAccountName: String?,
    val personName: String?
) {
    val id: String get() = transaction.id
    val type: TransactionType get() = TransactionType.valueOf(transaction.type)
}
