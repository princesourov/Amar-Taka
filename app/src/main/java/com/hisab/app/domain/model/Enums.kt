package com.hisab.app.domain.model

/**
 * Every financial movement in Hisab is one of these. This is the "Universal
 * Transaction System" from the spec (Section 28) — lending, borrowing and
 * repayments are NOT separate ledgers, they're transaction rows like
 * everything else, which is what lets balances stay derived (Section 48)
 * instead of drifting out of sync with a separately-stored number.
 */
enum class TransactionType {
    INCOME, EXPENSE, TRANSFER, LENDING, BORROWING,
    REPAYMENT_RECEIVED, REPAYMENT_MADE, REFUND, GIFT, OTHER
}

enum class AccountType { POCKET, BANK, BKASH, NAGAD, ROCKET, CUSTOM }

enum class SyncStatus { PENDING, SYNCED, FAILED }

enum class PersonStatus { YOU_WILL_RECEIVE, YOU_WILL_PAY, SETTLED }

// Period only — whether a budget is category-specific is decided independently
// by BudgetEntity.categoryId being non-null (a category budget is still either
// daily/weekly/monthly, e.g. "Food, ৳3,000/month" from spec Section 40).
enum class BudgetScope { DAILY, WEEKLY, MONTHLY }

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class AttachmentType { RECEIPT, SCREENSHOT, INVOICE, OTHER }
